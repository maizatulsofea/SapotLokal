package com.sapotlokal.ui.customer

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ValueEventListener
import com.sapotlokal.AppConfig
import com.sapotlokal.R
import com.sapotlokal.data.repository.PointRepository
import com.sapotlokal.databinding.ActivityCustomerHomeBinding
import com.sapotlokal.ui.auth.LoginActivity
import com.sapotlokal.ui.games.GamesMenuActivity

/**
 * CustomerHomeActivity — Phase 3 real implementation.
 *
 * WHAT IT DOES:
 * 1. Identifies the logged-in customer from FirebaseAuth.
 * 2. Attaches a live listener to /points filtered by userID.
 * 3. Computes lifetimeEarned → level + progress bar fill via AppConfig.
 * 4. Navigates to FindShopsActivity, ScanActivity, or MyVouchersActivity (Phase 5).
 *
 * WHY LIVE LISTENER: The points balance changes as soon as the customer scans a QR
 * (in ScanActivity). Using addValueEventListener means the Home Screen updates
 * automatically when they navigate back — no manual refresh needed.
 *
 * WHY LIFETIME NOT BALANCE FOR LEVEL: Build guide Section 3.3 and Open Decision #12 —
 * level is based on earned points only so it never goes down when a voucher is redeemed.
 */
class CustomerHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomerHomeBinding
    private var pointsListener: ValueEventListener? = null
    private val auth = FirebaseAuth.getInstance()

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

        setSupportActionBar(binding.toolbar)

        val user = auth.currentUser
        if (user == null) {
            // Defensive: if session expired, go back to login
            goToLogin()
            return
        }

        // Show greeting while points load
        binding.tvGreeting.text = "Welcome back!"
        startListeningToPoints(user.uid)

        // Navigation buttons
        binding.btnFindShops.setOnClickListener {
            startActivity(Intent(this, FindShopsActivity::class.java))
        }
        binding.btnScan.setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java))
        }
        binding.btnMyVouchers.setOnClickListener {
            // Phase 5 will create MyVouchersActivity; stub toast for now
            android.widget.Toast.makeText(
                this, "My Vouchers — coming in Phase 5", android.widget.Toast.LENGTH_SHORT
            ).show()
        }
        binding.btnGames.setOnClickListener {
            startActivity(Intent(this, GamesMenuActivity::class.java))
        }
    }

    // ── Points listener ───────────────────────────────────────────────────────

    private fun startListeningToPoints(userID: String) {
        pointsListener = PointRepository.listenToPointsForUser(
            userID = userID,
            onUpdate = { _, lifetimeEarned -> updateUi(lifetimeEarned) },
            onError  = { /* keep showing last values; network error handled in ScanActivity */ }
        )
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
            binding.tvMaxLevel.visibility  = android.view.View.VISIBLE
        } else {
            // Find previous threshold to compute progress within this level
            val prevThreshold = AppConfig.LEVEL_THRESHOLDS.getOrElse(level - 1) { 0 }
            val rangeSize     = nextThreshold - prevThreshold
            val earned        = lifetimeEarned - prevThreshold
            val pct           = if (rangeSize > 0) (earned * 100 / rangeSize) else 0

            binding.progressLevel.progress = pct
            binding.tvProgressLabel.text   = "Progress to Level ${level + 1}"
            binding.tvProgressCount.text   = "$lifetimeEarned / $nextThreshold pts"
            binding.tvMaxLevel.visibility  = android.view.View.GONE
        }
    }

    // ── Toolbar menu ──────────────────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_customer_home, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                auth.signOut()
                goToLogin()
                true
            }
            else -> super.onOptionsItemSelected(item)
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
        // Re-attach listener if we're returning from ScanActivity
        val uid = auth.currentUser?.uid ?: return
        if (pointsListener == null) {
            startListeningToPoints(uid)
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
