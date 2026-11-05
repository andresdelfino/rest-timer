package com.adelfino.resttimer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Wakes [RestTimerService] from an exact alarm. Exact alarms are delivered even in Doze and grant a
 * short window in which a foreground service may be started, so this works whether or not the
 * service survived until the deadline. The alarms carry the service's own actions, which are simply
 * passed on.
 */
class RestAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        RestTimerService.send(context, intent.action ?: return)
    }
}
