package com.sapotlokal.ui.vendor

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.sapotlokal.data.model.VendorStats
import com.sapotlokal.data.repository.VendorRepository
import com.sapotlokal.databinding.ActivityVendorStatsBinding

/**
 * VendorStatsActivity — Phase 6 Task 6.4:
 * Vendor Analytics Dashboard tracking customer footfall, points issued/redeemed,
 * voucher redemptions, and recent ledger activity.
 */
class VendorStatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVendorStatsBinding
    private val auth = FirebaseAuth.getInstance()
    private val adapter = VendorTransactionAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVendorStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.rvTransactions.layoutManager = LinearLayoutManager(this)
        binding.rvTransactions.adapter = adapter

        val vendorID = intent.getStringExtra(VendorHomeActivity.EXTRA_VENDOR_ID) ?: ""
        val user = auth.currentUser

        if (user == null) {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (vendorID.isNotBlank()) {
            loadStats(vendorID)
        } else {
            VendorRepository.fetchVendorByUserId(
                userID = user.uid,
                onResult = { vendor ->
                    if (vendor != null && vendor.vendorID.isNotBlank()) {
                        loadStats(vendor.vendorID)
                    } else {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this, "Could not identify shop record", Toast.LENGTH_SHORT).show()
                    }
                },
                onError = { err ->
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Error: $err", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun loadStats(vendorID: String) {
        binding.progressBar.visibility = View.VISIBLE

        VendorRepository.fetchVendorStats(
            vendorID = vendorID,
            onResult = { stats ->
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    bindStats(stats)
                }
            },
            onError = { err ->
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Failed to load stats: $err", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun bindStats(stats: VendorStats) {
        binding.tvTotalCustomers.text = stats.totalCustomers.toString()
        binding.tvPointsDistributed.text = stats.totalPointsDistributed.toString()
        binding.tvVouchersRedeemed.text = stats.totalVouchersRedeemed.toString()
        binding.tvPointsRedeemed.text = stats.totalPointsRedeemed.toString()

        adapter.submitList(stats.recentPoints)
        val hasTransactions = stats.recentPoints.isNotEmpty()
        binding.tvEmptyTransactions.visibility = if (hasTransactions) View.GONE else View.VISIBLE
        binding.rvTransactions.visibility = if (hasTransactions) View.VISIBLE else View.GONE
    }
}

