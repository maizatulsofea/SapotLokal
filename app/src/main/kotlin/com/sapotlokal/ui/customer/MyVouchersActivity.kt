package com.sapotlokal.ui.customer

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ValueEventListener
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.sapotlokal.R
import com.sapotlokal.data.model.Vendor
import com.sapotlokal.data.model.Voucher
import com.sapotlokal.data.model.VoucherStatus
import com.sapotlokal.data.model.VoucherUser
import com.sapotlokal.data.repository.PointRepository
import com.sapotlokal.data.repository.VendorRepository
import com.sapotlokal.data.repository.VoucherRepository
import com.sapotlokal.databinding.ActivityMyVouchersBinding
import com.sapotlokal.databinding.DialogVoucherQrBinding
import com.sapotlokal.util.ServerClock
import com.sapotlokal.util.VoucherClaimHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rewards tab. One job: "what can I claim or use?"
 * Three sections with one vocabulary:
 *  - Ready: rewards the customer has enough points for at that shop → Claim (spends points now).
 *  - My vouchers: claimed vouchers → Show QR at the counter; used and expired ones listed after.
 *  - Working towards: rewards at shops the customer has visited, closest first.
 * Shops where the customer has no points are left out; the Shops tab is where to discover them.
 */
class MyVouchersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyVouchersBinding
    private val auth = FirebaseAuth.getInstance()
    private var uid = ""

    private var vouchersListener: ValueEventListener? = null
    private var rulesListener: ValueEventListener? = null
    private var pointsListener: ValueEventListener? = null

    private var vouchers: List<VoucherUser> = emptyList()
    private var vendorMap: Map<String, Vendor> = emptyMap()
    private var perShop: Map<String, Int> = emptyMap()
    private var rules: List<Voucher> = emptyList()
    private var loaded = 0
    private var section: Section? = null

    private enum class Section { READY, VOUCHERS, TOWARDS }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyVouchersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        CustomerNav.bind(this, binding.customerNav, CustomerNav.Tab.REWARDS)

        uid = auth.currentUser?.uid ?: run {
            Toast.makeText(this, getString(R.string.k_please_log_in), Toast.LENGTH_SHORT).show()
            finish(); return
        }
        binding.btnEmptyAction.setOnClickListener { startActivity(Intent(this, FindShopsActivity::class.java)) }

        VendorRepository.fetchAllVendors(
            onResult = { v -> vendorMap = v.associateBy { it.vendorID }; render() },
            onError = { render() }
        )
        pointsListener = PointRepository.listenToPointsForUser(
            userID = uid, onUpdate = { p, _, _ -> perShop = p; loaded = loaded or 1; render() }, onError = { loaded = loaded or 1; render() }
        )
        rulesListener = VoucherRepository.listenToAllActiveRules(
            onUpdate = { r -> rules = r; loaded = loaded or 2; render() }, onError = { loaded = loaded or 2; render() }
        )
        vouchersListener = VoucherRepository.listenToVouchersForUser(
            userID = uid,
            onUpdate = { v -> vouchers = v; loaded = loaded or 4; render() },
            onError = { msg ->
                loaded = loaded or 4
                Toast.makeText(this, getString(R.string.k_could_not_load_vouchers, msg), Toast.LENGTH_SHORT).show()
                render()
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        vouchersListener?.let { VoucherRepository.removeVouchersListener(uid, it) }
        rulesListener?.let { VoucherRepository.removeAllRulesListener(it) }
        pointsListener?.let { PointRepository.removePointsListener(uid, it) }
    }

    private fun shopName(id: String) = vendorMap[id]?.vendorName?.ifBlank { null } ?: getString(R.string.k_local_shop)

    private fun isLive(v: VoucherUser, now: Long) =
        v.voucherStatus == VoucherStatus.ACTIVE && (v.voucherExpiryDate == 0L || v.voucherExpiryDate > now)

    private fun render() {
        if (loaded != 7) return
        binding.pbLoading.visibility = View.GONE
        val now = ServerClock.now()
        val live = vouchers.filter { isLive(it, now) }
        val heldRules = live.map { it.voucherID }.toSet()

        val ready = rules.filter {
            vendorMap.containsKey(it.vendorID) && it.voucherID !in heldRules && it.pointsRequired > 0 &&
                (perShop[it.vendorID] ?: 0) >= it.pointsRequired
        }.sortedBy { shopName(it.vendorID).lowercase() }
        val history = vouchers.filter { !isLive(it, now) }.sortedByDescending { it.createdAt }
        val towards = rules.filter {
            vendorMap.containsKey(it.vendorID) && it.voucherID !in heldRules &&
                (perShop[it.vendorID] ?: 0) in 1 until it.pointsRequired
        }.sortedBy { it.pointsRequired - (perShop[it.vendorID] ?: 0) }

        if (section == null) section = when {
            ready.isNotEmpty() -> Section.READY
            live.isNotEmpty() -> Section.VOUCHERS
            else -> Section.TOWARDS
        }

        bindSections(
            listOf(
                Section.READY to getString(R.string.hu_tab_ready, ready.size),
                Section.VOUCHERS to getString(R.string.hu_tab_vouchers, live.size),
                Section.TOWARDS to getString(R.string.hu_tab_towards, towards.size)
            )
        )

        val list = binding.list
        list.removeAllViews()
        when (section) {
            Section.READY -> ready.forEach { rule ->
                val shop = shopName(rule.vendorID)
                val bal = perShop[rule.vendorID] ?: 0
                val card = RewardCard.inflate(list)
                RewardCard.bind(
                    card, rule.vendorID, shop, rule.voucherName,
                    getString(R.string.hu_you_have_points, bal),
                    getString(R.string.hu_claim, rule.pointsRequired)
                ) { VoucherClaimHelper.confirmAndClaim(this, uid, rule, shop) }
                addWithGap(card.root, 14)
            }
            Section.VOUCHERS -> {
                val fmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                live.sortedBy { if (it.voucherExpiryDate == 0L) Long.MAX_VALUE else it.voucherExpiryDate }.forEach { v ->
                    val shop = shopName(v.vendorID)
                    val bal = perShop[v.vendorID] ?: 0
                    val locked = !v.pointsDeducted && bal < v.pointsRequiredSnapshot
                    val row = RewardRow.inflate(list)
                    val state = when {
                        locked -> getString(R.string.hu_need_more, v.pointsRequiredSnapshot - bal)
                        v.voucherExpiryDate > 0 -> getString(R.string.hu_use_by, fmt.format(Date(v.voucherExpiryDate)))
                        else -> getString(R.string.hu_voucher_detail)
                    }
                    RewardRow.bind(
                        row, v.vendorID, shop, v.voucherTitle.ifBlank { getString(R.string.k_reward_voucher) }, shop,
                        state, !locked, if (locked) null else getString(R.string.hu_show_qr_short),
                        onAction = { showVoucherQrDialog(v, shop) }
                    )
                    list.addView(row.root)
                }
                if (history.isNotEmpty()) {
                    list.addView(sectionLabel(getString(R.string.hu_history)))
                    history.take(20).forEach { v ->
                        val row = RewardRow.inflate(list)
                        val state = getString(if (v.voucherStatus == VoucherStatus.USED) R.string.hu_used_on else R.string.hu_expired)
                        RewardRow.bind(row, v.vendorID, shopName(v.vendorID), v.voucherTitle.ifBlank { getString(R.string.k_reward_voucher) },
                            shopName(v.vendorID), state, false, null, null)
                        row.root.alpha = 0.75f
                        list.addView(row.root)
                    }
                }
            }
            Section.TOWARDS, null -> towards.forEach { rule ->
                val shop = shopName(rule.vendorID)
                val bal = perShop[rule.vendorID] ?: 0
                val row = RewardRow.inflate(list)
                RewardRow.bind(
                    row, rule.vendorID, shop, rule.voucherName, getString(R.string.hu_n_points_at, rule.pointsRequired, shop),
                    getString(R.string.hu_need_more, rule.pointsRequired - bal), false, null, null
                )
                row.root.setOnClickListener {
                    startActivity(Intent(this, ShopDetailActivity::class.java).putExtra(ShopDetailActivity.EXTRA_VENDOR_ID, rule.vendorID))
                }
                row.root.isClickable = true
                list.addView(row.root)
            }
        }

        val empty = when (section) {
            Section.READY -> ready.isEmpty()
            Section.VOUCHERS -> live.isEmpty() && history.isEmpty()
            else -> towards.isEmpty()
        }
        binding.tvEmpty.setText(
            when (section) {
                Section.READY -> R.string.hu_empty_ready
                Section.VOUCHERS -> R.string.hu_empty_vouchers
                else -> if (ready.isNotEmpty()) R.string.hu_empty_towards_all_ready else R.string.hu_empty_towards
            }
        )
        binding.emptyBlock.visibility = if (empty) View.VISIBLE else View.GONE
    }

    /** Equal-width segmented switch; the selected section is ink, the others quiet. */
    private fun bindSections(items: List<Pair<Section, String>>) {
        val seg = binding.segSections
        seg.removeAllViews()
        items.forEach { (s, label) ->
            val on = s == section
            val tv = TextView(this).apply {
                text = label
                gravity = android.view.Gravity.CENTER
                minHeight = (48 * resources.displayMetrics.density).toInt()
                setPadding(8, 8, 8, 8)
                textSize = 15f
                typeface = androidx.core.content.res.ResourcesCompat.getFont(this@MyVouchersActivity, R.font.figtree_bold)
                setTextColor(getColor(if (on) R.color.hu_bg else R.color.hu_ink))
                if (on) setBackgroundResource(R.drawable.bg_hu_seg_on)
                isClickable = true
                isFocusable = true
                isSelected = on
                contentDescription = if (on) getString(R.string.nav_tab_selected_cd, label) else label
                setOnClickListener { section = s; render() }
            }
            seg.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
    }

    private fun addWithGap(v: View, gapDp: Int) {
        binding.list.addView(v, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (gapDp * resources.displayMetrics.density).toInt() })
    }

    private fun sectionLabel(text: String): TextView = TextView(this).apply {
        androidx.core.widget.TextViewCompat.setTextAppearance(this, R.style.HU_Text_Section)
        this.text = text
        setPadding(0, (24 * resources.displayMetrics.density).toInt(), 0, 0)
    }

    private fun showVoucherQrDialog(v: VoucherUser, shop: String) {
        val d = DialogVoucherQrBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this).setView(d.root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.tvDialogShopName.text = shop
        d.tvDialogRewardTitle.text = v.voucherTitle.ifBlank { getString(R.string.k_reward_voucher) }
        d.tvDialogVoucherCode.text = v.voucherCode
        val fmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        d.tvDialogExpiry.text = if (v.voucherExpiryDate > 0L) getString(R.string.k_valid_until, fmt.format(Date(v.voucherExpiryDate)))
        else getString(R.string.k_no_expiry)
        d.btnDialogClose.setOnClickListener { dialog.dismiss() }
        d.pbQrLoading.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.Default) {
            val qr = generateQrBitmap(v.voucherCode, 512)
            withContext(Dispatchers.Main) {
                d.pbQrLoading.visibility = View.GONE
                if (qr != null) d.ivVoucherQr.setImageBitmap(qr)
                else Toast.makeText(this@MyVouchersActivity, getString(R.string.k_could_not_make_qr), Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun generateQrBitmap(value: String, size: Int): Bitmap? = try {
        val m = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, size, size)
        Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
            for (x in 0 until size) for (y in 0 until size) setPixel(x, y, if (m[x, y]) Color.BLACK else Color.WHITE)
        }
    } catch (e: Exception) { null }
}
