package com.sapotlokal.ui.vendor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sapotlokal.data.model.Voucher
import com.sapotlokal.databinding.ItemRewardRuleBinding

/**
 * RewardRuleAdapter — RecyclerView adapter for the Reward Rules list.
 *
 * Uses ListAdapter + DiffUtil so only changed rows are redrawn when the live
 * Firebase listener fires a new list.
 *
 * Each row has:
 *   - Name, description, points required
 *   - A MaterialSwitch to toggle isActive
 *   - An edit icon button
 *
 * IMPORTANT — the Switch listener pattern:
 *   We remove the listener before setting the checked state, then re-attach it.
 *   Without this, setChecked() inside onBindViewHolder triggers the listener
 *   and causes phantom toggle events when the RecyclerView recycles views.
 */
class RewardRuleAdapter(
    private val onEditClick:    (Voucher) -> Unit,
    private val onToggleActive: (Voucher, Boolean) -> Unit
) : ListAdapter<Voucher, RewardRuleAdapter.RuleViewHolder>(DiffCallback) {

    /** Times each reward (by voucherID) has been claimed, all time. */
    var claims: Map<String, Int> = emptyMap()
        set(value) { field = value; notifyDataSetChanged() }

    inner class RuleViewHolder(
        private val binding: ItemRewardRuleBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(rule: Voucher) {
            binding.tvRuleName.text        = rule.voucherName
            binding.tvRuleDescription.text = rule.voucherDescription
            binding.tvPointsRequired.text  = binding.root.context.getString(com.sapotlokal.R.string.owner_n_points, rule.pointsRequired)

            // Remove listener before setting state to avoid phantom callbacks on recycle
            binding.switchActive.setOnCheckedChangeListener(null)
            binding.switchActive.isChecked = rule.isActive
            binding.switchActive.setOnCheckedChangeListener { _, isChecked ->
                onToggleActive(rule, isChecked)
            }

            binding.tvRuleDescription.visibility =
                if (rule.voucherDescription.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
            // A switched-off reward reads quieter, so the owner sees at a glance what customers can claim.
            binding.tvRuleName.alpha = if (rule.isActive) 1f else 0.55f
            val ctx = binding.root.context
            val n = claims[rule.voucherID] ?: 0
            binding.tvClaimed.text = if (n == 0) ctx.getString(com.sapotlokal.R.string.owner_not_claimed_yet)
                else ctx.resources.getQuantityString(com.sapotlokal.R.plurals.owner_claimed_times, n, n)
            binding.tvVisibility.setText(if (rule.isActive) com.sapotlokal.R.string.owner_reward_shown else com.sapotlokal.R.string.owner_reward_hidden)
            binding.tvVisibility.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, if (rule.isActive) com.sapotlokal.R.color.hu_ok else com.sapotlokal.R.color.hu_soft))
            binding.switchActive.contentDescription = ctx.getString(com.sapotlokal.R.string.owner_reward_shown)
            binding.root.setOnClickListener { onEditClick(rule) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleViewHolder {
        val binding = ItemRewardRuleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RuleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RuleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Voucher>() {
        override fun areItemsTheSame(old: Voucher, new: Voucher) = old.voucherID == new.voucherID
        override fun areContentsTheSame(old: Voucher, new: Voucher) = old == new
    }
}
