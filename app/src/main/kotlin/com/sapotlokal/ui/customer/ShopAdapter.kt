package com.sapotlokal.ui.customer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.sapotlokal.data.model.Vendor
import com.sapotlokal.databinding.ItemShopBinding

/**
 * ShopAdapter — binds a list of vendors to item_shop.xml cards.
 *
 * Each card also shows the customer's current balance at that shop,
 * passed in as [perShopBalance] (a Map<vendorID, balance>).
 *
 * The list is updated via [submitList] so the Activity can refresh it
 * in place without recreating the adapter.
 */
class ShopAdapter(
    private val perShopBalance: Map<String, Int>
) : RecyclerView.Adapter<ShopAdapter.ShopViewHolder>() {

    private val items = mutableListOf<Vendor>()

    fun submitList(vendors: List<Vendor>) {
        items.clear()
        items.addAll(vendors)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShopViewHolder {
        val binding = ItemShopBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ShopViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ShopViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ShopViewHolder(private val binding: ItemShopBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(vendor: Vendor) {
            binding.tvShopName.text     = vendor.vendorName
            binding.tvShopCategory.text = vendor.vendorCategory
            binding.tvShopAddress.text  = vendor.vendorAddress
            binding.tvShopPoints.text   = (perShopBalance[vendor.vendorID] ?: 0).toString()
        }
    }
}
