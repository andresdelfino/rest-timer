# Rest Timer

A rest timer for strength training: set how many sets and reps you are doing, tap **Rest**
between sets, and the app counts down two minutes and buzzes when it is over.

Originally a single-page web app (`rest-timer.html`, kept for reference); this is the native
Android port of it.

## Building

Needs an Android SDK with platform 37 and JDK 17+. Android Studio's bundled JDK works:

```sh
JAVA_HOME=/path/to/android-studio/jbr ./gradlew assembleDebug
```

`local.properties` is not checked in — point `sdk.dir` at your SDK, or open the project in
Android Studio and let it write the file.

- minSdk 31 (Android 12)
- targetSdk 36 (Android 16)
- compileSdk 37, which is what current AndroidX requires

## How the countdown survives the screen going off

The web version could only run a `setInterval` and hope the tab stayed alive, which is why it
had to keep re-deriving the remaining time from `Date.now()`. The native version does not have
to hope:

- The deadline is a reading on `SystemClock.elapsedRealtime()`, a monotonic clock that keeps
  counting through sleep and ignores wall-clock changes. Everything on screen is derived from
  it, so there is nothing to drift.
- `RestTimerService` runs in the foreground for the whole rest, holding the countdown
  notification. The notification uses a countdown chronometer, so the platform ticks it down
  without the app waking up at all.
- An exact alarm (`setExactAndAllowWhileIdle`) is what actually ends the rest. It fires through
  Doze, and it is held by the system rather than the app, so it survives even being killed.
- The rest is written to shared preferences alongside the boot instant that makes the monotonic
  reading meaningful. If the process dies mid-rest, the alarm still arrives, finds the rest
  waiting, and alerts anyway. Across a reboot the reading is correctly discarded.
- The alert is a full-screen-intent notification on an alarm-category channel, so it comes up
  over the lock screen, and vibrates as one repeating waveform the system vibrator keeps time
  for. **Resume** — in the app or on the notification — stops it and advances the set.

## Changing the rest length

`REST_SECONDS` in `TimerState.kt`.
