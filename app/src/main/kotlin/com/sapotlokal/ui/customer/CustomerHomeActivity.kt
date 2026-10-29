package com.sapotlokal.ui.customer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ValueEventListener
import com.sapotlokal.AppConfig
import com.sapotlokal.R
import com.sapotlokal.data.model.Vendor
import com.sapotlokal.data.repository.ChallengeRepository
import com.sapotlokal.data.repository.PointRepository
import com.sapotlokal.data.repository.VendorRepository
import com.sapotlokal.databinding.ActivityCustomerHomeBinding
import com.sapotlokal.service.SapotFcmService
import com.sapotlokal.ui.auth.LoginActivity
import com.sapotlokal.ui.games.GamesMenuActivity

/**
 * CustomerHomeActivity — Phase 3 real implementation adapted for the new SapotLokal UI.
 *
 * WHAT IT DOES:
 * 1. Identifies the logged-in customer from FirebaseAuth.
 * 2. Attaches a live listener to /points filtered by userID.
 * 3. Computes lifetimeEarned → level + progress bar fill via AppConfig.
 * 4. Navigates to FindShopsActivity, ScanActivity, GamesMenuActivity, ChallengesActivity, or MyVouchersActivity.
 * 5. Handles bottom navigation and profile/logout menu.
 * 6. Syncs FCM registration token & prompts for push notification permissions (Phase 6).
 */
class CustomerHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomerHomeBinding
    private var pointsListener: ValueEventListener? = null
    private val auth = FirebaseAuth.getInstance()

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            auth.currentUser?.uid?.let { SapotFcmService.syncTokenForUser(it) }
        }
    }

    /** Adapter for the "Around your corner" recently-visited shops list (Phase 3 Task 3.2). */
    private val recentShopAdapter = RecentShopAdapter()

    // Level names matching AppConfig.LEVEL_THRESHOLDS
    private val levelNames = listOf(
        "Pendatang Baru",      // Level 1
        "Tetamu Setia",        // Level 2
        "Pelanggan Emas",      // Level 3
        "Sahabat Lokal",       // Level 4
        "Juara SapotLokal"     // Level 5
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomerHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val user = auth.currentUser
        if (user == null) {
            // Defensive: if session expired, go back to login
            goToLogin()
            return
        }

        // Set up the recent shops RecyclerView (Phase 3 Task 3.2)
        binding.rvRecentShops.layoutManager = LinearLayoutManager(this)
        binding.rvRecentShops.adapter = recentShopAdapter

        // Show temporary greeting while fetching real name from DB
        binding.tvGreeting.text = "Morning, there ☀"
        loadCustomerName(user.uid)
        startListeningToPoints(user.uid)

        // Quick Actions & Navigation
        binding.btnScan.setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java))
        }
        binding.btnGames.setOnClickListener {
            startActivity(Intent(this, GamesMenuActivity::class.java))
        }
        binding.btnChallenges.setOnClickListener {
            startActivity(Intent(this, ChallengesActivity::class.java))
        }
        binding.btnMyVouchers.setOnClickListener {
            startActivity(Intent(this, MyVouchersActivity::class.java))
        }
        binding.btnExplore.setOnClickListener {
            startActivity(Intent(this, FindShopsActivity::class.java))
        }

        // Notification permission & FCM token synchronization (Phase 6)
        checkNotificationPermission(user.uid)
        ChallengeRepository.syncUserProgress(user.uid)

        // Bottom Navigation
        binding.bottomNav.tabHome.setOnClickListener {
            // Already on Home
        }
        binding.bottomNav.tabDiscover.setOnClickListener {
            startActivity(Intent(this, FindShopsActivity::class.java))
        }
        binding.bottomNav.tabMyShops.setOnClickListener {
            startActivity(Intent(this, MyVouchersActivity::class.java))
        }
        binding.cardRewardNudge.setOnClickListener {
            startActivity(Intent(this, MyVouchersActivity::class.java))
        }
        binding.bottomNav.tabProfile.setOnClickListener { view ->
            showProfileMenu(view)
        }

        // Top notification bell options
        binding.btnNotification.setOnClickListener { view ->
            showProfileMenu(view)
        }
    }

    private fun showProfileMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_customer_home, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_logout -> {
                    auth.signOut()
                    goToLogin()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    // ── Points listener ───────────────────────────────────────────────────────

    private fun loadCustomerName(uid: String) {
        com.google.firebase.database.FirebaseDatabase
            .getInstance(AppConfig.DATABASE_URL)
            .reference
            .child("users").child(uid).child("userName")
            .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val name = snapshot.getValue(String::class.java)?.trim() ?: ""
                    val firstName = name.split(" ").firstOrNull { it.isNotBlank() } ?: "there"
                    binding.tvGreeting.text = "Morning, $firstName ☀"
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    // Leave the placeholder; not a fatal error
                }
            })
    }

    private fun startListeningToPoints(userID: String) {
        pointsListener = PointRepository.listenToPointsForUser(
            userID = userID,
            onUpdate = { perShopBalance, lifetimeEarned ->
                updateUi(lifetimeEarned)
                loadRecentShops(perShopBalance)
            },
            onError  = { /* keep showing last values; network error handled in ScanActivity */ }
        )
    }

    /**
     * Given the per-shop balance map (vendorID → balance), fetches each vendor's
     * name and category from /vendors and submits a sorted list to the adapter.
     *
     * Vendors with a zero-or-negative balance are excluded (shouldn't happen in normal
     * flow, but guards against stale data).
     *
     * The list is sorted descending by balance so the shop with the most points
     * appears first — motivating the customer to keep earning.
     */
    private fun loadRecentShops(perShopBalance: Map<String, Int>) {
        val visitedVendorIDs = perShopBalance.keys.filter { it.isNotBlank() && (perShopBalance[it] ?: 0) > 0 }

        if (visitedVendorIDs.isEmpty()) {
            recentShopAdapter.submitList(emptyList())
            binding.tvNoShops.visibility    = View.VISIBLE
            binding.rvRecentShops.visibility = View.GONE
            return
        }

        // Accumulate resolved items; submit once all lookups complete.
        val resolved = mutableListOf<RecentShopItem>()
        var remaining = visitedVendorIDs.size

        for (vendorID in visitedVendorIDs) {
            VendorRepository.fetchVendorById(
                vendorID = vendorID,
                onResult = { vendor ->
                    val balance = perShopBalance[vendorID] ?: 0
                    if (vendor != null && balance > 0) {
                        synchronized(resolved) {
                            resolved.add(
                                RecentShopItem(
                                    vendorID  = vendorID,
                                    shopName  = vendor.vendorName.ifBlank { "Unknown Shop" },
                                    category  = vendor.vendorCategory.ifBlank { "Shop" },
                                    balance   = balance
                                )
                            )
                        }
                    }
                    synchronized(resolved) {
                        remaining--
                        if (remaining == 0) {
                            // All lookups done — sort and submit on the main thread
                            val sorted = resolved.sortedByDescending { it.balance }
                            runOnUiThread {
                                recentShopAdapter.submitList(sorted)
                                binding.tvNoShops.visibility     = View.GONE
                                binding.rvRecentShops.visibility = View.VISIBLE
                            }
                        }
                    }
                },
                onError = { _ ->
                    synchronized(resolved) {
                        remaining--
                        if (remaining == 0) {
                            val sorted = resolved.sortedByDescending { it.balance }
                            runOnUiThread {
                                recentShopAdapter.submitList(sorted)
                                binding.tvNoShops.visibility     = if (sorted.isEmpty()) View.VISIBLE else View.GONE
                                binding.rvRecentShops.visibility = if (sorted.isEmpty()) View.GONE else View.VISIBLE
                            }
                        }
                    }
                }
            )
        }
    }

    private fun updateUi(lifetimeEarned: Int) {
        val level     = AppConfig.levelForPoints(lifetimeEarned)
        val levelName = levelNames.getOrElse(level - 1) { "Juara SapotLokal" }
        val nextThreshold = AppConfig.nextLevelThreshold(lifetimeEarned)

        binding.tvLifetimePoints.text = lifetimeEarned.toString()
        binding.chipLevel.text        = "Lv $level · $levelName"

        if (nextThreshold == null) {
            // Maximum level reached
            binding.progressLevel.progress = 100
            binding.tvProgressLabel.text   = "Maximum level reached!"
            binding.tvProgressCount.text   = ""
            binding.tvMaxLevel.visibility  = View.VISIBLE
        } else {
            // Find previous threshold to compute progress within this level
            val prevThreshold = AppConfig.LEVEL_THRESHOLDS.getOrElse(level - 1) { 0 }
            val rangeSize     = nextThreshold - prevThreshold
            val earned        = lifetimeEarned - prevThreshold
            val pct           = if (rangeSize > 0) (earned * 100 / rangeSize) else 0

            binding.progressLevel.progress = pct
            binding.tvProgressLabel.text   = "Progress to Level ${level + 1}"
            binding.tvProgressCount.text   = "$lifetimeEarned / $nextThreshold pts"
            binding.tvMaxLevel.visibility  = View.GONE
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onStop() {
        super.onStop()
        // Detach live listener to avoid memory leaks and unnecessary reads
        val uid = auth.currentUser?.uid ?: return
        pointsListener?.let { PointRepository.removePointsListener(uid, it) }
        pointsListener = null
    }

    override fun onStart() {
        super.onStart()
        // Re-attach listener if we're returning from ScanActivity or Games
        val uid = auth.currentUser?.uid ?: return
        if (pointsListener == null) {
            startListeningToPoints(uid)
        }
        ChallengeRepository.syncUserProgress(uid)
    }

    private fun checkNotificationPermission(userID: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!isGranted) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                SapotFcmService.syncTokenForUser(userID)
            }
        } else {
            SapotFcmService.syncTokenForUser(userID)
        }
    }

    private fun goToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }
}
