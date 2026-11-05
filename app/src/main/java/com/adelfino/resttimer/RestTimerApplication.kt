package com.adelfino.resttimer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.annotation.StringRes

class RestTimerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                // Low importance: the countdown is glanceable, not something to interrupt for.
                channel(
                    CHANNEL_COUNTDOWN,
                    R.string.channel_countdown_name,
                    R.string.channel_countdown_description,
                    NotificationManager.IMPORTANCE_LOW,
                ),
                // High importance so it can take over the lock screen.
                channel(
                    CHANNEL_ALERT,
                    R.string.channel_alert_name,
                    R.string.channel_alert_description,
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            ),
        )
    }

    /** Both channels are silent: the vibration is driven by the service itself. */
    private fun channel(
        id: String,
        @StringRes name: Int,
        @StringRes description: Int,
        importance: Int,
    ) = NotificationChannel(id, getString(name), importance).apply {
        this.description = getString(description)
        setShowBadge(false)
        enableVibration(false)
        setSound(null, null)
    }

    companion object {
        const val CHANNEL_COUNTDOWN = "rest_countdown"
        const val CHANNEL_ALERT = "rest_alert"
    }
}
