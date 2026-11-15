package com.sapotlokal.ui.customer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.sapotlokal.AppConfig
import com.sapotlokal.R
import com.sapotlokal.data.model.Vendor
import com.sapotlokal.data.model.Voucher
import com.sapotlokal.data.model.VoucherStatus
import com.sapotlokal.data.model.VoucherUser
import com.sapotlokal.data.repository.GamesRepository
import com.sapotlokal.data.repository.PointRepository
import com.sapotlokal.databinding.ActivityCustomerHomeBinding
import com.sapotlokal.databinding.ViewHuFirstScanBinding
import com.sapotlokal.service.SapotFcmService
import com.sapotlokal.ui.auth.LoginActivity
import com.sapotlokal.util.ServerClock
import com.sapotlokal.util.VoucherClaimHelper

/**
 * Customer Home. One job: "what can I do now?"
 *
 * 1. Header: time-of-day greeting and name (opens Me) and a rank chip (opens Play).
 * 2. Any unclaimed top-3 prize.
 * 3. One reward card: a reward to claim now (claiming spends that shop's points at claim
 *    time via [VoucherClaimHelper]), else a claimed voucher to show, else the closest reward.
 *    A brand-new customer sees a "Scan your first shop" card instead.
 * 4. Recently visited: every shop the customer has been to, newest visit first, as a row of
 *    photo cards (ShopCard). "See all" opens Shops on the Visited filter.
 * 5. Today in Play: free spin and the nearest mission, opening the Play tab.
 *
 * Also shows the rank-up sheet once per new rank, syncs the FCM token, asks for notification
 * permission, and raises "shop is open" alerts for shops the customer follows.
 */
class CustomerHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomerHomeBinding
    private val auth = FirebaseAuth.getInstance()

    private var pointsListener: ValueEventListener? = null
    private var userListener: ValueEventListener? = null
    private var vendorsListener: ValueEventListener? = null
    private var missions: com.sapotlokal.data.repository.MissionRepository.Listening? = null
    private var vouchersListener: ValueEventListener? = null
    private var rulesListener: ValueEventListener? = null

    private var currentUserName = ""
    private var currentAvatarId = ""

    private var pointsLoaded = false
    private var lastPerShop: Map<String, Int> = emptyMap()
    /** Shops visited at least once → time of the latest visit. Kept even when the points are spent. */
    private var lastVisitAt: Map<String, Long> = emptyMap()
    private var vendorsById: Map<String, Vendor> = emptyMap()
    private var myVouchers: List<VoucherUser> = emptyList()
    private var allRules: List<Voucher> = emptyList()
    private var missionLine: String? = null
    private var spinReady: Boolean? = null

    /** Vendors this customer has earned points at — only their "now OPEN" alerts are shown. */
    @Volatile private var followedVendorIds: Set<String> = emptySet()

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) auth.currentUser?.uid?.let { SapotFcmService.syncTokenForUser(it) } }

    private var shopNotificationListener: com.google.firebase.database.ChildEventListener? = null
    private var shopNotifQuery: com.google.firebase.database.Query? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomerHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val user = auth.currentUser ?: run { goToLogin(); return }

        val prefs = getSharedPreferences("sapot_user_prefs", MODE_PRIVATE)
        currentUserName = prefs.getString("cached_user_name", "").orEmpty()
        currentAvatarId = prefs.getString("cached_user_avatar", "").orEmpty()
        updateGreeting()

        binding.btnMe.setOnClickListener { openMe() }
        binding.tvRankChip.setOnClickListener { startActivity(Intent(this, ChallengesActivity::class.java)) }
        binding.btnAllShops.setOnClickListener {
            startActivity(Intent(this, FindShopsActivity::class.java).putExtra(FindShopsActivity.EXTRA_VISITED, true))
        }
        binding.tvMoreReady.setOnClickListener { openRewards() }
        binding.playStrip.setOnClickListener { startActivity(Intent(this, ChallengesActivity::class.java)) }
        binding.btnRetry.setOnClickListener {
            binding.errorBlock.visibility = View.GONE
            pointsListener?.let { PointRepository.removePointsListener(user.uid, it) }
            startListeningToPoints(user.uid)
        }

        startListeningToUser(user.uid)
        startListeningToPoints(user.uid)
        checkNotificationPermission(user.uid)
        startListeningToShopNotifications()

        CustomerNav.bind(this, binding.customerNav, CustomerNav.Tab.HOME)
        HowPoints.showOnce(this, user.uid)
    }

    // ── Header & level ────────────────────────────────────────────────────────

    private fun updateGreeting() {
        val hour = java.time.Instant.ofEpochMilli(ServerClock.now()).atZone(com.sapotlokal.util.MissionDateUtils.KL_ZONE).hour
        binding.tvGreetingTime.setText(
            when (hour) {
                in 5..11 -> R.string.home_greet_morning
                in 12..14 -> R.string.home_greet_afternoon
                in 15..18 -> R.string.home_greet_evening
                else -> R.string.home_greet_night
            }
        )
        val name = currentUserName.ifBlank { getString(R.string.k_shopper) }
        binding.tvGreeting.text = name
        binding.btnMe.contentDescription = getString(R.string.hu_open_me_cd, name)
        val avatar = com.sapotlokal.data.model.AvatarDef.fromId(currentAvatarId)
        if (avatar != null) binding.ivAvatar.setImageResource(avatar.drawableRes)
        else binding.ivAvatar.setImageResource(R.drawable.ic_user_round)
    }

    // ── Rewards logic (shared rules with Rewards and the Shop page) ───────────

    private fun liveVouchers(): List<VoucherUser> {
        val now = ServerClock.now()
        return myVouchers.filter {
            it.voucherStatus == VoucherStatus.ACTIVE && (it.voucherExpiryDate == 0L || it.voucherExpiryDate > now)
        }
    }

    private fun claimableRules(): List<Voucher> {
        val held = liveVouchers().map { it.voucherID }.toSet()
        return allRules.filter {
            it.voucherID !in held && vendorsById.containsKey(it.vendorID) && it.pointsRequired > 0 &&
                (lastPerShop[it.vendorID] ?: 0) >= it.pointsRequired
        }.sortedBy { shopName(it.vendorID).lowercase() }
    }

    private fun nextRewardAt(vendorId: String): Voucher? {
        val balance = lastPerShop[vendorId] ?: 0
        return allRules.filter { it.vendorID == vendorId && it.pointsRequired > balance }.minByOrNull { it.pointsRequired }
    }

    private fun refreshAll() {
        if (!pointsLoaded) return
        bindReadyCard()
        bindShops()
        bindPlay()
    }

    private fun bindReadyCard() {
        val slot = binding.readySlot
        slot.removeAllViews()
        val claimable = claimableRules()
        val vouchers = liveVouchers()
        val readyCount = claimable.size + vouchers.size

        if (lastVisitAt.isEmpty() && vouchers.isEmpty()) {
            val first = ViewHuFirstScanBinding.inflate(LayoutInflater.from(this), slot, true)
            first.btnFirstScan.setOnClickListener { startActivity(Intent(this, ScanActivity::class.java)) }
            binding.tvMoreReady.visibility = View.GONE
            return
        }

        val card = RewardCard.inflate(slot)
        val rule = claimable.firstOrNull()
        val voucher = vouchers.firstOrNull()
        when {
            rule != null -> {
                val balance = lastPerShop[rule.vendorID] ?: 0
                val shop = shopName(rule.vendorID)
                RewardCard.bind(
                    card, rule.vendorID, shop, rule.voucherName,
                    getString(R.string.hu_you_have_points, balance),
                    getString(R.string.hu_claim, rule.pointsRequired)
                ) {
                    val uid = auth.currentUser?.uid ?: return@bind
                    VoucherClaimHelper.confirmAndClaim(this, uid, rule, shop)
                }
            }
            voucher != null -> RewardCard.bind(
                card, voucher.vendorID, shopName(voucher.vendorID),
                voucher.voucherTitle.ifBlank { getString(R.string.k_reward) },
                getString(R.string.hu_voucher_detail), getString(R.string.hu_show_qr)
            ) { openRewards() }
            else -> {
                val held = vouchers.map { it.voucherID }.toSet()
                val next = allRules
                    .filter { it.voucherID !in held && vendorsById.containsKey(it.vendorID) }
                    .minByOrNull { (it.pointsRequired - (lastPerShop[it.vendorID] ?: 0)).coerceAtLeast(0) }
                if (next == null) {
                    binding.tvMoreReady.visibility = View.GONE
                    return
                }
                val remaining = (next.pointsRequired - (lastPerShop[next.vendorID] ?: 0)).coerceAtLeast(1)
                RewardCard.bind(
                    card, next.vendorID, shopName(next.vendorID), next.voucherName,
                    getString(R.string.hu_next_detail, remaining), getString(R.string.hu_scan_a_shop)
                ) { startActivity(Intent(this, ScanActivity::class.java)) }
                card.root.setOnClickListener { openShop(next.vendorID) }
                // Scanning is a quiet outlined action here; Claim stays the only orange button.
                card.btnAction.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.hu_bg))
                card.btnAction.setStrokeColorResource(R.color.hu_line)
                card.btnAction.strokeWidth = dp(2)
            }
        }
        slot.addView(card.root)

        val more = readyCount - 1
        if (more > 0) {
            binding.tvMoreReady.text = getString(R.string.hu_more_ready, more)
            binding.tvMoreReady.visibility = View.VISIBLE
        } else {
            binding.tvMoreReady.visibility = View.GONE
        }
    }

    private fun bindShops() {
        val container = binding.shopCards
        container.removeAllViews()
        // Newest visit first; the reward card above already covers anything ready to claim.
        val recent = lastVisitAt.filterKeys { it.isNotBlank() && vendorsById.containsKey(it) }
            .entries.sortedByDescending { it.value }
            .map { it.key }
            .take(HOME_SHOP_CARDS)
        binding.shopsHeader.visibility = if (recent.isEmpty()) View.GONE else View.VISIBLE
        binding.shopRow.visibility = binding.shopsHeader.visibility
        val vouchers = liveVouchers()
        recent.forEachIndexed { i, vendorId ->
            val card = ShopCard.inflate(container)
            ShopCard.bind(
                card, vendorsById[vendorId], vendorId, lastPerShop[vendorId] ?: 0,
                readyReward = claimableRules().firstOrNull { it.vendorID == vendorId },
                holdsVoucher = vouchers.any { it.vendorID == vendorId },
                nextReward = nextRewardAt(vendorId),
                visited = true
            )
            card.root.setOnClickListener { openShop(vendorId) }
            // One shop fills the row; with more, each card leaves a peek of the next so the row reads as scrollable.
            val params = if (recent.size == 1) {
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            } else {
                LinearLayout.LayoutParams(dp(HOME_CARD_WIDTH_DP), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            container.addView(card.root, params.apply { if (i > 0) marginStart = dp(12) })
        }
    }

    private fun bindPlay() {
        val parts = listOfNotNull(
            if (spinReady == true) getString(R.string.hu_spin_ready) else null,
            missionLine
        )
        binding.tvPlayLine.text = parts.joinToString(" · ").ifBlank { getString(R.string.hu_play_missions) }
    }

    // ── Listeners ─────────────────────────────────────────────────────────────

    private fun startListeningToPoints(uid: String) {
        pointsListener = PointRepository.listenToPointsForUser(
            userID = uid,
            onVisits = { lastVisitAt = it },
            onUpdate = { perShop, _, _ ->
                followedVendorIds = perShop.keys.filter { it.isNotBlank() }.toSet()
                lastPerShop = perShop
                pointsLoaded = true
                binding.errorBlock.visibility = View.GONE
                refreshAll()
            },
            onError = { if (!pointsLoaded) binding.errorBlock.visibility = View.VISIBLE }
        )
    }

    private fun startListeningToUser(uid: String) {
        val ref = FirebaseDatabase.getInstance(AppConfig.DATABASE_URL).reference.child("users").child(uid)
        userListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val name = snapshot.child("userName").getValue(String::class.java)?.trim().orEmpty()
                currentAvatarId = snapshot.child("avatar").getValue(String::class.java).orEmpty()
                if (name.isNotBlank()) currentUserName = name
                val standing = com.sapotlokal.data.repository.XpRepository.standingOf(snapshot)
                binding.tvRankChip.text = getString(
                    R.string.hu_rank_chip, standing.rank.level, java.text.NumberFormat.getIntegerInstance().format(standing.xp)
                )
                binding.tvRankChip.contentDescription = getString(
                    R.string.rank_card_cd, standing.rank.title, standing.rank.level, java.text.NumberFormat.getIntegerInstance().format(standing.xp)
                )
                RankCard.maybeShowRankUp(this@CustomerHomeActivity, uid, standing)
                updateGreeting()
                cacheUser()
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(userListener!!)
    }

    private fun cacheUser() {
        getSharedPreferences("sapot_user_prefs", MODE_PRIVATE).edit()
            .putString("cached_user_name", currentUserName)
            .putString("cached_user_avatar", currentAvatarId)
            .apply()
    }

    private fun startListeningToVendors() {
        if (vendorsListener != null) return
        vendorsListener = com.sapotlokal.data.repository.VendorRepository.listenToAllVendors(
            onUpdate = { vendors -> vendorsById = vendors.associateBy { it.vendorID }; refreshAll() },
            onError = { }
        )
    }

    private fun startRewardListeners(uid: String) {
        if (vouchersListener == null) {
            vouchersListener = com.sapotlokal.data.repository.VoucherRepository.listenToVouchersForUser(
                userID = uid, onUpdate = { myVouchers = it; unlockTreatBadge(uid); refreshAll() }, onError = { }
            )
        }
        if (rulesListener == null) {
            rulesListener = com.sapotlokal.data.repository.VoucherRepository.listenToAllActiveRules(
                onUpdate = { allRules = it; refreshAll() }, onError = { }
            )
        }
    }

    /** Belanja Diri: the shop marks the voucher used, so the customer's own app unlocks the badge when it sees one. */
    private fun unlockTreatBadge(uid: String) {
        if (myVouchers.any { it.voucherStatus == VoucherStatus.USED }) {
            com.sapotlokal.data.repository.BadgeRepository.unlockNow(uid, com.sapotlokal.data.model.BadgeDef.BELANJA_DIRI)
        }
    }

    private fun startMissionPreview(uid: String) {
        if (missions != null) return
        missions = com.sapotlokal.data.repository.MissionRepository.listen(uid) { list ->
            val ready = list.count { it.claimable }
            // Something to claim beats progress; otherwise the weekly mission closest to done.
            val pick = list.filter { it.def.weekly && !it.done }.maxByOrNull { it.progress.toFloat() / it.def.target }
            missionLine = when {
                ready > 0 -> resources.getQuantityString(R.plurals.hu_missions_to_claim, ready, ready)
                pick != null -> getString(R.string.hu_mission_progress, getString(pick.def.nameRes, pick.def.target), pick.progressShown, pick.def.target)
                else -> null
            }
            bindPlay()
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun shopName(vendorId: String): String =
        vendorsById[vendorId]?.vendorName?.ifBlank { null } ?: getString(R.string.k_shop)

    private fun openRewards() = startActivity(Intent(this, MyVouchersActivity::class.java))

    private fun openMe() = startActivity(Intent(this, CustomerProfileActivity::class.java))

    private fun openShop(vendorId: String) = startActivity(
        Intent(this, ShopDetailActivity::class.java).putExtra(ShopDetailActivity.EXTRA_VENDOR_ID, vendorId)
    )

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onStart() {
        super.onStart()
        startListeningToVendors()
        val uid = auth.currentUser?.uid ?: return
        startMissionPreview(uid)
        PrizeCard.bind(this, binding.prizeSlot, uid)
        startRewardListeners(uid)
        if (pointsListener == null) startListeningToPoints(uid)
        if (userListener == null) startListeningToUser(uid)
    }

    override fun onResume() {
        super.onResume()
        updateGreeting()
        auth.currentUser?.uid?.let { uid ->
            GamesRepository.fetchStatus(uid) { status ->
                if (isFinishing || isDestroyed) return@fetchStatus
                spinReady = status?.canSpinToday
                bindPlay()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        vendorsListener?.let { com.sapotlokal.data.repository.VendorRepository.removeAllVendorsListener(it) }
        vendorsListener = null
        val uid = auth.currentUser?.uid
        missions?.stop()
        missions = null
        vouchersListener?.let { if (uid != null) com.sapotlokal.data.repository.VoucherRepository.removeVouchersListener(uid, it) }
        vouchersListener = null
        rulesListener?.let { com.sapotlokal.data.repository.VoucherRepository.removeAllRulesListener(it) }
        rulesListener = null
        if (uid == null) return
        pointsListener?.let { PointRepository.removePointsListener(uid, it) }
        pointsListener = null
        userListener?.let {
            FirebaseDatabase.getInstance(AppConfig.DATABASE_URL).reference.child("users").child(uid).removeEventListener(it)
        }
        userListener = null
    }

    override fun onDestroy() {
        super.onDestroy()
        shopNotificationListener?.let { shopNotifQuery?.removeEventListener(it) }
        shopNotificationListener = null
        shopNotifQuery = null
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    private fun checkNotificationPermission(userID: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            SapotFcmService.syncTokenForUser(userID)
        }
    }

    private fun startListeningToShopNotifications() {
        if (shopNotificationListener != null) return
        val query = FirebaseDatabase.getInstance(AppConfig.DATABASE_URL).reference
            .child("shopNotifications").limitToLast(10)
        // Replayed child events arrive before the first value event, so this flag marks the end of
        // the replay without relying on the phone's clock.
        var replayFinished = false
        val initialLoad = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { replayFinished = true }
            override fun onCancelled(error: DatabaseError) { replayFinished = true }
        }
        val listener = object : com.google.firebase.database.ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                if (!replayFinished) return
                val vendorId = snapshot.child("vendorId").getValue(String::class.java)
                    ?: snapshot.child("vendorID").getValue(String::class.java).orEmpty()
                if (vendorId.isBlank()) return
                if (vendorId !in followedVendorIds &&
                    vendorId !in com.sapotlokal.util.FollowedShops.all(this@CustomerHomeActivity)
                ) return
                val isOpen = snapshot.child("isOpen").getValue(Boolean::class.java) ?: true
                val vendorName = snapshot.child("vendorName").getValue(String::class.java).orEmpty()
                    .ifBlank { "A shop you follow" }
                SapotFcmService.showShopStatusNotification(this@CustomerHomeActivity, vendorId, vendorName, isOpen)
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }
        query.addChildEventListener(listener)
        query.addListenerForSingleValueEvent(initialLoad)
        shopNotifQuery = query
        shopNotificationListener = listener
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val HOME_SHOP_CARDS = 6
        const val HOME_CARD_WIDTH_DP = 264
    }
}
