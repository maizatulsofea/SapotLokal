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
import com.sapotlokal.AppConfig
import com.sapotlokal.R
import com.sapotlokal.databinding.ActivityLoginBinding
import com.sapotlokal.util.AppLanguage
import com.sapotlokal.util.PasswordResetHelper

/**
 * The one login screen for customers and shop owners (REFRACTOR M1).
 * After sign-in, the account's role from the database picks the home screen.
 * "New here? Create an account" opens the role picker; the role is only asked at sign-up.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance(AppConfig.DATABASE_URL).reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AppLanguage.bindToggle(binding.languageToggle.root)

        binding.btnLogin.setOnClickListener { attemptLogin() }
        binding.etPassword.setOnEditorActionListener { _, _, _ -> attemptLogin(); true }
        binding.btnForgotPassword.setOnClickListener {
            PasswordResetHelper.show(this, binding.etEmail.text.toString().trim())
        }
        binding.btnCreateAccount.setOnClickListener {
            startActivity(Intent(this, RolePickerActivity::class.java))
        }
    }

    private fun attemptLogin() {
        binding.tilEmail.error = null
        binding.tilPassword.error = null

        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()

        when {
            email.isEmpty() -> binding.tilEmail.error = getString(R.string.error_email_empty)
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> binding.tilEmail.error = getString(R.string.error_email_invalid)
        }
        if (password.isEmpty()) binding.tilPassword.error = getString(R.string.error_password_empty)
        if (binding.tilEmail.error != null || binding.tilPassword.error != null) return

        setLoading(true)
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result -> routeByRole(result.user!!.uid) }
            .addOnFailureListener { e ->
                setLoading(false)
                when (e) {
                    is FirebaseAuthInvalidUserException -> binding.tilEmail.error = getString(R.string.error_user_not_found)
                    is FirebaseAuthInvalidCredentialsException -> binding.tilPassword.error = getString(R.string.error_wrong_password)
                    else -> binding.tilEmail.error = getString(R.string.error_generic)
                }
            }
    }

    /** Reads the role from the live database, never from the phone, then opens that home. */
    private fun routeByRole(uid: String) {
        db.child("users").child(uid).child("role").get()
            .addOnSuccessListener { snap ->
                val home = RoleRouter.homeFor(snap.getValue(String::class.java))
                if (home != null) {
                    RoleRouter.open(this, home)
                } else {
                    // An Auth account without a profile (e.g. a sign-up that failed half way).
                    auth.signOut()
                    setLoading(false)
                    binding.tilEmail.error = getString(R.string.auth_no_role)
                }
            }
            .addOnFailureListener {
                auth.signOut()
                setLoading(false)
                binding.tilEmail.error = getString(R.string.error_no_internet)
            }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnLogin.isEnabled = !loading
        binding.btnLogin.text = if (loading) "" else getString(R.string.auth_log_in)
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }
}
