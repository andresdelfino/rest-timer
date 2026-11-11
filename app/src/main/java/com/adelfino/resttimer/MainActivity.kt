package com.adelfino.resttimer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
import android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adelfino.resttimer.ui.RestTimerScreen
import com.adelfino.resttimer.ui.theme.RestTimerTheme
import com.adelfino.resttimer.ui.theme.isDarkScheme

private const val LIGHT_SYSTEM_BARS = APPEARANCE_LIGHT_STATUS_BARS or APPEARANCE_LIGHT_NAVIGATION_BARS

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askForNotificationPermission()

        val repository = TimerRepository.get(this)

        // A rest may have been restored from a process that did not survive it. Hand it back to the
        // service, which owns the notification, the alarm and the alert.
        if (repository.state.value.phase != Phase.IDLE) {
            RestTimerService.send(this, RestTimerService.ACTION_RESYNC)
        }

        setContent {
            val state by repository.state.collectAsStateWithLifecycle()
            val alerting = state.phase == Phase.ELAPSED
            val darkScheme = isDarkScheme(isSystemInDarkTheme(), alerting)

            LaunchedEffect(alerting) {
                // While alerting, the activity is what the full-screen intent brings up, so it has
                // to be allowed over the keyguard and to light the screen by itself.
                setShowWhenLocked(alerting)
                setTurnScreenOn(alerting)
            }

            // The window theme starts the bar icons at the system's brightness. This keeps them
            // matching the scheme when the alert flips it.
            LaunchedEffect(darkScheme) {
                val appearance = if (darkScheme) 0 else LIGHT_SYSTEM_BARS
                window.insetsController?.setSystemBarsAppearance(appearance, LIGHT_SYSTEM_BARS)
            }

            RestTimerTheme(alerting = alerting) {
                RestTimerScreen(
                    state = state,
                    onSetsChange = repository::setSets,
                    onRepsChange = repository::setReps,
                    onCurrentSetChange = repository::setCurrentSet,
                    onRest = { RestTimerService.send(this, RestTimerService.ACTION_START) },
                    onResume = { RestTimerService.send(this, RestTimerService.ACTION_RESUME) },
                    // With a rest in flight it is the service that has to tear down the alarm,
                    // the notification and the vibration; with none, there is nothing to wake it
                    // for and the counter is simply reset in place.
                    onStartAgain = {
                        if (state.phase == Phase.IDLE) {
                            repository.startAgain()
                        } else {
                            RestTimerService.send(this, RestTimerService.ACTION_RESTART)
                        }
                    },
                )
            }
        }
    }

    /**
     * The countdown and the rest-over alert both surface as notifications, so ask once on the first
     * run. Declining costs only those; the in-app countdown and the vibration are unaffected.
     */
    private fun askForNotificationPermission() {
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        if (granted != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
