package com.sapotlokal.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.database.FirebaseDatabase
import com.sapotlokal.databinding.ActivityLoginBinding
import com.sapotlokal.ui.customer.CustomerHomeActivity
import com.sapotlokal.ui.vendor.VendorHomeActivity

/**
 * LoginActivity — email + password login only; no role choice.
 *
 * WHY NO ROLE CHOICE HERE:
 * Role is chosen ONCE at sign-up and stored in the database. The build guide says
 * "login never asks again". After authenticating, we read the role from
 * /users/{uid}/role in the Realtime Database and route to the correct home screen.
 *
 * ROUTING LOGIC:
 *   role = "customer" → CustomerHomeActivity
 *   role = "vendor"   → VendorHomeActivity
 *   role = unknown    → show error (database may be missing the USER record)
 *
 * WHY WE READ ROLE FROM THE DATABASE (Rule 43):
 * Every check runs against the live database — storing role on device would let
 * a modified app claim a fake role.
 *
 * ERROR HANDLING:
 * - FirebaseAuthInvalidUserException    → no account for this email
 * - FirebaseAuthInvalidCredentialsException → wrong password
 * - Any other exception                 → generic error
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseDatabase.getInstance(com.sapotlokal.AppConfig.DATABASE_URL).reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener { attemptLogin() }

        // "Don't have an account? Sign Up" → RolePickerActivity
        binding.tvGoToSignUp.setOnClickListener {
            startActivity(Intent(this, RolePickerActivity::class.java))
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
     * Reads role from the live database (never from local state) and opens the
     * appropriate home screen.
     */
    private fun fetchRoleAndNavigate(uid: String) {
        db.child("users/$uid/role").get()
            .addOnSuccessListener { snapshot ->
                setLoading(false)
                when (val role = snapshot.getValue(String::class.java)) {
                    "customer" -> navigateTo(CustomerHomeActivity::class.java)
                    "vendor"   -> navigateTo(VendorHomeActivity::class.java)
                    else       -> {
                        // USER record missing or role field corrupt — log out and show error
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
        // Clear back stack — user shouldn't be able to press Back to reach Login while logged in
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    // ── UI helpers ─────────────────────────────────────────────────────────────

    private fun setLoading(loading: Boolean) {
        binding.btnLogin.isEnabled     = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }
}
