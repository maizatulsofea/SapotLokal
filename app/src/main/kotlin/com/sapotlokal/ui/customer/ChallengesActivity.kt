package com.sapotlokal.ui.customer

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ValueEventListener
import com.sapotlokal.data.model.ChallengeWithProgress
import com.sapotlokal.data.model.UserChallenge
import com.sapotlokal.data.model.UserChallengeStatus
import com.sapotlokal.data.repository.ChallengeRepository
import com.sapotlokal.databinding.ActivityChallengesBinding
import com.sapotlokal.service.SapotFcmService

/**
 * ChallengesActivity — Phase 6 Task 6.1:
 * Displays active loyalty challenges with customer progress and one-time reward claiming.
 */
class ChallengesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChallengesBinding
    private val auth = FirebaseAuth.getInstance()
    private lateinit var adapter: ChallengeAdapter
    private var challengesListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChallengesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        adapter = ChallengeAdapter { item ->
            claimReward(user.uid, item)
        }

        binding.rvChallenges.layoutManager = LinearLayoutManager(this)
        binding.rvChallenges.adapter = adapter

        binding.progressBar.visibility = View.VISIBLE

        // Synchronize ledger progress against challenges
        ChallengeRepository.syncUserProgress(user.uid) {
            runOnUiThread {
                binding.progressBar.visibility = View.GONE
            }
        }

        // Real-time listener for challenges and user challenge progress
        challengesListener = ChallengeRepository.listenToChallengesWithProgress(
            userID = user.uid,
            onUpdate = { list ->
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    adapter.submitList(list)
                    binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvChallenges.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
                }
            },
            onError = { err ->
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Error loading challenges: $err", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun claimReward(userID: String, item: ChallengeWithProgress) {
        val userChallenge = item.userChallenge ?: UserChallenge(
            userChallengeID = "${userID}_${item.challenge.challengeID}",
            userID = userID,
            challengeID = item.challenge.challengeID,
            currentValue = item.challenge.challengeTargetValue,
            userChallengeStatus = UserChallengeStatus.COMPLETED
        )

        ChallengeRepository.claimReward(
            userID = userID,
            challenge = item.challenge,
            userChallenge = userChallenge
        ) { success, errorMsg ->
            if (success) {
                val reward = item.challenge.challengeRewardPoint
                Toast.makeText(this, "🎉 Claimed +$reward bonus points!", Toast.LENGTH_LONG).show()

                // Trigger local notification
                SapotFcmService.showLocalNotification(
                    context = this,
                    title = "Challenge Completed!",
                    body = "You earned +$reward bonus points for completing \"${item.challenge.challengeTitle}\"!",
                    channelId = SapotFcmService.CHANNEL_REWARDS
                )
            } else {
                Toast.makeText(this, errorMsg ?: "Could not claim reward", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        challengesListener?.let { ChallengeRepository.removeListener(it) }
    }
}

