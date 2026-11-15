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
    private var badgeWatch: com.google.firebase.database.ValueEventListener? = null

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
        badgeWatch = com.sapotlokal.ui.customer.Badges.watchUnlocks(this, userID, binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.backButton.setOnClickListener { finish() }

        binding.wheel.values = AppConfig.SPIN_XP_SEGMENTS
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
        if (::userID.isInitialized) com.sapotlokal.ui.customer.Badges.stopWatching(userID, badgeWatch)
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
        binding.spinStatusText.text = if (status.canSpinToday) getString(com.sapotlokal.R.string.k_spin_left_today) else getString(com.sapotlokal.R.string.games_spin_come_back)
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

            val segments = AppConfig.SPIN_XP_SEGMENTS
            val winnerIndex = Random.nextInt(segments.size)
            val pointsWon = segments[winnerIndex]

            // COMMIT FIRST. Animate only if the write succeeded (prevents free re-spins, C8).
            GamesRepository.recordSpin(userID, pointsWon, status.spinAttemptsTotal + 1, pointsWon == segments.max()) { ok, earned ->
                if (!ok) {
                    if (isFinishing || isDestroyed) return@recordSpin
                    setBusy(false)
                    showMessage(R.string.games_error_save)
                    return@recordSpin
                }

                if (isFinishing || isDestroyed) return@recordSpin
                animateWheelTo(winnerIndex, earned)
            }
        }
    }

    private var lastTickIndex = -1

    private fun animateWheelTo(winnerIndex: Int, pointsWon: Int) {
        isSpinning = true
        binding.spinNowButton.isEnabled = false
        binding.resultText.text = ""

        val segments = AppConfig.SPIN_XP_SEGMENTS
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
            .setUpdateListener {
                // A tick each time a slice passes the pointer.
                val n = segments.size
                val angle = ((-binding.wheel.rotation % 360f) + 360f) % 360f
                val index = Math.round(angle / sweep) % n
                if (index != lastTickIndex) {
                    lastTickIndex = index
                    com.sapotlokal.util.SoundFx.tick()
                    binding.wheel.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                }
            }
            .withEndAction {
                if (isFinishing || isDestroyed) return@withEndAction
                isSpinning = false
                binding.resultText.text = getString(R.string.games_spin_result, pointsWon)
                binding.resultText.scaleX = 0.6f
                binding.resultText.scaleY = 0.6f
                binding.resultText.animate().scaleX(1f).scaleY(1f).setDuration(350)
                    .setInterpolator(android.view.animation.OvershootInterpolator()).start()
                binding.confetti.burst(0.45f)
                com.sapotlokal.util.SoundFx.win()
                setBusy(false)
                showSpunToday()
            }
            .start()
    }

    private fun showSpunToday() {
        binding.spinStatusText.text = getString(com.sapotlokal.R.string.games_spin_come_back)
        binding.spinNowButton.isEnabled = false
        binding.spinNowButton.setText(R.string.games_spin_come_back)
    }

    private fun showMessage(resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    }
}

