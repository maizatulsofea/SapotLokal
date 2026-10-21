package com.sapotlokal.ui.games

import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.sapotlokal.AppConfig
import com.sapotlokal.R
import com.sapotlokal.data.repository.GamesRepository
import com.sapotlokal.databinding.ActivitySpinWheelBinding
import com.sapotlokal.util.isNetworkAvailable
import kotlin.random.Random

class SpinWheelActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySpinWheelBinding
    private var isSpinning = false
    private var isBusy = false
    private lateinit var userID: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpinWheelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val currentUID = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Toast.makeText(this, R.string.games_error_not_logged_in, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        userID = currentUID

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.backButton.setOnClickListener { finish() }

        binding.wheel.values = AppConfig.SPIN_WHEEL_SEGMENTS
        binding.spinNowButton.setOnClickListener { onSpinTapped() }

        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        if (!isSpinning) {
            refreshStatus()
        }
    }

    override fun onDestroy() {
        binding.wheel.animate().cancel()
        super.onDestroy()
    }

    private fun setBusy(busy: Boolean, textResId: Int? = null) {
        isBusy = busy
        binding.spinNowButton.isEnabled = !busy
        if (textResId != null) {
            binding.spinNowButton.setText(textResId)
        }
    }

    private fun refreshStatus() {
        setBusy(true, R.string.games_spin_checking)
        GamesRepository.fetchStatus(userID) { status ->
            if (isFinishing || isDestroyed) return@fetchStatus
            if (status == null) {
                setBusy(false)
                showMessage(R.string.games_error_load)
                return@fetchStatus
            }
            applyStatus(status)
        }
    }

    private fun applyStatus(status: GamesRepository.GameStatus) {
        setBusy(false)
        if (status.canSpinToday) {
            binding.spinNowButton.isEnabled = true
            binding.spinNowButton.setText(R.string.games_spin_button)
        } else {
            binding.spinNowButton.isEnabled = false
            binding.spinNowButton.setText(R.string.games_spin_come_back)
        }
    }

    private fun onSpinTapped() {
        if (isSpinning || isBusy) return
        if (!isNetworkAvailable()) {
            showMessage(R.string.error_no_internet)
            return
        }
        setBusy(true, R.string.games_spin_checking)

        GamesRepository.fetchStatus(userID) { status ->
            if (isFinishing || isDestroyed) return@fetchStatus
            if (status == null) {
                setBusy(false)
                showMessage(R.string.games_error_load)
                return@fetchStatus
            }
            if (!status.canSpinToday) {
                applyStatus(status)
                return@fetchStatus
            }

            val segments = AppConfig.SPIN_WHEEL_SEGMENTS
            val winnerIndex = Random.nextInt(segments.size)
            val pointsWon = segments[winnerIndex]

            // COMMIT FIRST. Animate only if the write succeeded (prevents free re-spins, C8).
            GamesRepository.recordSpin(userID, pointsWon, status.spinAttemptsTotal + 1) { ok ->
                if (isFinishing || isDestroyed) return@recordSpin
                if (!ok) {
                    setBusy(false)
                    showMessage(R.string.games_error_save)
                    return@recordSpin
                }
                animateWheelTo(winnerIndex, pointsWon)
            }
        }
    }

    private fun animateWheelTo(winnerIndex: Int, pointsWon: Int) {
        isSpinning = true
        binding.spinNowButton.isEnabled = false
        binding.resultText.text = ""

        val segments = AppConfig.SPIN_WHEEL_SEGMENTS
        val sweep = 360f / segments.size
        val jitter = (Random.nextFloat() - 0.5f) * sweep * 0.7f // land off-centre
        val desired = -(winnerIndex * sweep) + jitter
        val current = binding.wheel.rotation % 360f
        var delta = (desired - current) % 360f
        if (delta < 0f) delta += 360f
        val target = binding.wheel.rotation + 360f * GameConfig.SPIN_EXTRA_ROTATIONS + delta

        binding.wheel.animate()
            .rotation(target)
            .setDuration(GameConfig.SPIN_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (isFinishing || isDestroyed) return@withEndAction
                isSpinning = false
                binding.resultText.text = getString(R.string.games_spin_result, pointsWon)
                setBusy(false)
                showSpunToday()
            }
            .start()
    }

    private fun showSpunToday() {
        binding.spinNowButton.isEnabled = false
        binding.spinNowButton.setText(R.string.games_spin_come_back)
    }

    private fun showMessage(resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    }
}
