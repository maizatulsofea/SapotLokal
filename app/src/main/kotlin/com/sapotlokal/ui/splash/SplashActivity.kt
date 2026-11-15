package com.sapotlokal.ui.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.sapotlokal.AppConfig
import com.sapotlokal.databinding.ActivitySplashBinding
import com.sapotlokal.ui.auth.LoginActivity
import com.sapotlokal.ui.auth.RoleRouter

/**
 * Launch screen: the logo on white while the app decides where to go.
 * No session → login. A session → the home for the role stored in the database
 * (read live, never cached on the phone, so a modified app can't claim another role).
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val startedAt = System.currentTimeMillis()
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            after(startedAt) { openLogin() }
            return
        }
        FirebaseDatabase.getInstance(AppConfig.DATABASE_URL).reference
            .child("users").child(user.uid).child("role").get()
            .addOnSuccessListener { snap ->
                val home = RoleRouter.homeFor(snap.getValue(String::class.java))
                after(startedAt) {
                    if (home != null) { RoleRouter.open(this, home); finish() } else openLogin()
                }
            }
            .addOnFailureListener { after(startedAt) { openLogin() } }
    }

    /** Keeps the logo up for at least [MIN_SHOW_MS] so the screen doesn't flash. */
    private fun after(startedAt: Long, action: () -> Unit) {
        val wait = (MIN_SHOW_MS - (System.currentTimeMillis() - startedAt)).coerceAtLeast(0)
        Handler(Looper.getMainLooper()).postDelayed({ if (!isFinishing) action() }, wait)
    }

    private fun openLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private companion object {
        const val MIN_SHOW_MS = 600L
    }
}
