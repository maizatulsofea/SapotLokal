package com.sapotlokal.ui.games

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.sapotlokal.AppConfig
import com.sapotlokal.R
import com.sapotlokal.data.repository.GamesRepository
import com.sapotlokal.databinding.ActivityGamesMenuBinding

class GamesMenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGamesMenuBinding
    private lateinit var userID: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGamesMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val currentUID = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Toast.makeText(this, R.string.games_error_not_logged_in, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        userID = currentUID

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.cardSpinWheel.setOnClickListener {
            startActivity(Intent(this, SpinWheelActivity::class.java))
        }

        binding.cardMemoryMatch.setOnClickListener {
            startActivity(Intent(this, MemoryMatchActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        GamesRepository.fetchStatus(userID) { status ->
            if (isFinishing || isDestroyed) return@fetchStatus
            if (status != null) {
                binding.tvSpinSubtitle.text = getString(
                    if (status.canSpinToday) R.string.games_spin_available else R.string.games_spin_used
                )
                binding.tvMemorySubtitle.text = getString(
                    R.string.games_memory_info,
                    status.memoryPointsToday,
                    AppConfig.MEMORY_MATCH_DAILY_CAP_POINTS
                )
            } else {
                binding.tvSpinSubtitle.text = ""
                binding.tvMemorySubtitle.text = ""
            }
        }
    }
}

