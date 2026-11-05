# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

The constraints, toolchain and commands shared with the other Kotlin projects are in `../CLAUDE.md`, which Claude Code loads automatically from the parent directory. This file covers only what is specific to this project.

## Constraints

- The UI uses Compose Foundation only, with no Material dependency. `ui/Components.kt` provides a hand-rolled `Text` (on `BasicText`) and `Modifier.pressable` (no ripple). Build new controls from these instead of pulling in Material.

## Commands

```sh
./gradlew assembleRelease      # R8-minified + resource-shrunk; unsigned (no signing config)
```

There are no tests yet: no `test`/`androidTest` source sets and no test dependencies.

## Architecture

This is a single-module app (`:app`, package `com.adelfino.resttimer`). It is a native port of `rest-timer.html`, the original single-file web app, which stays in the repo as the reference for behaviour and visuals (palette, fonts, the `120`-second rest, the `[400, 200, 400]` vibration). Comments in the Kotlin code refer back to it.

**State.** `TimerRepository` is a process-wide singleton (`TimerRepository.get(context)`) and the single source of truth. It exposes `StateFlow<TimerState>`, and every change is written to SharedPreferences through its private `update {}`, including a rest that is still running. `TimerState` is a plain data class: phase `IDLE → RESTING → ELAPSED`, plus derived rules (`canRest`, `canResume`, the editable ranges) that both the UI and the repository use to enforce valid transitions.

**Timekeeping.** The rest deadline is stored as a `SystemClock.elapsedRealtime()` value, never wall-clock time, so the countdown can't drift while the device sleeps or the app is in the background. A "boot instant" (wall clock minus elapsedRealtime) is saved with it, so a restore can tell when a reboot has made the deadline meaningless. A rest that ended while the process was dead is restored as `ELAPSED` only if it ended less than `ALERT_TIMEOUT_MILLIS` ago.

**Who mutates what.** The UI changes sets, reps and the current set directly on the repository. Anything in the rest lifecycle (start, resume, cancel, restart during a rest) goes through `RestTimerService.send(context, ACTION_*)`. That always calls `startForegroundService`, because the service owns the notification, the alarms and the vibration, and it has to tear them down consistently.

**`RestTimerService`** is a `specialUse` foreground service for the whole rest.
- Every `onStartCommand` must call `startForeground` before doing anything else, including stopping.
- Two paths can end the countdown, and the first to arrive wins:
  - a coroutine `delay`, which is exact while the CPU is awake;
  - an exact `ELAPSED_REALTIME_WAKEUP` alarm. It fires `RestAlarmReceiver`, which passes the alarm's action straight back to the service.
- The `alerting` flag guards against two elapse events.
- Two notification channels are created in `RestTimerApplication`, both silent; the service drives the vibration itself:
  - Countdown: low importance, uses the platform chronometer so the service never redraws it.
  - Alert: high importance, with a full-screen intent.
- A notification can't change channel in place, so on elapse the foreground notification moves from id 1 to id 2.
- A second alarm stops the alert after `ALERT_TIMEOUT_MILLIS`.

**Process-death recovery.** If the restored phase isn't `IDLE`, `MainActivity` sends `ACTION_RESYNC` to rebuild the notification, alarm and alert. A pending alarm reaches the service on its own anyway.

**UI.** `MainActivity` hosts `ui/RestTimerScreen`. While the phase is `ELAPSED` it calls `setShowWhenLocked`/`setTurnScreenOn` so that the full-screen intent can wake the screen and appear over the keyguard. Meanwhile `RestTimerTheme(alerting)` animates the colours from `DarkColors` to `AlertColors` (provided through `LocalRestColors`), standing in for the web app's CSS inversion.
