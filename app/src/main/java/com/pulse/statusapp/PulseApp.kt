package com.pulse.statusapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.pulse.statusapp.data.PulseClient

class PulseApp : Application() {

    override fun onCreate() {
        super.onCreate()
        PulseClient.init(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val messages = NotificationChannel(
                CHANNEL_MESSAGES,
                getString(R.string.notif_channel_messages),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "New direct messages"
                enableVibration(true)
            }
            val statuses = NotificationChannel(
                CHANNEL_STATUSES,
                getString(R.string.notif_channel_statuses),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Friend status updates"
            }
            manager.createNotificationChannel(messages)
            manager.createNotificationChannel(statuses)
        }
    }

    companion object {
        const val CHANNEL_MESSAGES = "messages"
        const val CHANNEL_STATUSES = "statuses"
    }
}
