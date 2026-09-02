package com.adelfino.resttimer

import android.os.SystemClock
import kotlin.math.ceil

/** Length of a rest interval. The one knob the original page exposed by editing its source. */
const val REST_SECONDS = 120

const val MIN_COUNT = 1
const val MAX_COUNT = 99

const val DEFAULT_SETS = 4
const val DEFAULT_REPS = 10

enum class Phase {
    /** No rest running: the user is working through a set. */
    IDLE,

    /** Counting down towards [TimerState.deadlineRealtime]. */
    RESTING,

    /** The countdown reached zero and the alert is running until the user resumes. */
    ELAPSED,
}

data class TimerState(
    val sets: Int = DEFAULT_SETS,
    val reps: Int = DEFAULT_REPS,
    val currentSet: Int = MIN_COUNT,
    val phase: Phase = Phase.IDLE,
    /**
     * When the running rest is due, on [SystemClock.elapsedRealtime]'s monotonic clock. That clock
     * keeps counting while the device sleeps and is immune to wall-clock changes, so a countdown
     * derived from it cannot drift while the screen is off or the app is backgrounded.
     */
    val deadlineRealtime: Long = 0L,
) {
    /** There is no rest to take after the final set, so resting ends on the second-to-last one. */
    val canRest: Boolean get() = phase == Phase.IDLE && currentSet < sets

    /** Always available while alerting: it is also how the user silences the vibration. */
    val canResume: Boolean get() = phase == Phase.ELAPSED

    /** Starting over is only offered when there is something to undo: a set counted, or a rest. */
    val canStartAgain: Boolean get() = currentSet > MIN_COUNT || phase != Phase.IDLE

    /** The set count can never drop below the set the user is already on. */
    val setsRange: IntRange get() = currentSet..MAX_COUNT

    val repsRange: IntRange get() = MIN_COUNT..MAX_COUNT

    /** Editable between rests only, both to fix a miscount and to start the workout over. */
    val currentSetRange: IntRange get() = if (phase == Phase.IDLE) MIN_COUNT..sets else IntRange.EMPTY

    fun remainingMillis(now: Long = SystemClock.elapsedRealtime()): Long =
        if (phase == Phase.RESTING) (deadlineRealtime - now).coerceAtLeast(0L) else 0L

    /**
     * Seconds still to show. Rounded up so the display reads the full [REST_SECONDS] the instant a
     * rest starts and only reaches zero once the interval is genuinely over.
     */
    fun remainingSeconds(now: Long = SystemClock.elapsedRealtime()): Int = when (phase) {
        Phase.RESTING -> ceil(remainingMillis(now) / 1000.0).toInt()
        Phase.ELAPSED -> 0
        Phase.IDLE -> REST_SECONDS
    }
}

fun formatTime(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
