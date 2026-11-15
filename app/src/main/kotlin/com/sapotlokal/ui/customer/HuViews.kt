package com.sapotlokal.ui.customer

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.sapotlokal.AppConfig
import com.sapotlokal.R
import com.sapotlokal.data.model.ShopCategory
import com.sapotlokal.data.model.Vendor
import com.sapotlokal.data.model.Voucher
import com.sapotlokal.databinding.ItemHuRewardCardBinding
import com.sapotlokal.databinding.ItemHuShopCardBinding
import kotlin.math.abs

/**
 * Building blocks shared by the customer screens: each shop's fixed colour,
 * the shop card, the reward card and the reward row.
 */
object ShopStyle {

    /** A shop's colour and the text colour that reads on it. */
    data class Colors(val bg: Int, val on: Int)

    private val palette = listOf(
        R.color.hu_shop_pink to false,
        R.color.hu_shop_indigo to false,
        R.color.hu_shop_purple to false,
        R.color.hu_shop_green to false,
        R.color.hu_shop_blue to false,
        R.color.hu_shop_teal to false,
        R.color.hu_shop_berry to false,
        R.color.hu_shop_coral to false
    )

    /** Fixed per shop: the same vendor always gets the same colour. */
    fun colors(context: Context, vendorId: String): Colors {
        val (res, darkText) = palette[abs(vendorId.hashCode()) % palette.size]
        val on = ContextCompat.getColor(context, if (darkText) R.color.hu_ink else R.color.hu_bg)
        return Colors(ContextCompat.getColor(context, res), on)
    }

    /** Two-letter logo for shops without a picture: "Kopi Kita Cafe" → "KK". */
    fun initials(name: String): String {
        val words = name.split(' ', '&').filter { it.isNotBlank() }
        return when {
            words.isEmpty() -> "?"
            words.size == 1 -> words[0].take(2).uppercase()
            else -> (words[0].take(1) + words[1].take(1)).uppercase()
        }
    }

    /** Shop points this customer gets per visit at [vendor]: the shop's rate × their rank bonus. */
    fun perVisit(vendor: Vendor?, rank: AppConfig.Rank): Int {
        val rate = vendor?.pointsPerVisit?.takeIf { it in AppConfig.ALLOWED_POINTS_PER_VISIT } ?: AppConfig.DEFAULT_POINTS_PER_VISIT
        return AppConfig.shopPointsForVisit(rate, rank)
    }
}

/**
 * The shop card: the shop's photo (its colour and initials until it has one), name, points,
 * "Cafe · Open", progress to the next reward and one status line. Used on Home, Shops and the
 * shop page, so a shop looks the same everywhere.
 */
object ShopCard {

    fun inflate(parent: ViewGroup): ItemHuShopCardBinding =
        ItemHuShopCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)

    /**
     * [readyReward]: the cheapest reward the customer can claim now. [holdsVoucher]: they already
     * hold an unused voucher here. [nextReward]: the cheapest reward they haven't reached.
     * [visited]: they have been here before, even if their points are all spent.
     */
    fun bind(
        b: ItemHuShopCardBinding,
        vendor: Vendor?,
        vendorId: String,
        balance: Int,
        readyReward: Voucher?,
        holdsVoucher: Boolean,
        nextReward: Voucher?,
        visited: Boolean
    ) {
        val ctx = b.root.context
        val c = ShopStyle.colors(ctx, vendorId)
        val name = vendor?.vendorName?.ifBlank { null } ?: ctx.getString(R.string.k_shop)
        b.tvName.text = name
        b.tvPoints.text = ctx.getString(R.string.hu_n_pts, balance)
        b.tvMeta.text = listOfNotNull(
            vendor?.let { ShopCategory.label(ctx, it.vendorCategory).ifBlank { null } },
            vendor?.let { ctx.getString(if (it.isOpen) R.string.hu_open else R.string.hu_closed) }
        ).joinToString(" · ")

        val status: String
        val progress: Int?
        when {
            readyReward != null -> {
                status = ctx.getString(R.string.hu_reward_name_ready, readyReward.voucherName)
                progress = 100
            }
            holdsVoucher -> {
                status = ctx.getString(R.string.hu_voucher_to_use)
                progress = 100
            }
            nextReward != null && !visited -> {
                status = ctx.getString(R.string.hu_new_to_you)
                progress = 0
            }
            nextReward != null -> {
                status = ctx.getString(R.string.hu_to_go_for, nextReward.pointsRequired - balance, nextReward.voucherName)
                progress = (balance * 100 / nextReward.pointsRequired.coerceAtLeast(1)).coerceIn(0, 100)
            }
            else -> {
                status = ctx.getString(R.string.hu_no_rewards_yet)
                progress = null
            }
        }
        b.tvStatus.text = status
        b.progress.visibility = if (progress == null) View.GONE else View.VISIBLE
        b.progress.setIndicatorColor(c.bg)
        b.progress.progress = progress ?: 0

        // Photo: the shop's own picture over its colour and initials.
        b.photoFrame.setBackgroundColor(c.bg)
        b.tvLogo.text = ShopStyle.initials(name)
        b.tvLogo.setTextColor(c.on)
        b.ivPhoto.tag = vendorId
        b.ivPhoto.setImageDrawable(null)
        com.sapotlokal.data.repository.VendorPhotoRepository.load(vendorId) { bmp ->
            if (bmp != null && b.ivPhoto.tag == vendorId) b.ivPhoto.setImageBitmap(bmp)
        }
        b.root.contentDescription = ctx.getString(R.string.hu_shop_card_cd, name, balance, status)
    }
}

