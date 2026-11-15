package com.sapotlokal

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * SapotApp — applies safe-area padding to EVERY screen.
 *
 * Why: the app targets Android 15+, which draws apps edge-to-edge. Without this, screen titles
 * and bottom buttons slide under the status bar / gesture bar (seen on the emulator: the
 * "Shop Overview" title printed over the clock). Doing it once here fixes all screens instead
 * of editing each layout.
 */
class SapotApp : Application() {

    override fun onCreate() {
        super.onCreate()
        com.sapotlokal.util.ServerClock.start()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
                if (content.getTag(TAG_APPLIED) == true) return
                content.setTag(TAG_APPLIED, true)

                ViewCompat.setOnApplyWindowInsetsListener(content) { v: View, insets: WindowInsetsCompat ->
                    val bars = insets.getInsets(
                        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                    )
                    val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
                    v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
                    WindowInsetsCompat.CONSUMED
                }
                ViewCompat.requestApplyInsets(content)
            }

            override fun onActivityCreated(a: Activity, s: Bundle?) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, o: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }

    private companion object {
        val TAG_APPLIED = R.id.sapot_insets_applied
    }
}
