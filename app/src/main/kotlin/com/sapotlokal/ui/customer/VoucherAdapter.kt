package com.sapotlokal.ui.customer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sapotlokal.R
import com.sapotlokal.data.model.VoucherStatus
import com.sapotlokal.data.model.VoucherUser
import com.sapotlokal.databinding.ItemVoucherCardBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * UI presentation model for a voucher in the My Vouchers screen.
 */
data class VoucherUiModel(
    val voucherUser: VoucherUser,
    val vendorName: String,
    val isBestChoice: Boolean,
    val isLocked: Boolean,
    val currentBalance: Int
)

/**
 * VoucherAdapter — binds voucher cards with shop names, status indicators,
 * "Best Choice" highlights, and locked-state warnings.
 */
class VoucherAdapter(
    private val onVoucherClick: (VoucherUiModel) -> Unit
) : ListAdapter<VoucherUiModel, VoucherAdapter.VoucherViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VoucherViewHolder {
        val binding = ItemVoucherCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VoucherViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VoucherViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VoucherViewHolder(
        private val binding: ItemVoucherCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        fun bind(item: VoucherUiModel) {
            val vu = item.voucherUser
            binding.tvShopName.text = item.vendorName.ifBlank { "Local Shop" }
            binding.tvRewardTitle.text = vu.voucherTitle.ifBlank { "Reward Voucher" }
            binding.tvPointsRequired.text = "${vu.pointsRequiredSnapshot} pts"
            binding.tvVoucherCode.text = vu.voucherCode

            val isExpired = vu.voucherExpiryDate in 1 until System.currentTimeMillis()
            val expiryStr = if (vu.voucherExpiryDate > 0L) {
                "Expires " + dateFormat.format(Date(vu.voucherExpiryDate))
            } else {
                "No expiry date"
            }
            binding.tvExpiryDate.text = expiryStr

            when {
                vu.voucherStatus == VoucherStatus.USED -> {
                    binding.tvStatusBadge.text = "Redeemed"
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_pill_gray)
                    binding.tvBestChoiceBadge.visibility = View.GONE
                    binding.tvLockedNotice.visibility = View.GONE
                    binding.btnViewQr.isEnabled = false
                    binding.btnViewQr.alpha = 0.5f
                    binding.cardVoucher.alpha = 0.65f
                }
                isExpired || vu.voucherStatus == VoucherStatus.EXPIRED -> {
                    binding.tvStatusBadge.text = "Expired"
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_pill_gray)
                    binding.tvBestChoiceBadge.visibility = View.GONE
                    binding.tvLockedNotice.visibility = View.GONE
                    binding.btnViewQr.isEnabled = false
                    binding.btnViewQr.alpha = 0.5f
                    binding.cardVoucher.alpha = 0.65f
                }
                item.isLocked -> {
                    binding.tvStatusBadge.text = "Locked"
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_pill_gray)
                    binding.tvBestChoiceBadge.visibility = View.GONE
                    val needed = (vu.pointsRequiredSnapshot - item.currentBalance).coerceAtLeast(1)
                    binding.tvLockedNotice.text = "🔒 Locked: Need $needed more pts at ${item.vendorName}"
                    binding.tvLockedNotice.visibility = View.VISIBLE
                    binding.btnViewQr.isEnabled = false
                    binding.btnViewQr.alpha = 0.5f
                    binding.cardVoucher.alpha = 0.85f
                }
                else -> { // ACTIVE and unlocked
                    binding.tvStatusBadge.text = "Active"
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_pill_leaf)
                    binding.tvBestChoiceBadge.visibility = if (item.isBestChoice) View.VISIBLE else View.GONE
                    binding.tvLockedNotice.visibility = View.GONE
                    binding.btnViewQr.isEnabled = true
                    binding.btnViewQr.alpha = 1f
                    binding.cardVoucher.alpha = 1f
                }
            }

            binding.cardVoucher.setOnClickListener { onVoucherClick(item) }
            binding.btnViewQr.setOnClickListener { onVoucherClick(item) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<VoucherUiModel>() {
        override fun areItemsTheSame(oldItem: VoucherUiModel, newItem: VoucherUiModel): Boolean {
            return oldItem.voucherUser.voucherUserID == newItem.voucherUser.voucherUserID
        }

        override fun areContentsTheSame(oldItem: VoucherUiModel, newItem: VoucherUiModel): Boolean {
            return oldItem == newItem
        }
    }
}

