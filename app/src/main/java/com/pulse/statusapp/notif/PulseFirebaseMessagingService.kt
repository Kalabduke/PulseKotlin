package com.pulse.statusapp.notif

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pulse.statusapp.MainActivity
import com.pulse.statusapp.PulseApp
import com.pulse.statusapp.R
import com.pulse.statusapp.data.NotificationsRepository
import com.pulse.statusapp.data.PulseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles FCM push for DMs and status updates.
 *
 * The notify-friends edge function sends data-only messages so we can decide
 * locally whether to show a heads-up notification (respecting in-app focus).
 */
class PulseFirebaseMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token registered")
        scope.launch {
            runCatching {
                val userId = PulseClient.supabase.auth.currentSessionOrNull()?.user?.id ?: return@launch
                NotificationsRepository().registerFcmToken(userId, token)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data

        when (data["type"]) {
            "dm" -> showDmNotification(
                title = data["title"] ?: "New message",
                body = data["body"] ?: "",
                friendId = data["friend_id"],
            )
            "status" -> showStatusNotification(
                title = data["title"] ?: "Status update",
                body = data["body"] ?: "",
                friendId = data["friend_id"],
            )
            else -> {
                // Fallback: plain notification payload
                message.notification?.let {
                    showDmNotification(it.title ?: "Pulse", it.body ?: "")
                }
            }
        }
    }

    private fun showDmNotification(title: String, body: String, friendId: String? = null) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            friendId?.let { putExtra("open_friend_id", it) }
        }
        val pending = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = android.app.Notification.Builder(this, PulseApp.CHANNEL_MESSAGES)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_stat_pulse)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(android.app.Notification.PRIORITY_HIGH)
            .build()

        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(NOTIF_ID_DM, notification)
    }

    private fun showStatusNotification(title: String, body: String, friendId: String? = null) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt() + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = android.app.Notification.Builder(this, PulseApp.CHANNEL_STATUSES)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_stat_pulse)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(NOTIF_ID_STATUS, notification)
    }

    companion object {
        private const val TAG = "PulseFCM"
        private const val NOTIF_ID_DM = 1001
        private const val NOTIF_ID_STATUS = 1002
    }
}
