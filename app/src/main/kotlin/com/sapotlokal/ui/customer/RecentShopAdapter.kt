package com.sapotlokal.ui.customer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sapotlokal.R
import com.sapotlokal.databinding.ItemShopCardBinding

/**
 * RecentShopAdapter — displays a customer's recently visited shops on CustomerHomeActivity.
 *
 * Each item shows the shop name, category, and the customer's current point balance
 * for that specific shop. The list is driven by [RecentShopItem], a lightweight
 * view-model that merges data from /points and /vendors.
 *
 * Uses ListAdapter + DiffUtil so only changed rows re-bind — avoids full list flickers
 * when the live points listener fires an update.
 */
class RecentShopAdapter :
    ListAdapter<RecentShopItem, RecentShopAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemShopCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemShopCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RecentShopItem) {
            binding.tvShopName.text    = item.shopName
            binding.tvShopSubtitle.text = item.category
            binding.tvShopPoints.text  = "${item.balance} pts"

            // Category icon: map known category keywords to the existing drawables;
            // fall back to the generic croissant drawable for unknown categories.
            val (bgRes, iconRes) = categoryDrawables(item.category)
            binding.avatarContainer.setBackgroundResource(bgRes)
            binding.ivCategoryIcon.setImageResource(iconRes)
        }

        /**
         * Returns a Pair<backgroundDrawable, iconDrawable> for the given category string.
         * Matches are intentionally fuzzy (contains) so \"Café\", \"café\", \"Kopitiam\" all
         * map to the coffee icon without exhaustive enumeration.
         */
        private fun categoryDrawables(category: String): Pair<Int, Int> {
            val c = category.lowercase()
            return when {
                c.contains("kopi") || c.contains("caf") || c.contains("coffee") || c.contains("tea") ->
                    R.drawable.bg_shop_icon_coffee to R.drawable.ic_coffee
                else ->
                    R.drawable.bg_shop_icon_croissant to R.drawable.ic_croissant
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RecentShopItem>() {
            override fun areItemsTheSame(old: RecentShopItem, new: RecentShopItem) =
                old.vendorID == new.vendorID
            override fun areContentsTheSame(old: RecentShopItem, new: RecentShopItem) =
                old == new
        }
    }
}

/**
 * Lightweight view-model for a single row in the Recent Shops list.
 *
 * [shopName]  — from /vendors/{vendorID}/vendorName
 * [category]  — from /vendors/{vendorID}/vendorCategory
 * [balance]   — per-shop earned minus redeemed, from /points
 * [vendorID]  — used as the stable item ID for DiffUtil
 */
data class RecentShopItem(
    val vendorID: String,
    val shopName: String,
    val category: String,
    val balance: Int
)

