package com.adelfino.resttimer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class RestTimerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)

        // Silent and unobtrusive: the countdown is glanceable, not something to interrupt for.
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_COUNTDOWN,
                getString(R.string.channel_countdown_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.channel_countdown_description)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            },
        )

        // High importance so it can take over the lock screen. The vibration is driven by the
        // service itself, so the channel keeps its own alerting off.
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT,
                getString(R.string.channel_alert_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = getString(R.string.channel_alert_description)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )
    }

    companion object {
        const val CHANNEL_COUNTDOWN = "rest_countdown"
        const val CHANNEL_ALERT = "rest_alert"
    }
}
