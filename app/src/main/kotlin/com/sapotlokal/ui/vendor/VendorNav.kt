package com.sapotlokal.ui.vendor

import android.content.Intent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.sapotlokal.R
import com.sapotlokal.databinding.LayoutVendorBottomNavBinding

/**
 * VendorNav — the shop-owner bottom bar: Home · Rewards · Show QR · Redeem · Shop.
 *
 * Home is the root of the owner back stack. Rewards, Redeem and Shop replace each other, so the
 * stack never grows and Back always returns to Home. Show QR (the raised centre button) opens the
 * full-screen QR as a subpage on top of whichever tab is showing.
 */
object VendorNav {

    enum class Tab { HOME, REWARDS, REDEEM, SHOP }

    fun bind(
        activity: AppCompatActivity,
        nav: LayoutVendorBottomNavBinding,
        selected: Tab,
        vendorId: () -> String
    ) {
        highlight(activity, nav, selected)

        fun go(target: Tab, cls: Class<*>) {
            if (target == selected) return
            if (target == Tab.HOME) {
                // Home is always underneath: finishing returns to it.
                activity.finish()
                activity.overridePendingTransition(0, 0)
                return
            }
            val id = vendorId()
            if (id.isBlank()) return
            activity.startActivity(
                Intent(activity, cls)
                    .putExtra(VendorHomeActivity.EXTRA_VENDOR_ID, id)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
            if (selected != Tab.HOME) activity.finish()
            activity.overridePendingTransition(0, 0)
        }

        fun showQr() {
            val id = vendorId()
            if (id.isBlank()) return
            activity.startActivity(
                Intent(activity, ShowQrActivity::class.java).putExtra(VendorHomeActivity.EXTRA_VENDOR_ID, id)
            )
        }

        nav.tabHome.setOnClickListener { go(Tab.HOME, VendorHomeActivity::class.java) }
        nav.tabRewards.setOnClickListener { go(Tab.REWARDS, RewardRulesActivity::class.java) }
        nav.tabShowQr.setOnClickListener { showQr() }
        nav.qrFab.setOnClickListener { showQr() }
        nav.tabRedeem.setOnClickListener { go(Tab.REDEEM, ScanToRedeemActivity::class.java) }
        nav.tabShop.setOnClickListener { go(Tab.SHOP, ShopProfileActivity::class.java) }
    }

    private fun highlight(activity: AppCompatActivity, nav: LayoutVendorBottomNavBinding, selected: Tab) {
        val active = ContextCompat.getColor(activity, R.color.hu_ink)
        val idle = ContextCompat.getColor(activity, R.color.hu_soft)

        class Item(val tab: Tab, val bar: View, val icon: ImageView, val label: TextView)
        listOf(
            Item(Tab.HOME, nav.barHome, nav.iconHome, nav.labelHome),
            Item(Tab.REWARDS, nav.barRewards, nav.iconRewards, nav.labelRewards),
            Item(Tab.REDEEM, nav.barRedeem, nav.iconRedeem, nav.labelRedeem),
            Item(Tab.SHOP, nav.barShop, nav.iconShop, nav.labelShop)
        ).forEach {
            val on = it.tab == selected
            it.bar.visibility = if (on) View.VISIBLE else View.INVISIBLE
            it.icon.setColorFilter(if (on) active else idle)
            it.label.setTextColor(if (on) active else idle)
            it.label.isSelected = on
            (it.label.parent as View).contentDescription =
                if (on) activity.getString(R.string.nav_tab_selected_cd, it.label.text) else it.label.text
        }
        nav.labelShowQr.setTextColor(idle)
    }
}
