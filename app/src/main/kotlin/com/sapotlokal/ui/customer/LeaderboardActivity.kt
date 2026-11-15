package com.sapotlokal.ui.customer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ValueEventListener
import com.sapotlokal.AppConfig.Rank
import com.sapotlokal.R
import com.sapotlokal.data.model.AvatarDef
import com.sapotlokal.data.repository.LeaderboardRepository
import com.sapotlokal.databinding.ActivityLeaderboardBinding
import com.sapotlokal.databinding.ItemLeaderboardRowBinding
import com.sapotlokal.databinding.ViewPodiumSpotBinding
import com.sapotlokal.util.MissionDateUtils

/**
 * Leaderboard (REFRACTOR.md Phase 8): this week's visit race, read live from /weeklyVisits.
 * Top 3 on a podium, places 4–20 below, and the customer's own place pinned at the bottom.
 */
class LeaderboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLeaderboardBinding
    private val week = MissionDateUtils.getWeekYearKey()
    private var listener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLeaderboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnBack.setOnClickListener { finish() }
        binding.tvResets.text = getString(
            R.string.lb_this_week_left, MissionDateUtils.formatTimeRemaining(this, MissionDateUtils.getWeekExpiresAtMillis())
        )

        // Your own row sits on the pale orange wash, flush with the bottom of the screen.
        binding.meRow.root.apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.hu_accent_tint))
            (layoutParams as LinearLayout.LayoutParams).topMargin = 0
            setPadding(paddingLeft + dp(6), dp(14), paddingRight + dp(6), dp(14))
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        listener = LeaderboardRepository.listenWeek(week, uid) { top, me -> render(top, me) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun render(top: List<LeaderboardRepository.Entry>, me: LeaderboardRepository.Entry?) {
        binding.progressBar.visibility = View.GONE
        binding.tvEmpty.visibility = if (top.isEmpty()) View.VISIBLE else View.GONE
        binding.podium.visibility = if (top.isEmpty()) View.GONE else View.VISIBLE

        bindSpot(binding.first, top.getOrNull(0), 96)
        bindSpot(binding.second, top.getOrNull(1), 72)
        bindSpot(binding.third, top.getOrNull(2), 52)

        binding.rows.removeAllViews()
        top.drop(3).forEach { e ->
            val row = ItemLeaderboardRowBinding.inflate(LayoutInflater.from(this), binding.rows, false)
            val isMe = e.uid == me?.uid
            bindRow(row, e, if (isMe) getString(R.string.lb_you) else e.userName)
            if (isMe) row.root.backgroundTintList = ContextCompat.getColorStateList(this, R.color.hu_accent_tint)
            binding.rows.addView(row.root)
        }

        // The "You" bar is only for when your own row isn't already in the list above.
        val mine = binding.meRow
        mine.root.visibility = if (me != null && top.any { it.uid == me.uid }) View.GONE else View.VISIBLE
        if (me == null) {
            mine.tvPlace.text = "–"
            setAvatar(mine.ivAvatar, null)
            mine.tvName.text = getString(R.string.lb_you)
            mine.tvVisits.text = getString(R.string.lb_no_visits)
            mine.root.contentDescription = "${getString(R.string.lb_you)}, ${getString(R.string.lb_no_visits)}"
        } else {
            bindRow(mine, me, getString(R.string.lb_you))
        }
    }

    private fun visits(n: Int) = resources.getQuantityString(R.plurals.lb_visits, n, n)

    private fun bindRow(row: ItemLeaderboardRowBinding, e: LeaderboardRepository.Entry, name: String) {
        row.tvPlace.text = e.place.toString()
        setAvatar(row.ivAvatar, e.avatar)
        row.tvName.text = name
        row.tvVisits.text = visits(e.visits)
        row.root.contentDescription = getString(R.string.lb_row_cd, name, e.place, visits(e.visits))
    }

    private fun bindSpot(spot: ViewPodiumSpotBinding, e: LeaderboardRepository.Entry?, blockDp: Int) {
        spot.root.visibility = if (e == null) View.INVISIBLE else View.VISIBLE
        if (e == null) return
        setAvatar(spot.ivAvatar, e.avatar)
        // Medals use the rank colours: 1st the top-rank ink with its orange ring, then purple, blue.
        RankCard.bindBadge(spot.tvMedal, when (e.place) { 1 -> Rank.LEGENDA_SAPOT; 2 -> Rank.OTAI_LOKAL; else -> Rank.KAKI_LOKAL })
        spot.tvMedal.text = e.place.toString()
        spot.tvName.text = e.userName
        spot.tvVisits.text = visits(e.visits)
        spot.block.layoutParams = spot.block.layoutParams.apply { height = dp(blockDp) }
        if (e.place == 1) spot.block.backgroundTintList = ContextCompat.getColorStateList(this, R.color.hu_accent_tint)
        spot.root.contentDescription = getString(R.string.lb_row_cd, e.userName, e.place, visits(e.visits))
    }

    private fun setAvatar(view: ImageView, avatarId: String?) {
        val avatar = AvatarDef.fromId(avatarId)
        if (avatar != null) {
            view.imageTintList = null
            view.setImageResource(avatar.drawableRes)
        } else {
            view.setImageResource(R.drawable.ic_user_round)
            view.imageTintList = ContextCompat.getColorStateList(this, R.color.hu_soft)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LeaderboardRepository.stopListening(week, listener)
    }
}
