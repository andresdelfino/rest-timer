package com.adelfino.resttimer

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns a rest interval from the moment the user taps Rest until they resume.
 *
 * Two independent things end the countdown, and whichever lands first wins:
 *  - a coroutine that sleeps out the remainder, which is exact while the CPU is awake, and
 *  - an exact alarm, which is what actually wakes a dozing device.
 *
 * Running as a foreground service for the whole interval also means the elapse path never has to
 * *start* a foreground service from the background, which the platform would refuse. If the process
 * is killed anyway, the alarm still arrives and finds the rest waiting in [TimerRepository].
 */
class RestTimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository by lazy { TimerRepository.get(this) }
    private val alarmManager by lazy { getSystemService(AlarmManager::class.java) }
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private val vibrator by lazy { getSystemService(VibratorManager::class.java).defaultVibrator }

    private var countdownJob: Job? = null

    /** Tracks the foreground notification so a start command never posts a stray second one. */
    private var foregroundId: Int? = null

    /** Guards the alert against being started twice by two arrivals of the same deadline. */
    private var alerting = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Keep the notification honest when the set or rep counts change mid-rest.
        scope.launch {
            repository.state.collect { state ->
                if (state.phase != Phase.IDLE) notify(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every route in reaches us through startForegroundService(), which must be answered with a
        // notification before the service does anything else - including stopping.
        if (foregroundId == null) {
            val state = repository.state.value
            enterForeground(notificationId(state), buildNotification(state))
        }

        when (intent?.action) {
            ACTION_START -> startRest()
            ACTION_ELAPSED -> elapse()
            ACTION_RESYNC -> resync()
            ACTION_RESUME -> finish(repository::resume)
            ACTION_CANCEL -> finish(repository::cancelRest)
            ACTION_RESTART -> finish(repository::startAgain)
            else -> finish(repository::cancelRest)
        }
        return START_NOT_STICKY
    }

    private fun startRest() {
        if (repository.startRest() == null) {
            finish(repository::cancelRest)
            return
        }
        armCountdown()
    }

    /**
     * Picks up whatever [TimerRepository] restored, which is how a rest that outlived its process
     * gets its notification, its alarm and its alert back.
     */
    private fun resync() = when (repository.state.value.phase) {
        Phase.RESTING -> armCountdown()
        Phase.ELAPSED -> elapse()
        Phase.IDLE -> finish(repository::cancelRest)
    }

    private fun armCountdown() {
        val state = repository.state.value
        enterForeground(NOTIFICATION_COUNTDOWN, buildNotification(state))
        scheduleAlarm(RestAlarmReceiver.ACTION_ELAPSED, state.deadlineRealtime)

        countdownJob?.cancel()
        countdownJob = scope.launch {
            delay(state.deadlineRealtime - SystemClock.elapsedRealtime())
            elapse()
        }
    }

    private fun elapse() {
        if (alerting) return
        if (repository.state.value.phase == Phase.RESTING) repository.elapse()
        if (repository.state.value.phase != Phase.ELAPSED) {
            // Nothing was resting: a stale alarm, or a rest too old to be worth alerting about.
            finish(repository::cancelRest)
            return
        }
        alerting = true
        countdownJob?.cancel()
        countdownJob = null
        cancelAlarm(RestAlarmReceiver.ACTION_ELAPSED)

        // The alert has to live on the high-importance channel to reach the lock screen, and a
        // notification cannot change channel in place, so the foreground notification moves to a
        // second id and the countdown one is retired.
        enterForeground(NOTIFICATION_ALERT, buildNotification(repository.state.value))
        notificationManager.cancel(NOTIFICATION_COUNTDOWN)

        startVibrating()
        // Nobody should come back to a phone that has been buzzing since they left the gym.
        scheduleAlarm(
            RestAlarmReceiver.ACTION_ALERT_TIMEOUT,
            SystemClock.elapsedRealtime() + ALERT_TIMEOUT_MILLIS,
        )
    }

    private fun finish(apply: () -> Unit) {
        apply()
        countdownJob?.cancel()
        countdownJob = null
        cancelAlarm(RestAlarmReceiver.ACTION_ELAPSED)
        cancelAlarm(RestAlarmReceiver.ACTION_ALERT_TIMEOUT)
        stopVibrating()
        alerting = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundId = null
        notificationManager.cancel(NOTIFICATION_COUNTDOWN)
        notificationManager.cancel(NOTIFICATION_ALERT)
        stopSelf()
    }

    private fun enterForeground(id: Int, notification: Notification) {
        startForeground(id, notification)
        foregroundId = id
    }

    override fun onDestroy() {
        stopVibrating()
        scope.cancel()
        super.onDestroy()
    }

    // --- Vibration -------------------------------------------------------------------------

    /**
     * The web app's `navigator.vibrate([400, 200, 400])` on a two second interval, expressed as one
     * repeating waveform so the system vibrator keeps time instead of a wakeful handler in-process.
     */
    private fun startVibrating() {
        val effect = VibrationEffect.createWaveform(VIBRATION_PATTERN, VIBRATION_REPEAT_INDEX)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(
                effect,
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(
                effect,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
    }

    private fun stopVibrating() = vibrator.cancel()

    // --- Alarms ----------------------------------------------------------------------------

    // The exact-alarm branch is guarded by canScheduleExactAlarms() on the line above it, which
    // is the check lint is asking for; it just cannot see through the receiver.
    @SuppressLint("MissingPermission")
    private fun scheduleAlarm(action: String, triggerRealtime: Long) {
        val operation = alarmOperation(action)
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerRealtime,
                operation,
            )
        } else {
            // Without the exact-alarm grant the alert can be deferred in Doze. Still the best
            // available: an inexact wakeup beats no wakeup at all.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerRealtime,
                operation,
            )
        }
    }

    private fun cancelAlarm(action: String) = alarmManager.cancel(alarmOperation(action))

    private fun alarmOperation(action: String): PendingIntent = PendingIntent.getBroadcast(
        this,
        0,
        Intent(this, RestAlarmReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    // --- Notifications ---------------------------------------------------------------------

    private fun notificationId(state: TimerState): Int =
        if (state.phase == Phase.ELAPSED) NOTIFICATION_ALERT else NOTIFICATION_COUNTDOWN

    private fun notify(state: TimerState) =
        notificationManager.notify(notificationId(state), buildNotification(state))

    private fun buildNotification(state: TimerState): Notification {
        val alerting = state.phase == Phase.ELAPSED
        val nextSet = (state.currentSet + 1).coerceAtMost(state.sets)

        val builder = NotificationCompat.Builder(
            this,
            if (alerting) RestTimerApplication.CHANNEL_ALERT else RestTimerApplication.CHANNEL_COUNTDOWN,
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(getColor(R.color.accent))
            .setContentIntent(activityIntent())
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setShowWhen(false)

        return if (alerting) {
            builder
                .setContentTitle(getString(R.string.notification_elapsed_title))
                .setContentText(
                    if (state.currentSet < state.sets) {
                        getString(R.string.notification_elapsed_text, nextSet, state.sets)
                    } else {
                        getString(R.string.notification_done_text)
                    },
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setFullScreenIntent(activityIntent(), true)
                .addAction(
                    0,
                    getString(R.string.action_resume),
                    servicePendingIntent(ACTION_RESUME, REQUEST_RESUME),
                )
                .build()
        } else {
            builder
                .setContentTitle(getString(R.string.notification_resting_title))
                .setContentText(
                    getString(R.string.notification_resting_text, state.currentSet, state.sets),
                )
                .setPriority(NotificationCompat.PRIORITY_LOW)
                // The platform ticks this down itself, so the service never wakes to redraw it.
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(System.currentTimeMillis() + state.remainingMillis())
                .setShowWhen(true)
                .addAction(
                    0,
                    getString(R.string.action_cancel),
                    servicePendingIntent(ACTION_CANCEL, REQUEST_CANCEL),
                )
                .build()
        }
    }

    private fun activityIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_OPEN,
        Intent(this, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getForegroundService(
            this,
            requestCode,
            Intent(this, RestTimerService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val ACTION_START = "com.adelfino.resttimer.START"
        const val ACTION_ELAPSED = "com.adelfino.resttimer.ELAPSED"
        const val ACTION_RESYNC = "com.adelfino.resttimer.RESYNC"
        const val ACTION_RESUME = "com.adelfino.resttimer.RESUME"
        const val ACTION_CANCEL = "com.adelfino.resttimer.CANCEL"
        const val ACTION_RESTART = "com.adelfino.resttimer.RESTART"

        private const val NOTIFICATION_COUNTDOWN = 1
        private const val NOTIFICATION_ALERT = 2

        private const val REQUEST_OPEN = 0
        private const val REQUEST_RESUME = 1
        private const val REQUEST_CANCEL = 2

        private const val ALERT_TIMEOUT_MILLIS = 5 * 60 * 1000L

        /** Leading 0 is the delay before the first buzz; the trailing 1000 is the gap before repeating. */
        private val VIBRATION_PATTERN = longArrayOf(0, 400, 200, 400, 1000)
        private const val VIBRATION_REPEAT_INDEX = 0

        fun send(context: Context, action: String) {
            val intent = Intent(context, RestTimerService::class.java).setAction(action)
            when (action) {
                // These two may find no service running at all.
                ACTION_START, ACTION_RESYNC -> context.startForegroundService(intent)
                else -> context.startService(intent)
            }
        }
    }
}
