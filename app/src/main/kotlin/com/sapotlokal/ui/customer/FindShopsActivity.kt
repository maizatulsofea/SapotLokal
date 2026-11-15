package com.sapotlokal.ui.customer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ValueEventListener
import com.sapotlokal.R
import com.sapotlokal.data.model.ShopCategory
import com.sapotlokal.data.model.Vendor
import com.sapotlokal.data.model.Voucher
import com.sapotlokal.data.model.VoucherStatus
import com.sapotlokal.data.model.VoucherUser
import com.sapotlokal.data.repository.PointRepository
import com.sapotlokal.data.repository.VendorRepository
import com.sapotlokal.data.repository.VoucherRepository
import com.sapotlokal.databinding.ActivityFindShopsBinding
import com.sapotlokal.util.ServerClock

/**
 * Shops tab. One job: "where can I earn?"
 * Search, a scope filter (My shops · Open now · All), category chips built from the shops that
 * exist, then one stacked colour card per shop: points, cup stamps, open status, next reward.
 * Shops where the customer holds points come first; ready ones first of all.
 */
class FindShopsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFindShopsBinding
    private val auth = FirebaseAuth.getInstance()

    private var vendorsListener: ValueEventListener? = null
    private var pointsListener: ValueEventListener? = null
    private var rulesListener: ValueEventListener? = null
    private var vouchersListener: ValueEventListener? = null
    private var userId = ""

    private var allVendors: List<Vendor> = emptyList()
    private var perShop: Map<String, Int> = emptyMap()
    private var visited: Set<String> = emptySet()
    private var rules: List<Voucher> = emptyList()
    private var vouchers: List<VoucherUser> = emptyList()
    private var query = ""
    private var category = ALL
    private var categories: List<String> = emptyList()
    private var scope = Scope.ALL
    private var openedOnVisited = false

    private enum class Scope { VISITED, OPEN, ALL }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFindShopsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.etSearch.doAfterTextChanged { query = it?.toString().orEmpty().trim(); render() }
        userId = auth.currentUser?.uid.orEmpty()
        if (intent.getBooleanExtra(EXTRA_VISITED, false)) { scope = Scope.VISITED; openedOnVisited = true }
        buildScopeChips()
        startListeners()
        CustomerNav.bind(this, binding.customerNav, CustomerNav.Tab.SHOPS)
    }

    private fun buildScopeChips() {
        binding.chipScope.removeAllViews()
        listOf(
            Scope.VISITED to R.string.hu_filter_visited,
            Scope.OPEN to R.string.hu_filter_open,
            Scope.ALL to R.string.hu_filter_all
        ).forEach { (s, label) ->
            binding.chipScope.addView(HuChip.make(this, getString(label), s == scope) { scope = s; render() })
        }
    }

    private fun startListeners() {
        binding.progressBar.visibility = View.VISIBLE
        if (userId.isNotBlank()) {
            pointsListener = PointRepository.listenToPointsForUser(
                userID = userId,
                onVisits = { v ->
                    // Someone who has been to a shop starts on Visited, once.
                    if (visited.isEmpty() && v.isNotEmpty() && !openedOnVisited) { scope = Scope.VISITED; openedOnVisited = true; buildScopeChips() }
                    visited = v.keys
                },
                onUpdate = { shopPoints, _, _ ->
                    perShop = shopPoints
                    render()
                },
                onError = { render() }
            )
            vouchersListener = VoucherRepository.listenToVouchersForUser(
                userID = userId, onUpdate = { vouchers = it; render() }, onError = { }
            )
        }
        rulesListener = VoucherRepository.listenToAllActiveRules(onUpdate = { rules = it; render() }, onError = { })
        vendorsListener = VendorRepository.listenToAllVendors(
            onUpdate = { v ->
                allVendors = v
                binding.progressBar.visibility = View.GONE
                rebuildCategoryChips()
                render()
            },
            onError = {
                binding.progressBar.visibility = View.GONE
                binding.tvEmpty.text = getString(R.string.k_could_not_load_shops)
                binding.tvEmpty.visibility = View.VISIBLE
            }
        )
    }

    /** The categories that exist, most common first; the type chip picks one of them or all. */
    private fun rebuildCategoryChips() {
        categories = allVendors.map { categoryKey(it.vendorCategory) }.filter { it.isNotBlank() }
            .groupingBy { it.lowercase() }.eachCount().entries.sortedByDescending { it.value }
            .map { e -> allVendors.map { categoryKey(it.vendorCategory) }.first { it.lowercase() == e.key } }
        if (category != ALL && categories.none { it.equals(category, true) }) category = ALL
        bindTypeChip()
    }

    private fun typeLabel(key: String) = if (key == ALL) getString(R.string.hu_all_types) else ShopCategory.label(this, key)

    /** One chip at the end of the filter row: "All types ▾" or the picked type, opening a picker. */
    private fun bindTypeChip() {
        binding.typeChipSlot.removeAllViews()
        val chip = HuChip.make(this, getString(R.string.hu_type_chip, typeLabel(category)), category != ALL) { showTypePicker() }
        binding.typeChipSlot.addView(chip)
    }

    private fun showTypePicker() {
        val keys = listOf(ALL) + categories
        val picked = keys.indexOfFirst { it.equals(category, true) }.coerceAtLeast(0)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.label_shop_category)
            .setSingleChoiceItems(keys.map { typeLabel(it) }.toTypedArray(), picked) { dialog, i ->
                category = keys[i]
                bindTypeChip()
                render()
                dialog.dismiss()
            }
            .show()
    }

    private fun render() {
        if (allVendors.isEmpty()) return
        val now = ServerClock.now()
        val live = vouchers.filter {
            it.voucherStatus == VoucherStatus.ACTIVE && (it.voucherExpiryDate == 0L || it.voucherExpiryDate > now)
        }
        val held = live.map { it.voucherID }.toSet()
        fun readyRule(id: String): Voucher? = rules.filter {
            it.vendorID == id && it.voucherID !in held && it.pointsRequired > 0 && (perShop[id] ?: 0) >= it.pointsRequired
        }.minByOrNull { it.pointsRequired }
        fun nextRule(id: String): Voucher? = rules.filter { it.vendorID == id && it.pointsRequired > (perShop[id] ?: 0) }
            .minByOrNull { it.pointsRequired }

        val q = query.lowercase()
        val list = allVendors.filter { v ->
            val bal = perShop[v.vendorID] ?: 0
            val catText = ShopCategory.label(this, v.vendorCategory)
            (scope != Scope.VISITED || v.vendorID in visited) &&
                (scope != Scope.OPEN || v.isOpen) &&
                (category == ALL || categoryKey(v.vendorCategory).equals(category, true)) &&
                (q.isEmpty() || "${v.vendorName} ${v.vendorAddress} ${v.vendorCategory} $catText".lowercase().contains(q))
        }.sortedWith(
            compareByDescending<Vendor> { readyRule(it.vendorID) != null || live.any { l -> l.vendorID == it.vendorID } }
                .thenByDescending { perShop[it.vendorID] ?: 0 }
                .thenByDescending { it.isOpen }
                .thenBy { it.vendorName.lowercase() }
        )

        val container = binding.shopCards
        container.removeAllViews()
        list.forEach { v ->
            val bal = perShop[v.vendorID] ?: 0
            val ready = readyRule(v.vendorID)
            val card = ShopCard.inflate(container)
            ShopCard.bind(
                card, v, v.vendorID, bal, readyReward = ready,
                holdsVoucher = live.any { it.vendorID == v.vendorID }, nextReward = nextRule(v.vendorID),
                visited = v.vendorID in visited
            )
            card.root.setOnClickListener {
                startActivity(Intent(this, ShopDetailActivity::class.java).putExtra(ShopDetailActivity.EXTRA_VENDOR_ID, v.vendorID))
            }
            container.addView(card.root, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (12 * resources.displayMetrics.density).toInt() })
        }
        binding.tvEmpty.text = getString(if (scope == Scope.VISITED && q.isEmpty()) R.string.hu_no_shops_visited else R.string.hu_no_shops_match)
        binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        vendorsListener?.let { VendorRepository.removeAllVendorsListener(it) }
        pointsListener?.let { if (userId.isNotBlank()) PointRepository.removePointsListener(userId, it) }
        rulesListener?.let { VoucherRepository.removeAllRulesListener(it) }
        vouchersListener?.let { if (userId.isNotBlank()) VoucherRepository.removeVouchersListener(userId, it) }
    }

    private fun categoryKey(raw: String): String = ShopCategory.fromStored(raw)?.value ?: raw.trim()

    companion object {
        private const val ALL = "All shops"

        /** Open on the Visited filter (Home's "See all"). */
        const val EXTRA_VISITED = "extra_visited"
    }
}
