package com.sapotlokal.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.Html
import androidx.appcompat.app.AppCompatActivity
import com.sapotlokal.databinding.ActivityRolePickerBinding

/**
 * RolePickerActivity — the first screen a brand-new user sees.
 *
 * WHY IT EXISTS:
 * The build guide says the role is chosen ONCE at sign-up and stored on the account;
 * login never asks again. So this screen only appears when a user doesn't have a session
 * (routed from SplashActivity → LoginActivity → RolePickerActivity via "Sign Up" link).
 *
 * WHAT IT DOES:
 * Shows two cards: Customer and Shop Owner. Tapping a card opens the matching
 * sign-up activity with the role pre-decided. There is no way to go back and pick
 * the other role after the account is created — role is locked by Security Rules.
 *
 * WHY ROLE CAN'T CHANGE AFTER SIGN-UP:
 * The Firebase Security Rules write `role` under a condition of `!data.exists()`.
 * Once the field has a value in the database, no client can overwrite it — not even
 * the account owner. This prevents a customer from self-promoting to a vendor account.
 */
class RolePickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRolePickerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRolePickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Customer card → Customer sign-up form
        binding.cardCustomer.setOnClickListener {
            startActivity(Intent(this, SignUpCustomerActivity::class.java))
        }

        // Shop Owner card → Vendor sign-up form
        binding.cardVendor.setOnClickListener {
            startActivity(Intent(this, SignUpVendorActivity::class.java))
        }

        // "Already have an account? Log In" — go back to LoginActivity
        binding.tvGoToLogin.setOnClickListener {
            // LoginActivity is the parent on the back stack; finish brings us back there.
            finish()
        }
    }
}
