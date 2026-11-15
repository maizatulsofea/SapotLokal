package com.sapotlokal.ui.customer

import android.app.Activity
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.sapotlokal.AppConfig
import com.sapotlokal.R
import com.sapotlokal.data.model.BadgeState
import com.sapotlokal.data.repository.BadgeRepository
import com.sapotlokal.databinding.ItemBadgeTileBinding
import com.sapotlokal.databinding.SheetBadgeBinding
import java.text.DateFormat
import java.util.Date

/** Badge grid on Me (BRAND_DESIGN.md §6): grey until earned, orange dot when ready to claim. */
class BadgeGridAdapter(private val onTap: (BadgeState) -> Unit) :
    ListAdapter<BadgeState, BadgeGridAdapter.Holder>(Diff) {

    class Holder(val b: ItemBadgeTileBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemBadgeTileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val s = getItem(position)
        val b = holder.b
        val ctx = b.root.context
        b.ivArt.setImageResource(s.def.iconRes)
        b.tvName.text = s.def.title
        b.ivArt.colorFilter = if (s.unlocked) null else Badges.greyscale
        b.ivArt.alpha = if (s.unlocked) 1f else 0.45f
        b.ivLock.visibility = if (s.unlocked) View.GONE else View.VISIBLE
        b.dotNew.visibility = if (s.claimable) View.VISIBLE else View.GONE
        b.root.contentDescription = ctx.getString(
            when {
                s.claimable -> R.string.badge_cd_ready
                s.unlocked -> R.string.badge_cd_earned
                else -> R.string.badge_cd_locked
            },
            s.def.title
        )
        b.root.setOnClickListener { onTap(s) }
    }

    private object Diff : DiffUtil.ItemCallback<BadgeState>() {
        override fun areItemsTheSame(a: BadgeState, b: BadgeState) = a.def == b.def
        override fun areContentsTheSame(a: BadgeState, b: BadgeState) = a == b
    }
}

/** The badge sheet and the "Badge unlocked" snackbar. */
object Badges {

    val greyscale = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })

    /** Badge sheet: how to earn it, and Claim when it's unlocked and unclaimed. */
    fun showSheet(activity: Activity, uid: String, s: BadgeState) {
        if (activity.isFinishing || activity.isDestroyed) return
        val sheet = BottomSheetDialog(activity)
        val b = SheetBadgeBinding.inflate(LayoutInflater.from(activity))
        b.ivArt.setImageResource(s.def.iconRes)
        b.ivArt.colorFilter = if (s.unlocked) null else greyscale
        b.ivArt.alpha = if (s.unlocked) 1f else 0.45f
        b.tvName.text = s.def.title
        b.tvHowTo.text = activity.getString(s.def.howToRes)
        when {
            s.claimable -> {
                b.tvStatus.visibility = View.GONE
                b.btnClaim.visibility = View.VISIBLE
                b.btnClaim.text = activity.getString(R.string.mission_claim, s.def.xp)
                b.btnClaim.setOnClickListener {
                    b.btnClaim.isEnabled = false
                    BadgeRepository.claim(uid, s) { award ->
                        if (activity.isFinishing || activity.isDestroyed) return@claim
                        if (award == null) {
                            b.btnClaim.isEnabled = true
                            Toast.makeText(activity, R.string.k_could_not_claim, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(activity, activity.getString(R.string.k_plus_level_points, award.amount), Toast.LENGTH_SHORT).show()
                            sheet.dismiss()
                        }
                    }
                }
            }
            s.claimed -> b.tvStatus.text = activity.getString(
                R.string.badge_earned_on,
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(s.unlockedAt))
            )
            else -> b.tvStatus.text = activity.getString(R.string.mission_xp, s.def.xp)
        }
        sheet.setContentView(b.root)
        sheet.show()
    }

    /**
     * Shows "Badge unlocked: Hantu Malam · See" whenever a badge unlocks while [activity] is open.
     * The first read is the starting point, so badges unlocked earlier don't pop up again.
     * Stop it with [stopWatching] in onDestroy.
     */
    fun watchUnlocks(activity: Activity, uid: String, anchor: View): ValueEventListener? {
        if (uid.isBlank()) return null
        var known: Set<String>? = null
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val states = BadgeRepository.stateFrom(snapshot)
                val now = states.filter { it.unlocked }.map { it.def.id }.toSet()
                val before = known
                known = now
                if (before == null || activity.isFinishing || activity.isDestroyed) return
                announce(activity, uid, anchor, states.filter { it.unlocked && it.def.id !in before })
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        FirebaseDatabase.getInstance(AppConfig.DATABASE_URL).reference.child("badges").child(uid).addValueEventListener(l)
        return l
    }

    /** One Snackbar per new badge, each after the last is gone, so a scan that unlocks two shows both. */
    private fun announce(activity: Activity, uid: String, anchor: View, unlocked: List<BadgeState>) {
        val s = unlocked.firstOrNull() ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        Snackbar.make(anchor, activity.getString(R.string.badge_unlocked_toast, s.def.title), Snackbar.LENGTH_LONG)
            .setAction(R.string.badge_see) { showSheet(activity, uid, s) }
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(bar: Snackbar, event: Int) = announce(activity, uid, anchor, unlocked.drop(1))
            })
            .show()
    }

    fun stopWatching(uid: String, listener: ValueEventListener?) = BadgeRepository.stopListening(uid, listener)
}
