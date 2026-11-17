# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

The constraints, toolchain and commands shared with the other Kotlin projects are in `../CLAUDE.md`, which Claude Code loads automatically from the parent directory. This file covers only what is specific to this project.

## Constraints

- Android 17 is the only supported version: `minSdk` and `targetSdk` are both 37. Call the platform API directly, with no `Build.VERSION` checks, `*Compat` wrappers (`NotificationCompat`, `ContextCompat`, `WindowCompat`), backports such as `enableEdgeToEdge`, versioned resource folders (`-v34`), or fallbacks for other releases.
- The UI is Material 3 (`androidx.compose.material3`, version from the Compose BOM), coloured by the dynamic (wallpaper) scheme with no brand fallback, since Android 17 always has it. Use M3 components and the default type scale rather than hand-rolled controls or bundled fonts. Icons are Material Symbols vector drawables in `res/drawable` (`ic_add`, `ic_remove`), not the `material-icons` libraries.
- The brand lime (`@color/accent`) survives only outside the app: the launcher icon and the notification colour.

## Commands

```sh
./gradlew assembleRelease      # not minified (no R8); unsigned (no signing config)
```

There are no tests yet: no `test`/`androidTest` source sets and no test dependencies.

## Architecture

This is a single-module app (`:app`, package `com.adelfino.resttimer`). It is a native port of `rest-timer.html`, the original single-file web app, which stays in the repo as the reference for behaviour (the `120`-second rest, the `[400, 200, 400]` vibration). The visuals have since moved to Material 3 and no longer follow it. Comments in the Kotlin code refer back to it.

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
  - Countdown: low importance. The notification is a Live Update (`setRequestPromotedOngoing`, with the `POST_PROMOTED_NOTIFICATIONS` permission) laid out by Android 17's `MetricStyle`. Its first metric is a timer that counts down to `deadlineRealtime`. The platform ticks that timer, so the service never redraws it, and the status-bar chip shows it. The second metric is the set. The Cancel action carries `SEMANTIC_ACTION_STOP`.
  - Alert: high importance, with a full-screen intent.
- A notification can't change channel in place, so on elapse the foreground notification moves from id 1 to id 2.
- A second alarm stops the alert after `ALERT_TIMEOUT_MILLIS`.

**Process-death recovery.** If the restored phase isn't `IDLE`, `MainActivity` sends `ACTION_RESYNC` to rebuild the notification, alarm and alert. A pending alarm reaches the service on its own anyway.

**UI.** `MainActivity` hosts `ui/RestTimerScreen`. While the phase is `ELAPSED` it calls `setShowWhenLocked`/`setTurnScreenOn` so that the full-screen intent can wake the screen and appear over the keyguard. Meanwhile `RestTimerTheme(alerting)` switches the dynamic colour scheme instantly to its opposite brightness (dark to light, or light to dark), standing in for the web app's CSS inversion. That rule lives in `isDarkScheme` (`ui/theme/Theme.kt`). The window theme starts the system-bar icons at the system's brightness (`?android:attr/isLightTheme`). `MainActivity` applies the same rule through `window.insetsController`, so the icons flip with the scheme. The app is edge-to-edge because Android 17 enforces it, not because of any call.
