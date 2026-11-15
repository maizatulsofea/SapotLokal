package com.sapotlokal.ui.customer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ValueEventListener
import com.sapotlokal.AppConfig
import com.sapotlokal.R
import com.sapotlokal.data.repository.GamesRepository
import com.sapotlokal.data.repository.MissionRepository
import com.sapotlokal.databinding.ActivityChallengesBinding
import com.sapotlokal.ui.games.MemoryMatchActivity
import com.sapotlokal.ui.games.SpinWheelActivity

/**
 * Play tab. Everything fun in one place: the full rank card (the only one in the app), any
 * top-3 prize, missions, the two games and the leaderboard.
 */
class ChallengesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChallengesBinding
    private val auth = FirebaseAuth.getInstance()
    private var missions: MissionRepository.Listening? = null
    private var xpListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChallengesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        CustomerNav.bind(this, binding.customerNav, CustomerNav.Tab.PLAY)

        val user = auth.currentUser ?: run {
            Toast.makeText(this, getString(R.string.k_please_log_in), Toast.LENGTH_SHORT).show()
            finish(); return
        }

        binding.cardSpin.setOnClickListener { startActivity(Intent(this, SpinWheelActivity::class.java)) }
        binding.cardMemory.setOnClickListener { startActivity(Intent(this, MemoryMatchActivity::class.java)) }
        binding.rowLeaderboard.setOnClickListener { startActivity(Intent(this, LeaderboardActivity::class.java)) }
        binding.tvLeaderboardSub.text = AppConfig.LEADERBOARD_XP.let { getString(R.string.lb_prizes, it[0], it[1], it[2]) }

        binding.rowMissions.setOnClickListener { startActivity(Intent(this, MissionsActivity::class.java)) }
        missions = MissionRepository.listen(user.uid) { list ->
            val ready = list.count { it.claimable }
            binding.tvMissionsSub.text = if (ready > 0) {
                getString(R.string.play_missions_ready, ready)
            } else {
                com.sapotlokal.util.MissionDateUtils.formatTimeRemaining(this, com.sapotlokal.util.MissionDateUtils.getWeekExpiresAtMillis())
            }
        }

        xpListener = com.sapotlokal.data.repository.XpRepository.listenStanding(user.uid) { standing ->
            RankCard.bind(binding.rankCard, standing)
            RankCard.maybeShowRankUp(this, user.uid, standing)
        }
    }

    override fun onResume() {
        super.onResume()
        val uid = auth.currentUser?.uid ?: return
        PrizeCard.bind(this, binding.prizeSlot, uid)
        GamesRepository.fetchStatus(uid) { status ->
            if (isFinishing || isDestroyed) return@fetchStatus
            if (status == null) {
                binding.tvSpinSub.text = ""; binding.tvMemorySub.text = ""; return@fetchStatus
            }
            binding.tvSpinSub.text = getString(if (status.canSpinToday) R.string.hu_spin_ready else R.string.games_spin_used)
            binding.tvMemorySub.text = getString(R.string.hu_memory_today, status.memoryXpWinsToday.coerceAtMost(AppConfig.MEMORY_XP_WINS_PER_DAY), AppConfig.MEMORY_XP_WINS_PER_DAY)
        }
    }



    override fun onDestroy() {
        super.onDestroy()
        val uid = auth.currentUser?.uid ?: return
        missions?.stop()
        com.sapotlokal.data.repository.XpRepository.stopListening(uid, xpListener)
    }
}
