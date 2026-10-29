package com.sapotlokal.ui.customer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sapotlokal.R
import com.sapotlokal.data.model.ChallengeWithProgress
import com.sapotlokal.data.model.UserChallengeStatus
import com.sapotlokal.databinding.ItemChallengeCardBinding

/**
 * ChallengeAdapter — binds ChallengeWithProgress items to item_challenge_card.xml.
 */
class ChallengeAdapter(
    private val onClaimClick: (ChallengeWithProgress) -> Unit
) : ListAdapter<ChallengeWithProgress, ChallengeAdapter.ChallengeViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChallengeViewHolder {
        val binding = ItemChallengeCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ChallengeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChallengeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ChallengeViewHolder(
        private val binding: ItemChallengeCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ChallengeWithProgress) {
            val challenge = item.challenge
            val userChallenge = item.userChallenge
            val target = challenge.challengeTargetValue
            val current = item.progress.coerceAtMost(target)

            binding.tvChallengeTitle.text = challenge.challengeTitle
            binding.tvChallengeDesc.text = challenge.challengeDescription
            binding.tvRewardBadge.text = "+${challenge.challengeRewardPoint} pts"

            binding.pbChallenge.max = if (target > 0) target else 1
            binding.pbChallenge.progress = current
            binding.tvProgressText.text = "$current / $target"

            val status = userChallenge?.userChallengeStatus ?: UserChallengeStatus.IN_PROGRESS

            when (status) {
                UserChallengeStatus.CLAIMED -> {
                    binding.btnClaimReward.visibility = View.GONE
                    binding.tvStatusBadge.visibility = View.VISIBLE
                    binding.tvStatusBadge.text = "✓ Claimed"
                    binding.tvStatusBadge.background = ContextCompat.getDrawable(
                        itemView.context, R.drawable.bg_pill_leaf
                    )
                    binding.tvStatusBadge.setTextColor(
                        ContextCompat.getColor(itemView.context, R.color.sapot_text_primary)
                    )
                }
                UserChallengeStatus.COMPLETED -> {
                    binding.btnClaimReward.visibility = View.VISIBLE
                    binding.tvStatusBadge.visibility = View.GONE
                    binding.btnClaimReward.setOnClickListener {
                        onClaimClick(item)
                    }
                }
                else -> {
                    // In Progress
                    if (current >= target) {
                        // Progress reached target but not marked completed yet
                        binding.btnClaimReward.visibility = View.VISIBLE
                        binding.tvStatusBadge.visibility = View.GONE
                        binding.btnClaimReward.setOnClickListener {
                            onClaimClick(item)
                        }
                    } else {
                        binding.btnClaimReward.visibility = View.GONE
                        binding.tvStatusBadge.visibility = View.VISIBLE
                        binding.tvStatusBadge.text = "In Progress"
                        binding.tvStatusBadge.background = ContextCompat.getDrawable(
                            itemView.context, R.drawable.bg_pill_gray
                        )
                        binding.tvStatusBadge.setTextColor(
                            ContextCompat.getColor(itemView.context, R.color.sapot_text_secondary)
                        )
                    }
                }
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<ChallengeWithProgress>() {
        override fun areItemsTheSame(
            oldItem: ChallengeWithProgress,
            newItem: ChallengeWithProgress
        ): Boolean = oldItem.challenge.challengeID == newItem.challenge.challengeID

        override fun areContentsTheSame(
            oldItem: ChallengeWithProgress,
            newItem: ChallengeWithProgress
        ): Boolean = oldItem == newItem
    }
}

