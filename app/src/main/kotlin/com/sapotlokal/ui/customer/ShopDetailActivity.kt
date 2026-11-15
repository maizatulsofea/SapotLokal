package com.sapotlokal.ui.customer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ValueEventListener
import com.sapotlokal.AppConfig
import com.sapotlokal.R
import com.sapotlokal.data.model.Vendor
import com.sapotlokal.data.model.Voucher
import com.sapotlokal.data.model.VoucherStatus
import com.sapotlokal.data.model.VoucherUser
import com.sapotlokal.data.repository.PointRepository
import com.sapotlokal.data.repository.VendorRepository
import com.sapotlokal.data.repository.VoucherRepository
import com.sapotlokal.databinding.ActivityShopDetailBinding
import com.sapotlokal.util.ServerClock
import com.sapotlokal.util.VoucherClaimHelper

/**
 * Shop page. One job: "what can I get here?"
 * The shop card (photo, your points here, open status, progress), about, points per visit, the
 * rewards with Claim (spends points at claim time), then location, hours and Maps. Viewing a shop follows it for "now open" alerts, as before.
 */
class ShopDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VENDOR_ID = "extra_vendor_id"
    }

    private lateinit var binding: ActivityShopDetailBinding
    private val auth = FirebaseAuth.getInstance()

    private var vendorId = ""
    private var vendor: Vendor? = null
    private var balance = 0
    private var myRank = AppConfig.Rank.JIRAN_BARU
    private var visitedHere = false
    private var rules: List<Voucher> = emptyList()
    private var myVouchers: List<VoucherUser> = emptyList()

    private var vendorListener: ValueEventListener? = null
    private var pointsListener: ValueEventListener? = null
    private var rulesListener: ValueEventListener? = null
    private var vouchersListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShopDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        vendorId = intent.getStringExtra(EXTRA_VENDOR_ID).orEmpty()
        if (vendorId.isBlank()) {
            Toast.makeText(this, getString(R.string.k_shop_not_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        com.sapotlokal.util.FollowedShops.follow(this, vendorId)
        binding.btnBack.setOnClickListener { finish() }
        binding.btnOpenMaps.setOnClickListener {
            val address = vendor?.vendorAddress.orEmpty().trim()
            if (address.isBlank()) Toast.makeText(this, getString(R.string.k_shop_address_na), Toast.LENGTH_SHORT).show()
            else openInMaps(address)
        }
        loadData()

        // Subscribe to this shop's FCM topic so its "now open" alerts arrive.
        com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic("shop_$vendorId")
    }

    private fun loadData() {
        vendorListener = VendorRepository.listenToVendor(
            vendorID = vendorId,
            onUpdate = { v ->
                if (v == null) {
                    Toast.makeText(this, getString(R.string.k_shop_not_available), Toast.LENGTH_SHORT).show()
                    finish()
                    return@listenToVendor
                }
                vendor = v
                binding.progressBar.visibility = View.GONE
                binding.layoutContent.visibility = View.VISIBLE
                render()
            },
            onError = { msg ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, getString(R.string.k_error_loading_shop, msg), Toast.LENGTH_SHORT).show()
            }
        )
        val uid = auth.currentUser?.uid
        if (!uid.isNullOrBlank()) {
            com.sapotlokal.data.repository.XpRepository.fetchStanding(uid) { myRank = it.rank; render() }
            pointsListener = PointRepository.listenToPointsForUser(
                userID = uid,
                onVisits = { visitedHere = it.containsKey(vendorId) },
                onUpdate = { perShop, _, _ -> balance = perShop[vendorId] ?: 0; render() },
                onError = { render() }
            )
            vouchersListener = VoucherRepository.listenToVouchersForUser(
                userID = uid, onUpdate = { myVouchers = it; render() }, onError = { }
            )
        }
        rulesListener = VoucherRepository.listenToRulesForVendor(
            vendorID = vendorId,
            onUpdate = { r -> rules = r.filter { it.isActive }.sortedBy { it.pointsRequired }; render() },
            onError = { rules = emptyList(); render() }
        )
    }

    private fun render() {
        val v = vendor ?: return
        val name = v.vendorName.ifBlank { getString(R.string.k_local_shop) }
        binding.tvTitle.text = name

        val now = ServerClock.now()
        val held = myVouchers.filter {
            it.vendorID == vendorId && it.voucherStatus == VoucherStatus.ACTIVE &&
                (it.voucherExpiryDate == 0L || it.voucherExpiryDate > now)
        }.map { it.voucherID }.toSet()
        val claimable = rules.firstOrNull { it.voucherID !in held && it.pointsRequired > 0 && balance >= it.pointsRequired }
        val next = rules.firstOrNull { it.pointsRequired > balance }

        // The shop card; the title above already names the shop.
        binding.shopCardSlot.removeAllViews()
        val card = ShopCard.inflate(binding.shopCardSlot)
        ShopCard.bind(card, v, vendorId, balance, readyReward = claimable, holdsVoucher = held.isNotEmpty(), nextReward = next, visited = visitedHere)
        card.tvName.visibility = View.GONE
        card.root.isClickable = false
        card.root.foreground = null
        binding.shopCardSlot.addView(card.root)

        val rate = v.pointsPerVisit.takeIf { it in AppConfig.ALLOWED_POINTS_PER_VISIT } ?: AppConfig.DEFAULT_POINTS_PER_VISIT
        val mine = ShopStyle.perVisit(v, myRank)
        binding.tvPointsRule.text = if (mine > rate) {
            getString(R.string.hu_points_rule_you, rate, mine)
        } else {
            getString(R.string.hu_points_rule, rate)
        }

        // Rewards
        val rows = binding.rewardRows
        rows.removeAllViews()
        binding.tvNoVouchers.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
        rules.forEach { rule ->
            val row = RewardRow.inflate(rows)
            val sub = rule.voucherDescription
            when {
                rule.voucherID in held -> RewardRow.bind(
                    row, vendorId, name, rule.voucherName, sub, getString(R.string.hu_already_claimed), true,
                    getString(R.string.hu_show_qr), { startActivity(Intent(this, MyVouchersActivity::class.java)) }, showThumb = false
                )
                balance >= rule.pointsRequired && rule.pointsRequired > 0 -> RewardRow.bind(
                    row, vendorId, name, rule.voucherName, sub, "", true,
                    getString(R.string.hu_claim, rule.pointsRequired), {
                        val uid = auth.currentUser?.uid.orEmpty()
                        VoucherClaimHelper.confirmAndClaim(this, uid, rule, name)
                    }, showThumb = false
                )
                else -> RewardRow.bind(
                    row, vendorId, name, rule.voucherName, sub,
                    getString(R.string.hu_need_more, rule.pointsRequired - balance), false, null, null, showThumb = false
                )
            }
            rows.addView(row.root)
        }

        // About, location, hours, status
        binding.tvShopDescription.text = v.vendorDescription
        binding.tvShopDescription.visibility = if (v.vendorDescription.isBlank()) View.GONE else View.VISIBLE
        binding.tvShopAddress.text = v.vendorAddress.ifBlank { getString(R.string.k_address_not_specified) }
        binding.tvOperatingHours.text = getString(R.string.hu_hours, v.operatingHours.ifBlank { getString(R.string.k_hours_not_specified) })
    }

    private fun openInMaps(address: String) {
        val uri = Uri.parse("geo:0,0?q=${Uri.encode(address)}")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps"))
        } catch (e: ActivityNotFoundException) {
            try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (e2: Exception) {
                Toast.makeText(this, getString(R.string.k_no_map_app), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        vendorListener?.let { VendorRepository.removeVendorListener(vendorId, it) }
        val uid = auth.currentUser?.uid
        if (uid != null) {
            pointsListener?.let { PointRepository.removePointsListener(uid, it) }
            vouchersListener?.let { VoucherRepository.removeVouchersListener(uid, it) }
        }
        rulesListener?.let { VoucherRepository.removeRulesListener(vendorId, it) }
    }
}
