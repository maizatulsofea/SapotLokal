package com.sapotlokal.ui.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.sapotlokal.databinding.ActivitySplashBinding
import com.sapotlokal.ui.auth.LoginActivity
import com.sapotlokal.ui.customer.CustomerHomeActivity
import com.sapotlokal.ui.vendor.VendorHomeActivity
import com.google.firebase.database.FirebaseDatabase

/**
 * SplashActivity — the launch point of SapotLokal.
 *
 * WHY IT EXISTS:
 * - Shows the brand logo and tagline for ~1.5 seconds while Firebase determines
 *   whether the user is already logged in.
 * - After the check, routes the user to the correct screen without them needing
 *   to log in again on every open:
 *     • Not logged in  → LoginActivity
 *     • Logged in, role = "customer" → CustomerHomeActivity
 *     • Logged in, role = "vendor"   → VendorHomeActivity
 *
 * WHY WE READ ROLE FROM THE DATABASE (not stored locally):
 * Rule 43 of the build guide says every check must run against the live database.
 * Storing role on the device would let a modified app claim a fake role.
 *
 * NOTE: The 1.5 s minimum delay gives the logo animation time to show even if
 * Firebase responds instantly. Both the timer AND the role check must finish
 * before we navigate — whichever takes longer wins.
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val auth = FirebaseAuth.getInstance()

    // Minimum time the splash is visible, in milliseconds
    private val MIN_SPLASH_MS = 1500L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val startTime = System.currentTimeMillis()

        val currentUser = auth.currentUser
        if (currentUser == null) {
            // No session — navigate to Login after the minimum delay
            delayThen(startTime) { goTo(LoginActivity::class.java) }
        } else {
            // Session exists — fetch role from database to pick the right home screen
            val uid = currentUser.uid
            FirebaseDatabase.getInstance(com.sapotlokal.AppConfig.DATABASE_URL)
                .getReference("users/$uid/role")
                .get()
                .addOnSuccessListener { snapshot ->
                    val role = snapshot.getValue(String::class.java)
                    delayThen(startTime) {
                        when (role) {
                            "vendor"   -> goTo(VendorHomeActivity::class.java)
                            "customer" -> goTo(CustomerHomeActivity::class.java)
                            else       -> goTo(LoginActivity::class.java) // unknown role → re-login
                        }
                    }
                }
                .addOnFailureListener {
                    // Database error (e.g. no internet) — send to Login so they can retry
                    delayThen(startTime) { goTo(LoginActivity::class.java) }
                }
        }
    }

    /** Waits until MIN_SPLASH_MS has elapsed since [startTime], then runs [action]. */
    private fun delayThen(startTime: Long, action: () -> Unit) {
        val elapsed = System.currentTimeMillis() - startTime
        val remaining = (MIN_SPLASH_MS - elapsed).coerceAtLeast(0)
        Handler(Looper.getMainLooper()).postDelayed(action, remaining)
    }

    private fun <T : AppCompatActivity> goTo(cls: Class<T>) {
        startActivity(Intent(this, cls))
        finish() // Remove splash from the back stack — pressing Back won't return to it
    }
}
