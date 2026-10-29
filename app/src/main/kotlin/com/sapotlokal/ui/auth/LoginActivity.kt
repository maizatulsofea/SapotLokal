package com.sapotlokal.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.database.FirebaseDatabase
import com.sapotlokal.databinding.ActivityLoginBinding
import com.sapotlokal.ui.customer.CustomerHomeActivity

/**
 * LoginActivity — Default entry point for Customer Login.
 *
 * NAVIGATION:
 *   - "Are you a shop owner? Login as a vendor" -> VendorLoginActivity
 *   - "New here? Register customer account"    -> SignUpCustomerActivity
 *
 * ROLE GUARD:
 *   - role = "customer" -> CustomerHomeActivity
 *   - role = "vendor"   -> Disallow login here; instruct to use Vendor Login.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseDatabase.getInstance(com.sapotlokal.AppConfig.DATABASE_URL).reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Customer login button
        binding.btnLogin.setOnClickListener { attemptLogin() }

        // 1. Redirect to Vendor Login
        binding.tvGoToVendorLogin.setOnClickListener {
            startActivity(Intent(this, VendorLoginActivity::class.java))
        }

        // 2. Direct link to Customer Registration (skipping RolePicker)
        binding.tvRegisterCustomer.setOnClickListener {
            startActivity(Intent(this, SignUpCustomerActivity::class.java))
        }
    }

    // ── Validation ─────────────────────────────────────────────────────────────

    private fun attemptLogin() {
        binding.tilEmail.error    = null
        binding.tilPassword.error = null

        val email    = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()

        var valid = true

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
        }

        if (!valid) return

        setLoading(true)
        signIn(email, password)
    }

    // ── Firebase Auth ──────────────────────────────────────────────────────────

    private fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user!!.uid
                fetchRoleAndNavigate(uid)
            }
            .addOnFailureListener { exception ->
                setLoading(false)
                when (exception) {
                    is FirebaseAuthInvalidUserException ->
                        binding.tilEmail.error = getString(com.sapotlokal.R.string.error_user_not_found)
                    is FirebaseAuthInvalidCredentialsException ->
                        binding.tilPassword.error = getString(com.sapotlokal.R.string.error_wrong_password)
                    else ->
                        binding.tilEmail.error = getString(com.sapotlokal.R.string.error_generic)
                }
            }
    }

    /**
     * Reads role from the live database.
     * Only "customer" is allowed to log in through this screen.
     */
    private fun fetchRoleAndNavigate(uid: String) {
        db.child("users/$uid/role").get()
            .addOnSuccessListener { snapshot ->
                setLoading(false)
                val role = snapshot.getValue(String::class.java)?.lowercase()
                when (role) {
                    "customer" -> navigateTo(CustomerHomeActivity::class.java)
                    "vendor"   -> {
                        // Reject vendor login on customer portal
                        auth.signOut()
                        Toast.makeText(
                            this,
                            "This account is registered as a Vendor. Please login via Vendor Login.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    else -> {
                        // User record missing or role field corrupt
                        auth.signOut()
                        binding.tilEmail.error = getString(com.sapotlokal.R.string.error_generic)
                    }
                }
            }
            .addOnFailureListener {
                setLoading(false)
                auth.signOut()
                binding.tilEmail.error = getString(com.sapotlokal.R.string.error_no_internet)
            }
    }

    private fun <T : AppCompatActivity> navigateTo(cls: Class<T>) {
        val intent = Intent(this, cls)
        // Clear back stack — user cannot press Back to return to Login once logged in
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    // ── UI helpers ─────────────────────────────────────────────────────────────

    private fun setLoading(loading: Boolean) {
        binding.btnLogin.isEnabled     = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }
}