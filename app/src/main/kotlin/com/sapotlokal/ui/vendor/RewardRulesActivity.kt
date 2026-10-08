package com.sapotlokal.ui.vendor

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.database.ValueEventListener
import com.sapotlokal.data.model.Voucher
import com.sapotlokal.data.repository.VoucherRepository
import com.sapotlokal.databinding.ActivityRewardRulesBinding

/**
 * RewardRulesActivity — lists the shop's reward rules; the vendor can add, edit, or toggle.
 *
 * WHAT IT DOES:
 * - Attaches a live listener to /vouchers filtered by vendorID.
 * - Shows all rules (active and inactive) in a RecyclerView.
 * - Empty state is shown when the list is blank.
 * - FAB → AddEditRuleActivity (new rule).
 * - Edit icon on each row → AddEditRuleActivity (edit mode).
 * - Switch on each row → toggle isActive via VoucherRepository.setRuleActive().
 *
 * WHY LIVE LISTENER: If the vendor edits a rule in AddEditRuleActivity and comes back,
 * the list refreshes automatically — no need to reload.
 *
 * TURNING A RULE OFF (build guide Open Decision #11):
 * Setting isActive=false stops new VOUCHER_USER records from being created for that rule.
 * Any customer vouchers already issued from it stay valid and can still be redeemed.
 */
class RewardRulesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRewardRulesBinding
    private lateinit var adapter: RewardRuleAdapter

    private var vendorID: String = ""
    private var rulesListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRewardRulesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        vendorID = intent.getStringExtra(VendorHomeActivity.EXTRA_VENDOR_ID) ?: ""
        if (vendorID.isEmpty()) { finish(); return }

        setupRecyclerView()

        binding.fabAddRule.setOnClickListener {
            startActivity(
                Intent(this, AddEditRuleActivity::class.java)
                    .putExtra(VendorHomeActivity.EXTRA_VENDOR_ID, vendorID)
            )
        }

        startListening()
    }

    private fun setupRecyclerView() {
        adapter = RewardRuleAdapter(
            onEditClick = { rule ->
                startActivity(
                    Intent(this, AddEditRuleActivity::class.java)
                        .putExtra(VendorHomeActivity.EXTRA_VENDOR_ID, vendorID)
                        .putExtra(AddEditRuleActivity.EXTRA_RULE_ID,   rule.voucherID)
                        .putExtra(AddEditRuleActivity.EXTRA_RULE_NAME, rule.voucherName)
                        .putExtra(AddEditRuleActivity.EXTRA_RULE_DESC, rule.voucherDescription)
                        .putExtra(AddEditRuleActivity.EXTRA_RULE_PTS,  rule.pointsRequired)
                )
            },
            onToggleActive = { rule, isActive ->
                VoucherRepository.setRuleActive(
                    voucherID = rule.voucherID,
                    isActive  = isActive,
                    onResult  = { success ->
                        if (!success) {
                            Toast.makeText(
                                this,
                                "Could not update rule. Please try again.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
        )
        binding.rvRewardRules.layoutManager = LinearLayoutManager(this)
        binding.rvRewardRules.adapter = adapter
    }

    private fun startListening() {
        rulesListener = VoucherRepository.listenToRulesForVendor(
            vendorID = vendorID,
            onUpdate = { rules ->
                adapter.submitList(rules)
                val isEmpty = rules.isEmpty()
                binding.layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
                binding.rvRewardRules.visibility = if (isEmpty) View.GONE else View.VISIBLE
            },
            onError = { err ->
                Toast.makeText(this, "Error loading rules: $err", Toast.LENGTH_SHORT).show()
            }
        )
    }

    override fun onStop() {
        super.onStop()
        rulesListener?.let { VoucherRepository.removeRulesListener(vendorID, it) }
        rulesListener = null
    }

    override fun onStart() {
        super.onStart()
        if (rulesListener == null) startListening()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
