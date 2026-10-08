package com.sapotlokal.ui.vendor

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sapotlokal.data.repository.VoucherRepository
import com.sapotlokal.databinding.ActivityAddEditRuleBinding

/**
 * AddEditRuleActivity — form to add a new reward rule or edit an existing one.
 *
 * MODE DETECTION:
 *   If EXTRA_RULE_ID is present in the Intent, we're in EDIT mode — the toolbar
 *   title changes to "Edit Reward Rule" and Save calls VoucherRepository.updateRule().
 *   Otherwise, we're in ADD mode and Save calls VoucherRepository.createRule().
 *
 * VALIDATION:
 *   - Name: required, non-blank
 *   - Description: optional (but the field is shown so we allow empty)
 *   - Points: required, must be a positive integer
 *
 * WHY NO DELETE:
 *   The build guide says rules can be "turned off" (isActive=false), not deleted.
 *   Deleting a rule whose voucherID is referenced by existing VOUCHER_USER records
 *   would orphan those records. Turning it off is safe — already-issued customer
 *   vouchers keep working. If a delete feature is ever needed, it must first check
 *   that no active VOUCHER_USER records reference this rule (future work).
 */
class AddEditRuleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditRuleBinding

    private var vendorID: String = ""
    private var ruleID:   String = ""   // Empty in ADD mode

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditRuleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        vendorID = intent.getStringExtra(VendorHomeActivity.EXTRA_VENDOR_ID) ?: ""
        ruleID   = intent.getStringExtra(EXTRA_RULE_ID) ?: ""

        if (ruleID.isNotEmpty()) {
            // EDIT mode — pre-fill the form
            supportActionBar?.title = "Edit Reward Rule"
            binding.etRuleName.setText(intent.getStringExtra(EXTRA_RULE_NAME) ?: "")
            binding.etRuleDesc.setText(intent.getStringExtra(EXTRA_RULE_DESC) ?: "")
            val pts = intent.getIntExtra(EXTRA_RULE_PTS, 0)
            binding.etPoints.setText(if (pts > 0) pts.toString() else "")
        } else {
            supportActionBar?.title = "Add Reward Rule"
        }

        binding.btnSaveRule.setOnClickListener { onSave() }
    }

    private fun onSave() {
        val name = binding.etRuleName.text?.toString()?.trim() ?: ""
        val desc = binding.etRuleDesc.text?.toString()?.trim() ?: ""
        val ptsStr = binding.etPoints.text?.toString()?.trim() ?: ""

        // Input validation
        if (name.isEmpty()) {
            binding.tilRuleName.error = "Reward name is required"
            return
        }
        binding.tilRuleName.error = null

        val points = ptsStr.toIntOrNull()
        if (points == null || points <= 0) {
            binding.tilPoints.error = "Enter a valid point amount (e.g. 100)"
            return
        }
        binding.tilPoints.error = null

        binding.btnSaveRule.isEnabled = false

        if (ruleID.isEmpty()) {
            // ADD mode
            VoucherRepository.createRule(
                vendorID          = vendorID,
                voucherName       = name,
                voucherDescription = desc,
                pointsRequired    = points,
                onResult = { success -> handleResult(success) }
            )
        } else {
            // EDIT mode
            VoucherRepository.updateRule(
                voucherID          = ruleID,
                voucherName        = name,
                voucherDescription = desc,
                pointsRequired     = points,
                onResult = { success -> handleResult(success) }
            )
        }
    }

    private fun handleResult(success: Boolean) {
        runOnUiThread {
            binding.btnSaveRule.isEnabled = true
            if (success) {
                finish()  // Go back to RewardRulesActivity, live listener will update the list
            } else {
                Toast.makeText(this, "Could not save rule. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    companion object {
        const val EXTRA_RULE_ID   = "extra_rule_id"
        const val EXTRA_RULE_NAME = "extra_rule_name"
        const val EXTRA_RULE_DESC = "extra_rule_desc"
        const val EXTRA_RULE_PTS  = "extra_rule_pts"
    }
}
