package com.sapotlokal.ui.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.sapotlokal.ui.customer.CustomerHomeActivity
import com.sapotlokal.ui.vendor.VendorHomeActivity

/**
 * Where a signed-in account lands, decided by the `role` stored on `/users/{uid}` at sign-up.
 * Used by the splash (existing session) and the login screen (fresh sign-in).
 */
object RoleRouter {

    fun homeFor(role: String?): Class<out Activity>? = when (role?.lowercase()) {
        "customer" -> CustomerHomeActivity::class.java
        "vendor" -> VendorHomeActivity::class.java
        else -> null
    }

    /** Opens [home] as the only screen on the back stack, so Back can't return to sign-in. */
    fun open(context: Context, home: Class<out Activity>) {
        context.startActivity(
            Intent(context, home).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
    }
}
