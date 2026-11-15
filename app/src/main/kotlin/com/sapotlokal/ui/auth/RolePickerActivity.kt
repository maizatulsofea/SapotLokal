package com.sapotlokal.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sapotlokal.databinding.ActivityRolePickerBinding

/**
 * Sign up, step 1: "I'm a customer" or "I own a shop".
 * The role is written once at sign-up and the security rules stop it from changing later,
 * so this is the only place a role is ever chosen. Login never asks.
 */
class RolePickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRolePickerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRolePickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.cardCustomer.setOnClickListener {
            startActivity(Intent(this, SignUpCustomerActivity::class.java))
        }
        binding.cardVendor.setOnClickListener {
            startActivity(Intent(this, SignUpVendorActivity::class.java))
        }
    }
}
