package com.sapotlokal.util

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.database.FirebaseDatabase
import com.sapotlokal.AppConfig
import com.sapotlokal.R
import com.sapotlokal.data.model.AvatarDef

/** Shows an avatar on screen and lets a customer choose one. */
object AvatarUi {

    /**
     * If [avatarId] is a real avatar: show it filling the [image] (hiding the [initials] text) and
     * return true. Otherwise change nothing and return false, so the caller's initials fallback stays.
     */
    fun bind(image: ImageView, initials: TextView?, avatarId: String?, insetDp: Int = 0): Boolean {
        val avatar = AvatarDef.fromId(avatarId) ?: return false
        image.imageTintList = null            // the placeholder icon was tinted; the avatar must not be
        image.scaleType = ImageView.ScaleType.FIT_CENTER
        val lp = image.layoutParams
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT
        (lp as? android.widget.FrameLayout.LayoutParams)?.gravity = Gravity.CENTER
        image.layoutParams = lp
        val inset = (insetDp * image.resources.displayMetrics.density).toInt()
        image.setPadding(inset, inset, inset, inset)
        image.requestLayout()
        image.setImageResource(avatar.drawableRes)
        image.visibility = View.VISIBLE
        initials?.visibility = View.GONE
        return true
    }

    /** Opens the picker. [onChosen] gets the new id, or null when the customer removes their avatar. */
    fun showPicker(activity: Activity, currentId: String?, onChosen: (String?) -> Unit) {
        val density = activity.resources.displayMetrics.density
        fun px(dp: Int) = (dp * density).toInt()

        val grid = GridLayout(activity).apply {
            columnCount = 3
            setPadding(px(16), px(8), px(16), 0)
        }

        var dialog: androidx.appcompat.app.AlertDialog? = null
        AvatarDef.entries.forEach { def ->
            val selected = def.id == currentId
            val cell = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(px(6), px(6), px(6), px(6))
                layoutParams = GridLayout.LayoutParams().apply {
                    width = px(92); height = GridLayout.LayoutParams.WRAP_CONTENT
                    setMargins(px(2), px(4), px(2), px(4))
                }
            }
            val img = ImageView(activity).apply {
                setImageResource(def.drawableRes)
                layoutParams = LinearLayout.LayoutParams(px(72), px(72))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(android.graphics.Color.TRANSPARENT)
                    setStroke(
                        px(if (selected) 3 else 0),
                        ContextCompat.getColor(activity, R.color.hu_ink)
                    )
                }
                contentDescription = def.label
            }
            val label = TextView(activity).apply {
                text = def.label
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(activity, R.color.hu_soft))
            }
            cell.addView(img)
            cell.addView(label)
            cell.setOnClickListener {
                onChosen(def.id)
                dialog?.dismiss()
            }
            grid.addView(cell)
        }

        dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(com.sapotlokal.R.string.k_choose_an_avatar))
            .setView(grid)
            .setNeutralButton(activity.getString(com.sapotlokal.R.string.k_remove_avatar)) { _, _ -> onChosen(null) }
            .setNegativeButton(activity.getString(com.sapotlokal.R.string.k_cancel), null)
            .show()
    }

    /** Saves the choice (or clears it) on the signed-in user's record. */
    fun save(uid: String, avatarId: String?, onDone: (Boolean) -> Unit = {}) {
        val ref = FirebaseDatabase.getInstance(AppConfig.DATABASE_URL).reference
            .child("users").child(uid).child("avatar")
        val task = if (avatarId == null) ref.removeValue() else ref.setValue(avatarId)
        task.addOnCompleteListener { onDone(it.isSuccessful) }
    }
}
