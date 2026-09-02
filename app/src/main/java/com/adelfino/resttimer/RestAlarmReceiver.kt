package com.adelfino.resttimer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Wakes [RestTimerService] from an exact alarm. Exact alarms are delivered even in Doze and grant a
 * short window in which a foreground service may be started, so this works whether or not the
 * service survived until the deadline.
 */
class RestAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val serviceAction = when (intent.action) {
            ACTION_ELAPSED -> RestTimerService.ACTION_ELAPSED
            ACTION_ALERT_TIMEOUT -> RestTimerService.ACTION_CANCEL
            else -> return
        }
        ContextCompat.startForegroundService(
            context,
            Intent(context, RestTimerService::class.java).setAction(serviceAction),
        )
    }

    companion object {
        const val ACTION_ELAPSED = "com.adelfino.resttimer.alarm.ELAPSED"
        const val ACTION_ALERT_TIMEOUT = "com.adelfino.resttimer.alarm.ALERT_TIMEOUT"
    }
}
