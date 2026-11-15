package com.sapotlokal.util

import android.app.Activity
import android.content.Intent
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.sapotlokal.AppConfig
import com.sapotlokal.ui.auth.LoginActivity

/**
 * AccountActions — change password, log out and delete account for a CUSTOMER.
 * (Shop owners have their own version that also removes the shop.)
 * Passwords are only ever typed into Firebase's own re-authentication; they are never stored or logged.
 */
object AccountActions {

    private fun field(activity: Activity, hint: String) = EditText(activity).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    private fun form(activity: Activity, vararg fields: EditText): LinearLayout {
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            fields.forEach { addView(it) }
        }
    }

    private fun toast(activity: Activity, msg: String) =
        Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()

    /** Firebase only allows sensitive changes shortly after a fresh sign-in, so confirm the password first. */
    private fun reauthenticate(activity: Activity, password: String, onSuccess: () -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        val email = user?.email
        if (user == null || email.isNullOrBlank() || password.isEmpty()) {
            toast(activity, activity.getString(com.sapotlokal.R.string.k_enter_password))
            return
        }
        user.reauthenticate(EmailAuthProvider.getCredential(email, password))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { toast(activity, activity.getString(com.sapotlokal.R.string.k_password_wrong)) }
    }

    fun showChangePassword(activity: Activity) {
        val current = field(activity, activity.getString(com.sapotlokal.R.string.k_current_password))
        val newer = field(activity, activity.getString(com.sapotlokal.R.string.k_new_password))
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(com.sapotlokal.R.string.k_change_password))
            .setView(form(activity, current, newer))
            .setPositiveButton(activity.getString(com.sapotlokal.R.string.k_save)) { _, _ ->
                val newPw = newer.text.toString()
                if (newPw.length < 6) {
                    toast(activity, activity.getString(com.sapotlokal.R.string.k_new_password_short))
                    return@setPositiveButton
                }
                reauthenticate(activity, current.text.toString()) {
                    FirebaseAuth.getInstance().currentUser?.updatePassword(newPw)
                        ?.addOnSuccessListener { toast(activity, activity.getString(com.sapotlokal.R.string.k_password_changed)) }
                        ?.addOnFailureListener { toast(activity, activity.getString(com.sapotlokal.R.string.k_password_change_failed)) }
                }
            }
            .setNegativeButton(activity.getString(com.sapotlokal.R.string.k_cancel), null)
            .show()
    }

    fun confirmLogout(activity: Activity) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(com.sapotlokal.R.string.k_log_out_q))
            .setPositiveButton(activity.getString(com.sapotlokal.R.string.k_log_out)) { _, _ ->
                SessionManager.signOut(activity)
                goToLogin(activity)
            }
            .setNegativeButton(activity.getString(com.sapotlokal.R.string.k_cancel), null)
            .show()
    }

    fun showDeleteAccount(activity: Activity) {
        val password = field(activity, activity.getString(com.sapotlokal.R.string.k_your_password))
        MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(com.sapotlokal.R.string.k_delete_account_q))
            .setMessage(activity.getString(com.sapotlokal.R.string.k_delete_account_msg))
            .setView(form(activity, password))
            .setPositiveButton(activity.getString(com.sapotlokal.R.string.k_delete)) { _, _ ->
                reauthenticate(activity, password.text.toString()) { deleteEverything(activity) }
            }
            .setNegativeButton(activity.getString(com.sapotlokal.R.string.k_cancel), null)
            .show()
    }

    private fun deleteEverything(activity: Activity) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val uid = user.uid
        val db = FirebaseDatabase.getInstance(AppConfig.DATABASE_URL).reference

        // Own records that the security rules let a customer remove. The points ledger is
        // append-only by design, so it stays (it is what shops' totals are built from).
        val removals = hashMapOf<String, Any?>(
            "users/$uid" to null,
            "xpEvents/$uid" to null,
            "userStats/$uid" to null,
            "missionProgress/$uid" to null,
            "badges/$uid" to null
        )
        db.updateChildren(removals)
            .addOnSuccessListener {
                user.delete()
                    .addOnSuccessListener {
                        SessionManager.signOut(activity)
                        goToLogin(activity)
                    }
                    .addOnFailureListener {
                        toast(activity, activity.getString(com.sapotlokal.R.string.k_login_not_deleted))
                    }
            }
            .addOnFailureListener { toast(activity, activity.getString(com.sapotlokal.R.string.k_account_not_deleted)) }
    }

    private fun goToLogin(activity: Activity) {
        activity.startActivity(
            Intent(activity, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }
}
