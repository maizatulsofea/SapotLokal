package com.sapotlokal.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * SapotFcmService — Firebase Cloud Messaging receiver.
 *
 * WHY IT EXISTS NOW: The AndroidManifest registers this service so the app is
 * correctly wired for FCM from day one. Phase 6 will implement the actual
 * notification display logic for level-ups, challenge completions, and
 * "your reward is ready" alerts.
 *
 * For now it just logs incoming messages so we know FCM is connected.
 */
class SapotFcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Phase 6: save the token to the user's database record so the server
        // can send targeted pushes. For now, do nothing.
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // Phase 6: parse message.data or message.notification and show a
        // system notification. For now, do nothing.
    }
}
