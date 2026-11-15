package com.sapotlokal.ui.customer

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import com.sapotlokal.R
import com.sapotlokal.data.repository.LeaderboardRepository
import com.sapotlokal.databinding.ViewPrizeCardBinding

/**
 * "You came #2 last week · Claim 100 XP" on Home and Play (REFRACTOR.md §3.6). Shows the newest
 * unclaimed top-3 place from the last few weeks; after a claim it checks again for an older one.
 */
object PrizeCard {

    fun bind(activity: Activity, slot: FrameLayout, uid: String) {
        LeaderboardRepository.findPrizes(uid) { prizes ->
            if (activity.isFinishing || activity.isDestroyed) return@findPrizes
            slot.removeAllViews()
            val prize = prizes.firstOrNull()
            slot.visibility = if (prize == null) View.GONE else View.VISIBLE
            if (prize == null) return@findPrizes

            val b = ViewPrizeCardBinding.inflate(LayoutInflater.from(activity), slot, false)
            val lastWeek = LeaderboardRepository.claimableWeeks().first()
            b.tvTitle.text = if (prize.weekKey == lastWeek) {
                activity.getString(R.string.lb_prize_last_week, prize.place)
            } else {
                activity.getString(R.string.lb_prize_week, prize.place, prize.weekKey.substringAfter("-W").toInt())
            }
            b.btnClaim.text = activity.getString(R.string.mission_claim, prize.xp)
            b.btnClaim.setOnClickListener {
                b.btnClaim.isEnabled = false
                LeaderboardRepository.claim(uid, prize) { award ->
                    if (activity.isFinishing || activity.isDestroyed) return@claim
                    if (award == null) {
                        b.btnClaim.isEnabled = true
                        Toast.makeText(activity, R.string.k_could_not_claim, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(activity, activity.getString(R.string.k_plus_level_points, award.amount), Toast.LENGTH_SHORT).show()
                        bind(activity, slot, uid)
                    }
                }
            }
            slot.addView(b.root)
        }
    }
}
