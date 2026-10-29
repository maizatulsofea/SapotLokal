package com.sapotlokal.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sapotlokal.R

/**
 * SapotFcmService — Firebase Cloud Messaging receiver.
 *
 * Handles two FCM lifecycle events:
 *
 * **[onNewToken]** — Called whenever FCM issues a fresh registration token for this
 * device/app installation. The token is persisted to `/users/{uid}/fcmToken` in
 * Firebase Realtime Database so the backend can address push notifications to the
 * currently signed-in user. If no user is signed in the token is silently discarded
 * (it will be re-saved on the next sign-in or the next FCM token refresh).
 *
 * **[onMessageReceived]** — Called for every incoming FCM message while the app is
 * in the foreground (or when the message carries only a data payload). Displays a
 * system notification on the appropriate channel:
 * - `channel_rewards` — low-urgency reward and voucher alerts (IMPORTANCE_DEFAULT)
 * - `channel_levels`  — high-urgency level-up congratulations (IMPORTANCE_HIGH)
 *
 * The channel is selected via the `channel` key in `remoteMessage.data`. The
 * notification title/body are taken from the FCM notification object first, with the
 * data payload used as a fallback so the service works with both notification and
 * data-only messages.
 *
 * Notification channels are created lazily on every call via [createNotificationChannels];
 * the OS de-duplicates channel creation so this is safe and cheap.
 *
 * On Android 13+ (API 33) the POST_NOTIFICATIONS runtime permission is checked before
 * attempting to post — if the permission has not been granted the notification is
 * silently dropped.
 */
class SapotFcmService : FirebaseMessagingService() {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    companion object {
        private const val TAG = "SapotFcmService"

        /** Notification channel ID for reward and voucher alerts. */
        const val CHANNEL_REWARDS = "channel_rewards"

        /** Notification channel ID for level-up congratulations. */
        const val CHANNEL_LEVELS = "channel_levels"

        /**
         * Ensures FCM token is fetched and written to /users/{uid}/fcmToken.
         * Safe to call on sign-in or home activity startup.
         */
        @Suppress("DEPRECATION")
        fun syncTokenForUser(userID: String) {
            if (userID.isBlank()) return
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    if (!token.isNullOrBlank()) {
                        FirebaseDatabase.getInstance(com.sapotlokal.AppConfig.DATABASE_URL)
                            .getReference("users/$userID/fcmToken")
                            .setValue(token)
                            .addOnSuccessListener {
                                Log.d(TAG, "Explicitly synced FCM token for uid=$userID")
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed to retrieve FCM token for uid=$userID", e)
                }
        }

        /**
         * Posts a local notification using the defined channels.
         */
        fun showLocalNotification(
            context: android.content.Context,
            title: String,
            body: String,
            channelId: String = CHANNEL_REWARDS
        ) {
            ensureChannels(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    Log.w(TAG, "POST_NOTIFICATIONS not granted — local notification suppressed")
                    return
                }
            }

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(
                    if (channelId == CHANNEL_LEVELS) NotificationCompat.PRIORITY_HIGH
                    else NotificationCompat.PRIORITY_DEFAULT
                )
                .setAutoCancel(true)
                .build()

            val notificationId = System.currentTimeMillis().toInt()
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }

        fun ensureChannels(context: android.content.Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return

            val rewardsChannel = NotificationChannel(
                CHANNEL_REWARDS,
                context.getString(R.string.notif_channel_rewards_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_rewards_desc)
            }

            val levelsChannel = NotificationChannel(
                CHANNEL_LEVELS,
                context.getString(R.string.notif_channel_levels_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_levels_desc)
            }

            manager.createNotificationChannel(rewardsChannel)
            manager.createNotificationChannel(levelsChannel)
        }
    }

    // -------------------------------------------------------------------------
    // FirebaseMessagingService overrides
    // -------------------------------------------------------------------------

    /**
     * Called by FCM whenever a new registration token is generated for this device.
     *
     * Saves the token to `/users/{uid}/fcmToken` in Firebase Realtime Database so the
     * server-side FCM sender can target this specific installation. If no user is
     * currently authenticated the token is discarded silently — it will be saved the
     * next time the token rotates while a user is signed in.
     *
     * @param token The new FCM registration token.
     */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed")

        createNotificationChannels()

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Log.d(TAG, "onNewToken: no authenticated user — token not persisted")
            return
        }

        FirebaseDatabase.getInstance()
            .getReference("users/$uid/fcmToken")
            .setValue(token)
            .addOnSuccessListener {
                Log.d(TAG, "FCM token saved for uid=$uid")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Failed to save FCM token for uid=$uid", e)
            }
    }

    /**
     * Called when an FCM message arrives while the app is in the foreground, or when
     * the message is a data-only payload (no notification object).
     *
     * Resolves the title and body from the FCM notification object, falling back to the
     * `title` / `body` keys in the data payload. Selects the notification channel based
     * on the `channel` key in the data payload, then posts the notification if the
     * POST_NOTIFICATIONS permission is granted (Android 13+) or unconditionally on
     * older API levels.
     *
     * @param remoteMessage The incoming FCM message.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Message received from: ${remoteMessage.from}")

        createNotificationChannels()

        // Resolve title and body — prefer the FCM notification object, fall back to data.
        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: return // Nothing to display — skip silently.

        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: ""

        // Select channel; default to rewards unless explicitly set to levels.
        val channelId = if (remoteMessage.data["channel"] == CHANNEL_LEVELS) {
            CHANNEL_LEVELS
        } else {
            CHANNEL_REWARDS
        }

        showNotification(title, body, channelId)
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Ensures both notification channels exist. Safe to call repeatedly — the OS
     * no-ops duplicate channel creation.
     *
     * Must be called before any notification is posted and before [onNewToken] returns
     * so that channels are ready even if the first notification arrives very quickly.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java) ?: return

        val rewardsChannel = NotificationChannel(
            CHANNEL_REWARDS,
            getString(R.string.notif_channel_rewards_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notif_channel_rewards_desc)
        }

        val levelsChannel = NotificationChannel(
            CHANNEL_LEVELS,
            getString(R.string.notif_channel_levels_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notif_channel_levels_desc)
        }

        manager.createNotificationChannel(rewardsChannel)
        manager.createNotificationChannel(levelsChannel)
        Log.d(TAG, "Notification channels ensured")
    }

    /**
     * Constructs and posts a system notification.
     *
     * On Android 13+ (API 33) the POST_NOTIFICATIONS runtime permission is checked; if
     * it has not been granted the notification is silently dropped rather than crashing.
     *
     * @param title     The notification title.
     * @param body      The notification body text.
     * @param channelId The notification channel to post to.
     */
    private fun showNotification(title: String, body: String, channelId: String) {
        // Android 13+ requires POST_NOTIFICATIONS at runtime.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.w(TAG, "POST_NOTIFICATIONS not granted — notification suppressed")
                return
            }
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(
                if (channelId == CHANNEL_LEVELS) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setAutoCancel(true)
            .build()

        val notificationId = System.currentTimeMillis().toInt()
        NotificationManagerCompat.from(this).notify(notificationId, notification)
        Log.d(TAG, "Notification posted: id=$notificationId channel=$channelId title=\"$title\"")
    }
}
