package com.sapotlokal.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.database.FirebaseDatabase
import com.sapotlokal.data.model.User
import com.sapotlokal.data.model.Vendor
import com.sapotlokal.databinding.ActivitySignupVendorBinding
import com.sapotlokal.ui.vendor.VendorHomeActivity

/**
 * SignUpVendorActivity — creates a Shop Owner account.
 *
 * WHAT HAPPENS ON SIGN-UP:
 * 1. Validate all fields locally — SSM field being blank BLOCKS submission (build guide Rule 6).
 * 2. Firebase Auth creates the account.
 * 3. On success, write TWO records atomically to the database:
 *    - /users/{uid}         → USER record (role = "vendor")
 *    - /vendors/{vendorId}  → VENDOR record (linked to the user by userID FK)
 * 4. Navigate to VendorHomeActivity and clear the back stack.
 *
 * WHY TWO RECORDS:
 * The ERD has USER and VENDOR as separate entities. USER holds identity and role.
 * VENDOR holds shop-specific data (name, category, address, SSM number). Phase 4
 * will add QR codes and reward rules under the VENDOR node.
 *
 * SSM VALIDATION:
 * The SSM field is required (cannot be empty). It is stored as plain text — NO
 * external database lookup is ever made. This is deliberately simple; the build
 * guide notes that stronger verification is future work (Open Decision #9).
 *
 * ATOMIC WRITE:
 * Both USER and VENDOR records are written using updateChildren() on the root
 * reference in a single call, so the database either gets both or neither
 * (Firebase multi-location update — atomic for reads, best-effort on failure).
 */
class SignUpVendorActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupVendorBinding
    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseDatabase.getInstance(com.sapotlokal.AppConfig.DATABASE_URL).reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupVendorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.btnSignUp.setOnClickListener { attemptSignUp() }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    // ── Validation ─────────────────────────────────────────────────────────────

    private fun attemptSignUp() {
        // Clear previous field errors
        listOf(
            binding.tilName, binding.tilEmail, binding.tilPassword,
            binding.tilShopName, binding.tilShopCategory,
            binding.tilShopAddress, binding.tilSsmNumber
        ).forEach { it.error = null }

        val name         = binding.etName.text.toString().trim()
        val email        = binding.etEmail.text.toString().trim()
        val password     = binding.etPassword.text.toString()
        val shopName     = binding.etShopName.text.toString().trim()
        val shopCategory = binding.etShopCategory.text.toString().trim()
        val shopAddress  = binding.etShopAddress.text.toString().trim()
        val ssmNumber    = binding.etSsmNumber.text.toString().trim()

        var valid = true

        if (name.isEmpty()) {
            binding.tilName.error = getString(com.sapotlokal.R.string.error_name_empty)
            valid = false
        }
        if (email.isEmpty()) {
            binding.tilEmail.error = getString(com.sapotlokal.R.string.error_email_empty)
            valid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(com.sapotlokal.R.string.error_email_invalid)
            valid = false
        }
        if (password.isEmpty()) {
            binding.tilPassword.error = getString(com.sapotlokal.R.string.error_password_empty)
            valid = false
        } else if (password.length < 6) {
            binding.tilPassword.error = getString(com.sapotlokal.R.string.error_password_short)
            valid = false
        }
        if (shopName.isEmpty()) {
            binding.tilShopName.error = getString(com.sapotlokal.R.string.error_shop_name_empty)
            valid = false
        }
        if (shopCategory.isEmpty()) {
            binding.tilShopCategory.error = getString(com.sapotlokal.R.string.error_shop_category_empty)
            valid = false
        }
        if (shopAddress.isEmpty()) {
            binding.tilShopAddress.error = getString(com.sapotlokal.R.string.error_shop_address_empty)
            valid = false
        }
        // SSM is REQUIRED — blank SSM blocks account creation (build guide Rule 6)
        if (ssmNumber.isEmpty()) {
            binding.tilSsmNumber.error = getString(com.sapotlokal.R.string.error_ssm_empty)
            valid = false
        }

        if (!valid) return

        setLoading(true)
        createFirebaseAccount(name, email, password, shopName, shopCategory, shopAddress, ssmNumber)
    }

    // ── Firebase Auth + Database write ─────────────────────────────────────────

    private fun createFirebaseAccount(
        name: String, email: String, password: String,
        shopName: String, shopCategory: String, shopAddress: String, ssmNumber: String
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user!!.uid
                writeRecords(uid, name, email, shopName, shopCategory, shopAddress, ssmNumber)
            }
            .addOnFailureListener { exception ->
                setLoading(false)
                when (exception) {
                    is FirebaseAuthUserCollisionException ->
                        binding.tilEmail.error = getString(com.sapotlokal.R.string.error_email_in_use)
                    else ->
                        binding.tilEmail.error = getString(com.sapotlokal.R.string.error_generic)
                }
            }
    }

    /**
     * Writes USER then VENDOR as two sequential database calls.
     *
     * Sequential (not atomic) is required because the vendor Security Rule reads
     * /users/{uid}/role from the CURRENT database state. A single root-level
     * updateChildren() evaluates rules against the pre-write state, so the role
     * field would be missing and the vendor write would be denied.
     *
     * vendorID == uid keeps lookups simple; for Phase 4 QR generation we need
     * to find a vendor by the authenticated user's uid quickly.
     */
    private fun writeRecords(
        uid: String, name: String, email: String,
        shopName: String, shopCategory: String, shopAddress: String, ssmNumber: String
    ) {
        val vendorId = uid   // 1-to-1 relationship; use same ID for simplicity

        val user = User(
            userID    = uid,
            userName  = name,
            userEmail = email,
            role      = "vendor"   // Locked by Security Rules after this write
        )
        val vendor = Vendor(
            vendorID       = vendorId,
            userID         = uid,
            vendorName     = shopName,
            vendorCategory = shopCategory,
            vendorAddress  = shopAddress,
            ssmNumber      = ssmNumber
        )

        // WHY TWO SEQUENTIAL WRITES (not updateChildren at root):
        // The vendor Security Rule checks that /users/{uid}/role == 'vendor'.
        // If we write both in one root-level updateChildren(), the rule evaluates
        // the PRE-write state of the database — the user record doesn't exist yet,
        // so role is null and the vendor write is denied. Writing USER first
        // commits the role to the DB, then the VENDOR write sees it and succeeds.
        db.child("users").child(uid).setValue(user)
            .addOnSuccessListener {
                // USER record committed — now safe to write VENDOR
                db.child("vendors").child(vendorId).setValue(vendor)
                    .addOnSuccessListener { navigateToHome() }
                    .addOnFailureListener {
                        setLoading(false)
                        binding.tilEmail.error = getString(com.sapotlokal.R.string.error_generic)
                    }
            }
            .addOnFailureListener {
                setLoading(false)
                binding.tilEmail.error = getString(com.sapotlokal.R.string.error_generic)
            }
    }

    private fun navigateToHome() {
        setLoading(false)
        val intent = Intent(this, VendorHomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    // ── UI helpers ─────────────────────────────────────────────────────────────

    private fun setLoading(loading: Boolean) {
        binding.btnSignUp.isEnabled     = !loading
        binding.progressBar.visibility  = if (loading) View.VISIBLE else View.GONE
    }
}
