package com.sapotlokal.ui.customer

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.sapotlokal.AppConfig.Rank
import com.sapotlokal.R
import com.sapotlokal.data.repository.XpRepository
import com.sapotlokal.databinding.SheetRankUpBinding
import com.sapotlokal.databinding.ViewRankCardBinding
import com.sapotlokal.util.RankText
import java.text.NumberFormat

/**
 * The rank card (BRAND_DESIGN.md §6), shown in full on Play only, and the rank-up sheet.
 * Both read users/{uid}/xp through XpRepository; nothing else decides a customer's rank.
 */
object RankCard {

    private fun fmt(n: Int): String = NumberFormat.getIntegerInstance().format(n)

    fun colorRes(rank: Rank): Int = when (rank) {
        Rank.JIRAN_BARU -> R.color.hu_rank_1
        Rank.PELANGGAN_SETIA -> R.color.hu_rank_2
        Rank.KAKI_LOKAL -> R.color.hu_rank_3
        Rank.OTAI_LOKAL -> R.color.hu_rank_4
        Rank.LEGENDA_SAPOT -> R.color.hu_rank_5
    }

    /** Draws [rank] into a round badge: the rank colour with its number; rank 5 gets the orange ring. */
    fun bindBadge(badge: android.widget.TextView, rank: Rank) {
        badge.text = rank.level.toString()
        if (rank == Rank.LEGENDA_SAPOT) {
            badge.setBackgroundResource(R.drawable.bg_rank_badge_top)
            badge.backgroundTintList = null
        } else {
            badge.setBackgroundResource(R.drawable.bg_rank_badge)
            badge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(badge.context, colorRes(rank)))
        }
    }

    fun bind(b: ViewRankCardBinding, standing: XpRepository.Standing) {
        val ctx = b.root.context
        val rank = standing.rank
        val next = rank.next
        bindBadge(b.tvRankBadge, rank)
        b.tvRankName.text = rank.title
        b.tvXp.text = fmt(standing.xp)

        if (next == null) {
            b.rankBar.visibility = View.GONE
            b.tvToNext.text = ctx.getString(R.string.rank_top)
        } else {
            // Just this rank's stretch; "x XP to next" below says how far.
            val span = next.minXp - rank.minXp
            b.rankBar.visibility = View.VISIBLE
            b.rankBar.max = span
            b.rankBar.progress = (standing.xp - rank.minXp).coerceIn(0, span)
            b.tvToNext.text = ctx.getString(R.string.rank_xp_to_next, fmt(next.minXp - standing.xp), next.title)
        }

        b.tvBonus.text = if (rank.pointsBonus > 1.0) {
            ctx.getString(R.string.rank_bonus_line, RankText.bonus(rank.pointsBonus))
        } else {
            ctx.getString(R.string.rank_bonus_first, next!!.title, RankText.bonus(next.pointsBonus))
        }

        b.root.contentDescription = listOf(
            ctx.getString(R.string.rank_card_cd, rank.title, rank.level, fmt(standing.xp)),
            b.tvToNext.text,
            b.tvBonus.text
        ).joinToString(". ")
    }

    /** Highest rank a sheet has been shown for in this app session; stops two open screens both showing it. */
    private var shownRank = 0

    /**
     * Shows the rank-up sheet once per new rank. A customer who has never had a rank recorded as
     * seen (everyone before this feature) is marked quietly instead, so nobody gets a surprise
     * sheet for a rank they already had.
     *
     * Home, Play and Me stay alive under the Scan screen and all hear the XP change. The sheet
     * waits until its screen is in front, so it is never shown (and marked seen) behind another.
     */
    fun maybeShowRankUp(activity: AppCompatActivity, uid: String, standing: XpRepository.Standing) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (standing.rankSeen == 0) return XpRepository.markRankSeen(uid, standing.rank)
        if (standing.rank.level <= standing.rankSeen) return
        whenResumed(activity) {
            if (standing.rank.level <= shownRank) return@whenResumed
            shownRank = standing.rank.level
            XpRepository.markRankSeen(uid, standing.rank)
            showRankUp(activity, standing.rank)
        }
    }

    private fun whenResumed(activity: AppCompatActivity, action: () -> Unit) {
        val lifecycle = activity.lifecycle
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return action()
        lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_RESUME) {
                    lifecycle.removeObserver(this)
                    action()
                } else if (event == Lifecycle.Event.ON_DESTROY) {
                    lifecycle.removeObserver(this)
                }
            }
        })
    }

    private fun showRankUp(activity: AppCompatActivity, rank: Rank) {
        val sheet = BottomSheetDialog(activity)
        val b = SheetRankUpBinding.inflate(LayoutInflater.from(activity))
        bindBadge(b.tvRankBadge, rank)
        b.tvTitle.text = activity.getString(R.string.rank_up_title, rank.title)
        b.tvBody.text = activity.getString(R.string.rank_up_body, RankText.bonus(rank.pointsBonus))
        b.btnNice.setOnClickListener { sheet.dismiss() }
        sheet.setContentView(b.root)
        sheet.show()
    }
}
