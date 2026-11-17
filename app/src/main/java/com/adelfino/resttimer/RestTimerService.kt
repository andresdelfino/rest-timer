package com.adelfino.resttimer

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.annotation.StringRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
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

    private val openActivityIntent by lazy {
        PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            Intent(this, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
    private val resumeIntent by lazy { servicePendingIntent(ACTION_RESUME, REQUEST_RESUME) }
    private val cancelIntent by lazy { servicePendingIntent(ACTION_CANCEL, REQUEST_CANCEL) }
    private val elapsedAlarm by lazy { alarmOperation(ACTION_ELAPSED) }
    private val timeoutAlarm by lazy { alarmOperation(ACTION_CANCEL) }

    private var countdownJob: Job? = null

    /** Tracks the foreground notification so a start command never posts a stray second one. */
    private var inForeground = false

    /** Guards the alert against being started twice by two arrivals of the same deadline. */
    private var alerting = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Keep the notification honest when the set count changes mid-rest. Reps are not shown in
        // it, so a rep change alone is not worth reposting it.
        scope.launch {
            repository.state
                .distinctUntilChangedBy { it.copy(reps = 0) }
                .collect { state -> if (state.phase != Phase.IDLE) notify(state) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every route in reaches us through startForegroundService(), which must be answered with a
        // notification before the service does anything else - including stopping.
        if (!inForeground) {
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
        if (repository.startRest()) armCountdown() else finish(repository::cancelRest)
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
        scheduleAlarm(elapsedAlarm, state.deadlineRealtime)

        countdownJob?.cancel()
        countdownJob = scope.launch {
            delay(state.deadlineRealtime - SystemClock.elapsedRealtime())
            elapse()
        }
    }

    private fun disarmCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        alarmManager.cancel(elapsedAlarm)
    }

    private fun elapse() {
        if (alerting) return
        repository.elapse()
        if (repository.state.value.phase != Phase.ELAPSED) {
            // Nothing was resting: a stale alarm, or a rest too old to be worth alerting about.
            finish(repository::cancelRest)
            return
        }
        alerting = true
        disarmCountdown()

        // The alert has to live on the high-importance channel to reach the lock screen, and a
        // notification cannot change channel in place, so the foreground notification moves to a
        // second id and the countdown one is retired.
        enterForeground(NOTIFICATION_ALERT, buildNotification(repository.state.value))
        notificationManager.cancel(NOTIFICATION_COUNTDOWN)

        startVibrating()
        // Nobody should come back to a phone that has been buzzing since they left the gym.
        scheduleAlarm(timeoutAlarm, SystemClock.elapsedRealtime() + ALERT_TIMEOUT_MILLIS)
    }

    private fun finish(apply: () -> Unit) {
        apply()
        disarmCountdown()
        alarmManager.cancel(timeoutAlarm)
        stopVibrating()
        alerting = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        inForeground = false
        notificationManager.cancel(NOTIFICATION_COUNTDOWN)
        notificationManager.cancel(NOTIFICATION_ALERT)
        stopSelf()
    }

    private fun enterForeground(id: Int, notification: Notification) {
        startForeground(id, notification)
        inForeground = true
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
    private fun startVibrating() = vibrator.vibrate(
        VibrationEffect.createWaveform(VIBRATION_PATTERN, VIBRATION_REPEAT_INDEX),
        VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
    )

    private fun stopVibrating() = vibrator.cancel()

    // --- Alarms ----------------------------------------------------------------------------

    /** Always exact: USE_EXACT_ALARM is granted at install and the user can't revoke it. */
    // Lint knows only SCHEDULE_EXACT_ALARM and can't tell that USE_EXACT_ALARM covers this call.
    @SuppressLint("MissingPermission")
    private fun scheduleAlarm(operation: PendingIntent, triggerRealtime: Long) =
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerRealtime,
            operation,
        )

    /** Fires [RestAlarmReceiver], which hands [action] on to this service. */
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

        val builder = Notification.Builder(
            this,
            if (alerting) RestTimerApplication.CHANNEL_ALERT else RestTimerApplication.CHANNEL_COUNTDOWN,
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(getColor(R.color.accent))
            .setContentIntent(openActivityIntent)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setShowWhen(false)

        return if (alerting) {
            builder
                .setContentTitle(getString(R.string.notification_elapsed_title))
                .setContentText(
                    if (state.hasNextSet) {
                        getString(R.string.notification_elapsed_text, state.nextSet, state.sets)
                    } else {
                        getString(R.string.notification_done_text)
                    },
                )
                .setFullScreenIntent(openActivityIntent, true)
                .addAction(action(R.string.action_resume, resumeIntent))
                .build()
        } else {
            builder
                .setContentTitle(getString(R.string.notification_resting_title))
                .setStyle(restingMetrics(state))
                // A Live Update: pinned to the top of the shade and the lock screen, with the
                // countdown in a status-bar chip. The user can turn this off per app, which leaves
                // an ordinary notification.
                .setRequestPromotedOngoing(true)
                .addAction(
                    action(R.string.action_cancel, cancelIntent, Notification.Action.SEMANTIC_ACTION_STOP),
                )
                .build()
        }
    }

    /**
     * The countdown and the set it leads to. The countdown comes first, which makes it the metric
     * the status-bar chip shows. It counts down to the rest's own monotonic deadline, and the
     * platform ticks it, so the service never wakes to redraw it.
     */
    private fun restingMetrics(state: TimerState) = Notification.MetricStyle()
        .addMetric(
            Notification.Metric(
                Notification.Metric.TimeDifference.forTimer(
                    state.deadlineRealtime,
                    Notification.Metric.TimeDifference.FORMAT_CHRONOMETER,
                ),
                getString(R.string.notification_metric_remaining),
            ),
        )
        .addMetric(
            Notification.Metric(
                Notification.Metric.FixedText(
                    getString(R.string.notification_metric_set_value, state.currentSet, state.sets),
                ),
                getString(R.string.notification_metric_set),
            ),
        )

    /** No icon: the system doesn't draw one on notification action buttons. */
    private fun action(
        @StringRes title: Int,
        intent: PendingIntent,
        semanticAction: Int = Notification.Action.SEMANTIC_ACTION_NONE,
    ): Notification.Action =
        Notification.Action.Builder(null, getString(title), intent)
            .setSemanticAction(semanticAction)
            .build()

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getForegroundService(
            this,
            requestCode,
            intent(this, action),
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

        /** Leading 0 is the delay before the first buzz; the trailing 1000 is the gap before repeating. */
        private val VIBRATION_PATTERN = longArrayOf(0, 400, 200, 400, 1000)
        private const val VIBRATION_REPEAT_INDEX = 0

        private fun intent(context: Context, action: String): Intent =
            Intent(context, RestTimerService::class.java).setAction(action)

        /**
         * Always a foreground start: it is legal whether or not the service is already running,
         * and [onStartCommand] answers it with a notification either way.
         */
        fun send(context: Context, action: String) {
            context.startForegroundService(intent(context, action))
        }
    }
}
