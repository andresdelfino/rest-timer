package com.adelfino.resttimer

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs

/**
 * The single source of truth for the workout, shared by the UI and [RestTimerService].
 *
 * All of it survives process death in shared preferences, a rest in flight included. The alarm
 * that ends a rest is held by the system and outlives the process on its own, so recording enough
 * to rebuild the rest around it is what lets a rest still land after the app has been killed.
 */
class TimerRepository private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(restore())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private fun restore(): TimerState {
        val sets = prefs.getInt(KEY_SETS, DEFAULT_SETS).coerceIn(MIN_COUNT, MAX_COUNT)
        val deadline = prefs.getLong(KEY_DEADLINE, 0L)
        return TimerState(
            sets = sets,
            reps = prefs.getInt(KEY_REPS, DEFAULT_REPS).coerceIn(MIN_COUNT, MAX_COUNT),
            currentSet = prefs.getInt(KEY_CURRENT_SET, MIN_COUNT).coerceIn(MIN_COUNT, sets),
            phase = restorePhase(deadline),
            deadlineRealtime = deadline,
        )
    }

    /**
     * A stored deadline is a reading on the monotonic clock, which only means anything within the
     * boot that took it. The boot instant recorded alongside it says whether that is still this
     * boot: across a restart it moves by the whole of the previous uptime, and a rest measured
     * against a clock that has since restarted from zero is simply gone.
     */
    private fun restorePhase(deadline: Long): Phase {
        if (deadline <= 0L) return Phase.IDLE
        val storedBootInstant = prefs.getLong(KEY_BOOT_INSTANT, 0L)
        if (abs(bootInstant() - storedBootInstant) > BOOT_INSTANT_TOLERANCE_MILLIS) return Phase.IDLE

        val overdueBy = SystemClock.elapsedRealtime() - deadline
        return when {
            overdueBy < 0L -> Phase.RESTING
            // Elapsed while the app was gone, and recently enough that the user is still resting.
            overdueBy <= STALE_ALERT_MILLIS -> Phase.ELAPSED
            else -> Phase.IDLE
        }
    }

    private fun persist(state: TimerState) {
        prefs.edit {
            putInt(KEY_SETS, state.sets)
            putInt(KEY_REPS, state.reps)
            putInt(KEY_CURRENT_SET, state.currentSet)
            if (state.phase == Phase.IDLE) {
                remove(KEY_DEADLINE)
                remove(KEY_BOOT_INSTANT)
            } else {
                putLong(KEY_DEADLINE, state.deadlineRealtime)
                putLong(KEY_BOOT_INSTANT, bootInstant())
            }
        }
    }

    /** The wall-clock moment this boot started, which is stable for as long as the boot lasts. */
    private fun bootInstant(): Long = System.currentTimeMillis() - SystemClock.elapsedRealtime()

    fun setSets(value: Int) = update {
        val sets = value.coerceIn(it.setsRange)
        it.copy(sets = sets, currentSet = it.currentSet.coerceAtMost(sets))
    }

    fun setReps(value: Int) = update { it.copy(reps = value.coerceIn(it.repsRange)) }

    fun setCurrentSet(value: Int) = update {
        if (it.phase != Phase.IDLE) it else it.copy(currentSet = value.coerceIn(it.currentSetRange))
    }

    /** @return the deadline of the started rest, or `null` if no rest could start. */
    fun startRest(): Long? {
        val deadline = SystemClock.elapsedRealtime() + REST_SECONDS * 1000L
        var started = false
        update {
            if (!it.canRest) return@update it
            started = true
            it.copy(phase = Phase.RESTING, deadlineRealtime = deadline)
        }
        return deadline.takeIf { started }
    }

    /** @return `true` if this call is the one that ended the countdown. */
    fun elapse(): Boolean {
        var elapsed = false
        update {
            if (it.phase != Phase.RESTING) return@update it
            elapsed = true
            it.copy(phase = Phase.ELAPSED)
        }
        return elapsed
    }

    /** Acknowledges the alert and advances to the next set. */
    fun resume() = update {
        if (it.phase != Phase.ELAPSED) return@update it
        it.copy(
            phase = Phase.IDLE,
            deadlineRealtime = 0L,
            currentSet = (it.currentSet + 1).coerceAtMost(it.sets),
        )
    }

    /** Abandons a rest or an alert without advancing the set counter. */
    fun cancelRest() = update { it.copy(phase = Phase.IDLE, deadlineRealtime = 0L) }

    /** Takes the workout back to its first set, abandoning any rest in flight. */
    fun startAgain() = update {
        it.copy(phase = Phase.IDLE, deadlineRealtime = 0L, currentSet = MIN_COUNT)
    }

    private inline fun update(crossinline transform: (TimerState) -> TimerState) {
        _state.update { current ->
            transform(current).also { if (it != current) persist(it) }
        }
    }

    companion object {
        private const val PREFS = "rest_timer"
        private const val KEY_SETS = "sets"
        private const val KEY_REPS = "reps"
        private const val KEY_CURRENT_SET = "current_set"
        private const val KEY_DEADLINE = "deadline_realtime"
        private const val KEY_BOOT_INSTANT = "boot_instant"

        /** Slack for the wall clock being nudged by a time sync part-way through a rest. */
        private const val BOOT_INSTANT_TOLERANCE_MILLIS = 60_000L

        /** How long after a missed deadline it is still worth alerting rather than giving up. */
        private const val STALE_ALERT_MILLIS = 5 * 60 * 1000L

        @Volatile
        private var instance: TimerRepository? = null

        fun get(context: Context): TimerRepository =
            instance ?: synchronized(this) {
                instance ?: TimerRepository(context).also { instance = it }
            }
    }
}
