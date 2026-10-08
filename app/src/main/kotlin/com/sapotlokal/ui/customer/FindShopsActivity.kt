package com.sapotlokal.ui.customer

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.sapotlokal.databinding.ActivityFindShopsBinding
import com.sapotlokal.data.repository.PointRepository
import com.sapotlokal.data.repository.VendorRepository

/**
 * FindShopsActivity — Phase 3 shop list.
 *
 * WHAT IT DOES:
 *   1. Fetches all vendors from /vendors (one-shot, then shows the list).
 *   2. Fetches the customer's per-shop balances from /points.
 *   3. Merges them and hands the result to ShopAdapter.
 *
 * WHY TWO FETCHES: Vendors and Points live under different nodes in the database.
 * We need both to show points per shop. We fetch vendors first, then points,
 * so the list can show "0 pts" immediately if points haven't loaded yet.
 *
 * This is a one-shot fetch (not a live listener) because Find Shops is a list
 * the customer browses; real-time updates are not critical here. ScanActivity
 * will trigger a fresh open of this screen anyway if the user navigates back.
 */
class FindShopsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFindShopsBinding
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFindShopsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.rvShops.layoutManager = LinearLayoutManager(this)

        loadShops()
    }

    private fun loadShops() {
        binding.progressBar.visibility = View.VISIBLE
        binding.rvShops.visibility     = View.GONE
        binding.tvEmpty.visibility     = View.GONE

        val userID = auth.currentUser?.uid ?: return

        // Step 1: fetch all vendors
        VendorRepository.fetchAllVendors(
            onResult = { vendors ->
                if (vendors.isEmpty()) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvEmpty.visibility     = View.VISIBLE
                    return@fetchAllVendors
                }

                // Step 2: fetch this customer's per-shop balances
                PointRepository.fetchAllPointsForUser(
                    userID   = userID,
                    onResult = { perShopBalance, _ ->
                        val adapter = ShopAdapter(perShopBalance)
                        binding.rvShops.adapter = adapter
                        adapter.submitList(vendors)

                        binding.progressBar.visibility = View.GONE
                        binding.rvShops.visibility     = View.VISIBLE
                    },
                    onError = { _ ->
                        // Show shops even if points fail to load — they'll show 0
                        val adapter = ShopAdapter(emptyMap())
                        binding.rvShops.adapter = adapter
                        adapter.submitList(vendors)

                        binding.progressBar.visibility = View.GONE
                        binding.rvShops.visibility     = View.VISIBLE
                    }
                )
            },
            onError = { errorMsg ->
                binding.progressBar.visibility = View.GONE
                binding.tvEmpty.text           = "Could not load shops. Please check your connection."
                binding.tvEmpty.visibility     = View.VISIBLE
            }
        )
    }
}
