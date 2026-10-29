package com.sapotlokal.ui.vendor

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.sapotlokal.AppConfig
import com.sapotlokal.data.repository.VendorRepository
import com.sapotlokal.databinding.ActivityVendorHomeBinding
import com.sapotlokal.ui.auth.LoginActivity

/**
 * VendorHomeActivity — Phase 4 real implementation (replaces the Phase 2 placeholder).
 *
 * WHAT IT DOES:
 * 1. Identifies the logged-in vendor via FirebaseAuth uid → VendorRepository.
 * 2. Stores the vendorID as a companion-object value so other activities can use it
 *    without re-querying (within the same session).
 * 3. Navigation: Show QR Code, Reward Rules, Scan to Redeem.
 * 4. Log out clears the session.
 *
 * WHY STORE vendorID HERE: Every vendor screen needs the vendorID. Fetching it once
 * here and passing it via Intent extras avoids repeated database round-trips and
 * keeps the other activities simple.
 */
class VendorHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVendorHomeBinding
    private val auth = FirebaseAuth.getInstance()

    // Holds the vendor record for this session (set after the DB lookup)
    private var vendorID: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVendorHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val user = auth.currentUser
        if (user == null) { goToLogin(); return }

        refreshVendorProfile()
        user.let { com.sapotlokal.service.SapotFcmService.syncTokenForUser(it.uid) }

        binding.btnLogOut.setOnClickListener {
            auth.signOut()
            goToLogin()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshVendorProfile()
    }

    private fun refreshVendorProfile() {
        val user = auth.currentUser ?: return

        // Look up the vendor record for this user
        VendorRepository.fetchVendorByUserId(
            userID   = user.uid,
            onResult = { vendor ->
                if (vendor == null) {
                    binding.tvShopName.text = "Shop details not found"
                    return@fetchVendorByUserId
                }
                vendorID = vendor.vendorID
                binding.tvWelcome.text  = "Welcome back, ${vendor.vendorName}!"
                binding.tvShopName.text = "${vendor.vendorCategory} · ${vendor.vendorAddress}"

                // Navigation — all screens need the vendorID
                binding.btnShowQr.setOnClickListener {
                    startActivity(
                        Intent(this, ShowQrActivity::class.java)
                            .putExtra(EXTRA_VENDOR_ID, vendorID)
                    )
                }
                binding.btnRewardRules.setOnClickListener {
                    startActivity(
                        Intent(this, RewardRulesActivity::class.java)
                            .putExtra(EXTRA_VENDOR_ID, vendorID)
                    )
                }
                binding.btnScanRedeem.setOnClickListener {
                    startActivity(
                        Intent(this, ScanToRedeemActivity::class.java)
                            .putExtra(EXTRA_VENDOR_ID, vendorID)
                    )
                }
                binding.btnStats.setOnClickListener {
                    startActivity(
                        Intent(this, VendorStatsActivity::class.java)
                            .putExtra(EXTRA_VENDOR_ID, vendorID)
                    )
                }
                binding.btnShopSettings.setOnClickListener {
                    startActivity(
                        Intent(this, ShopSettingsActivity::class.java)
                            .putExtra(EXTRA_VENDOR_ID, vendorID)
                    )
                }
            },
            onError = { err ->
                binding.tvShopName.text = "Could not load shop: $err"
            }
        )

        binding.btnLogOut.setOnClickListener {
            auth.signOut()
            goToLogin()
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(com.sapotlokal.R.menu.menu_vendor_home, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            com.sapotlokal.R.id.action_logout -> { auth.signOut(); goToLogin(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun goToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }

    companion object {
        const val EXTRA_VENDOR_ID = "extra_vendor_id"
    }
}
