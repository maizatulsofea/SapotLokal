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
import com.sapotlokal.databinding.ActivityVendorLoginBinding
import com.sapotlokal.ui.vendor.VendorHomeActivity

/**
 * VendorLoginActivity — Separate login dedicated to Shop Owners / Vendors.
 *
 * NAVIGATION:
 *   - "Not a shop owner? Login as customer" -> Finishes and returns to Customer Login.
 *   - "New here? Register vendor account"   -> Opens SignUpVendorActivity.
 *
 * ROLE GUARD:
 *   - role = "vendor"   -> VendorHomeActivity.
 *   - role = "customer" -> Rejected; told to log in using Customer Login.
 */
class VendorLoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVendorLoginBinding
    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseDatabase.getInstance(com.sapotlokal.AppConfig.DATABASE_URL).reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVendorLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Vendor login button
        binding.btnVendorLogin.setOnClickListener { attemptVendorLogin() }

        // 1. Back to Customer Login
        binding.tvBackToCustomerLogin.setOnClickListener {
            finish() // Returns back to LoginActivity
        }

        // 2. Register Vendor Account (vendor only)
        binding.tvRegisterVendor.setOnClickListener {
            startActivity(Intent(this, SignUpVendorActivity::class.java))
        }
    }

    private fun attemptVendorLogin() {
        binding.tilVendorEmail.error    = null
        binding.tilVendorPassword.error = null

        val email    = binding.etVendorEmail.text.toString().trim()
        val password = binding.etVendorPassword.text.toString()

        var valid = true

        if (email.isEmpty()) {
            binding.tilVendorEmail.error = getString(com.sapotlokal.R.string.error_email_empty)
            valid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilVendorEmail.error = getString(com.sapotlokal.R.string.error_email_invalid)
            valid = false
        }
        if (password.isEmpty()) {
            binding.tilVendorPassword.error = getString(com.sapotlokal.R.string.error_password_empty)
            valid = false
        }

        if (!valid) return

        setLoading(true)
        signInVendor(email, password)
    }

    private fun signInVendor(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user!!.uid
                fetchRoleAndNavigate(uid)
            }
            .addOnFailureListener { exception ->
                setLoading(false)
                when (exception) {
                    is FirebaseAuthInvalidUserException ->
                        binding.tilVendorEmail.error = getString(com.sapotlokal.R.string.error_user_not_found)
                    is FirebaseAuthInvalidCredentialsException ->
                        binding.tilVendorPassword.error = getString(com.sapotlokal.R.string.error_wrong_password)
                    else ->
                        binding.tilVendorEmail.error = getString(com.sapotlokal.R.string.error_generic)
                }
            }
    }

    private fun fetchRoleAndNavigate(uid: String) {
        db.child("users/$uid/role").get()
            .addOnSuccessListener { snapshot ->
                setLoading(false)
                val role = snapshot.getValue(String::class.java)?.lowercase()
                when (role) {
                    "vendor" -> {
                        val intent = Intent(this, VendorHomeActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    }
                    "customer" -> {
                        // Deny customer logging into vendor dashboard
                        auth.signOut()
                        Toast.makeText(
                            this,
                            "This account is registered as a Customer. Please login via Customer Login.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    else -> {
                        auth.signOut()
                        binding.tilVendorEmail.error = getString(com.sapotlokal.R.string.error_generic)
                    }
                }
            }
            .addOnFailureListener {
                setLoading(false)
                auth.signOut()
                binding.tilVendorEmail.error = getString(com.sapotlokal.R.string.error_no_internet)
            }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnVendorLogin.isEnabled = !loading
        binding.progressBar.visibility   = if (loading) View.VISIBLE else View.GONE
    }
}