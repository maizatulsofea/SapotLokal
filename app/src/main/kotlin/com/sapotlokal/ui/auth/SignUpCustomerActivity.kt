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
import com.sapotlokal.databinding.ActivitySignupCustomerBinding
import com.sapotlokal.ui.customer.CustomerHomeActivity

/**
 * SignUpCustomerActivity — creates a Customer account.
 *
 * WHAT HAPPENS ON SIGN-UP:
 * 1. Validate all fields locally (name, valid email, 6+ char password).
 * 2. Call Firebase Auth to create the account (this handles password hashing —
 *    we NEVER store the password ourselves, per build guide Rule 5 / Section 3.4).
 * 3. On success, write a USER record to /users/{uid} with role = "customer".
 * 4. Navigate to CustomerHomeActivity and clear the back stack.
 *
 * WHY role IS IN THE DATABASE (not just in Auth claims):
 * Firebase Auth custom claims require a server SDK to set. By writing role into
 * Realtime Database we can read it on the client AND protect it with Security Rules
 * (`role` uses `!data.exists()` — once written, no client can overwrite it).
 *
 * ERROR HANDLING:
 * - FirebaseAuthUserCollisionException → email already registered
 * - Any other exception → generic error shown to user
 * All validation errors are shown as TextInputLayout error messages directly
 * under the offending field — no toast spam.
 */
class SignUpCustomerActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupCustomerBinding
    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseDatabase.getInstance(com.sapotlokal.AppConfig.DATABASE_URL).reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupCustomerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Up/back arrow behaviour — go back to RolePickerActivity
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.btnSignUp.setOnClickListener { attemptSignUp() }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    // ── Validation ─────────────────────────────────────────────────────────────

    private fun attemptSignUp() {
        // Clear previous errors
        binding.tilName.error     = null
        binding.tilEmail.error    = null
        binding.tilPassword.error = null

        val name     = binding.etName.text.toString().trim()
        val email    = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()

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

        if (!valid) return

        setLoading(true)
        createFirebaseAccount(name, email, password)
    }

    // ── Firebase Auth + Database write ─────────────────────────────────────────

    private fun createFirebaseAccount(name: String, email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user!!.uid
                writeUserRecord(uid, name, email)
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
     * Writes the USER record to /users/{uid}.
     *
     * Security rule note: `role` is protected by `!data.exists()` in the Security Rules —
     * so once this write succeeds the role is permanently locked to "customer".
     * No client (including the app itself) can change it later.
     */
    private fun writeUserRecord(uid: String, name: String, email: String) {
        val user = User(
            userID    = uid,
            userName  = name,
            userEmail = email,
            role      = "customer"
        )
        db.child("users").child(uid).setValue(user)
            .addOnSuccessListener {
                // Account + database record both written successfully
                navigateToHome()
            }
            .addOnFailureListener {
                // Auth account was created but the DB write failed.
                // The user CAN still log in later — the role will be missing, and
                // SplashActivity will fall through to LoginActivity. For now show a
                // generic error and let them try logging in.
                setLoading(false)
                binding.tilEmail.error = getString(com.sapotlokal.R.string.error_generic)
            }
    }

    private fun navigateToHome() {
        setLoading(false)
        val intent = Intent(this, CustomerHomeActivity::class.java)
        // Clear the auth stack so the user can't press Back to reach sign-up
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    // ── UI helpers ─────────────────────────────────────────────────────────────

    private fun setLoading(loading: Boolean) {
        binding.btnSignUp.isEnabled  = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }
}