/** The reward card: shop logo + name, reward name, one detail line and one action that carries the cost. */
object RewardCard {

    fun inflate(parent: ViewGroup): ItemHuRewardCardBinding =
        ItemHuRewardCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)

    fun bind(
        b: ItemHuRewardCardBinding,
        vendorId: String,
        shopName: String,
        title: String,
        detail: String,
        action: String?,
        onAction: (() -> Unit)?
    ) {
        val ctx = b.root.context
        val c = ShopStyle.colors(ctx, vendorId)
        b.root.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(c.bg, 34))
        b.tvLogo.text = ShopStyle.initials(shopName)
        b.tvLogo.backgroundTintList = ColorStateList.valueOf(c.bg)
        b.tvLogo.setTextColor(c.on)
        b.tvShop.text = shopName
        b.tvTitle.text = title
        b.tvDetail.text = detail
        if (action != null && onAction != null) {
            b.btnAction.text = action
            b.btnAction.setOnClickListener { onAction() }
            b.btnAction.visibility = View.VISIBLE
        } else {
            b.btnAction.visibility = View.GONE
        }
    }
}

/**
 * "How it works" (REFRACTOR.md Phase 9): points, XP and the rank bonus in three short rows, linked
 * from Home, Shops, the Shop page, Rewards, Play and Me. Home also shows it once to a new customer.
 */
object HowPoints {
    fun show(context: Context) {
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(context)
        val b = com.sapotlokal.databinding.SheetHowItWorksBinding.inflate(LayoutInflater.from(context))
        b.btnGotIt.setOnClickListener { sheet.dismiss() }
        sheet.setContentView(b.root)
        sheet.show()
    }

    /** First run: shows the sheet once per account on this phone (a convenience flag, not game state). */
    fun showOnce(context: Context, uid: String) {
        val prefs = context.getSharedPreferences("sapot_user_prefs", Context.MODE_PRIVATE)
        val key = "seen_how_it_works_$uid"
        if (uid.isBlank() || prefs.getBoolean(key, false)) return
        prefs.edit().putBoolean(key, true).apply()
        show(context)
    }
}


/** Filter chip: ink when selected, quiet grey fill otherwise. */
object HuChip {
    fun make(context: Context, label: String, checked: Boolean, onClick: () -> Unit): com.google.android.material.chip.Chip {
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        val ink = ContextCompat.getColor(context, R.color.hu_ink)
        val fill = ContextCompat.getColor(context, R.color.hu_fill)
        val white = ContextCompat.getColor(context, R.color.hu_bg)
        return com.google.android.material.chip.Chip(context).apply {
            text = label
            isCheckable = true
            isChecked = checked
            isCheckedIconVisible = false
            chipBackgroundColor = ColorStateList(states, intArrayOf(ink, fill))
            setTextColor(ColorStateList(states, intArrayOf(white, ink)))
            chipStrokeWidth = 0f
            textSize = 15f
            typeface = ResourcesCompat.getFont(context, R.font.figtree_bold)
            chipMinHeight = 44f * context.resources.displayMetrics.density
            setEnsureMinTouchTargetSize(true)
            setOnClickListener { onClick() }
        }
    }
}

/** A reward or voucher row. [showThumb] is false on the shop page, where the shop is already shown. */
object RewardRow {
    fun inflate(parent: ViewGroup): com.sapotlokal.databinding.ItemHuRewardRowBinding =
        com.sapotlokal.databinding.ItemHuRewardRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)

    fun bind(
        b: com.sapotlokal.databinding.ItemHuRewardRowBinding,
        vendorId: String,
        shopName: String,
        title: String,
        sub: String,
        state: String,
        stateIsGood: Boolean,
        action: String?,
        onAction: (() -> Unit)?,
        showThumb: Boolean = true
    ) {
        val ctx = b.root.context
        val c = ShopStyle.colors(ctx, vendorId)
        b.tvName.text = title
        b.tvSub.text = sub
        b.tvSub.visibility = if (sub.isBlank()) View.GONE else View.VISIBLE
        b.tvState.text = state
        b.tvState.visibility = if (state.isBlank()) View.GONE else View.VISIBLE
        b.thumb.visibility = if (showThumb) View.VISIBLE else View.GONE
        (b.tvName.parent as? View)?.let { col ->
            (col.layoutParams as? ViewGroup.MarginLayoutParams)?.marginStart = if (showThumb) col.resources.getDimensionPixelSize(R.dimen.hu_row_gap) else 0
        }
        b.tvState.setTextColor(ContextCompat.getColor(ctx, if (stateIsGood) R.color.hu_accent_text else R.color.hu_soft))
        b.tvThumbLogo.text = ShopStyle.initials(shopName)
        b.tvThumbLogo.setTextColor(c.bg)
        b.tvThumbLogo.setBackgroundColor(ColorUtils.setAlphaComponent(c.bg, 50))
        if (action != null && onAction != null) {
            b.btnAction.text = action
            b.btnAction.setOnClickListener { onAction() }
            b.btnAction.visibility = View.VISIBLE
        } else {
            b.btnAction.visibility = View.GONE
        }
        b.root.contentDescription = listOf(title, sub, state).filter { it.isNotBlank() }.joinToString(", ")
        b.ivThumb.tag = vendorId
        b.ivThumb.setImageDrawable(null)
        com.sapotlokal.data.repository.VendorPhotoRepository.load(vendorId) { bmp ->
            if (bmp != null && b.ivThumb.tag == vendorId) b.ivThumb.setImageBitmap(bmp)
        }
    }
}
