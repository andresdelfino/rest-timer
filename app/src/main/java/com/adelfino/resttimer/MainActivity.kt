package com.adelfino.resttimer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adelfino.resttimer.ui.RestTimerScreen
import com.adelfino.resttimer.ui.theme.RestTimerTheme
import com.adelfino.resttimer.ui.theme.isDarkScheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = TimerRepository.get(this)

        // The bar icons follow the scheme's brightness, which the alert flips. They are handed over
        // as a rule rather than set once because enableEdgeToEdge re-applies its style on every
        // configuration change this activity handles itself (rotation, for one), which would
        // otherwise undo the flip.
        val systemBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { resources ->
            isDarkScheme(
                systemDark = resources.configuration.isNightModeActive,
                alerting = repository.state.value.phase == Phase.ELAPSED,
            )
        }
        enableEdgeToEdge(systemBarStyle, systemBarStyle)
        askForNotificationPermission()

        // A rest may have been restored from a process that did not survive it. Hand it back to the
        // service, which owns the notification, the alarm and the alert.
        if (repository.state.value.phase != Phase.IDLE) {
            RestTimerService.send(this, RestTimerService.ACTION_RESYNC)
        }

        setContent {
            val state by repository.state.collectAsStateWithLifecycle()
            val alerting = state.phase == Phase.ELAPSED

            LaunchedEffect(alerting) {
                // While alerting, the activity is what the full-screen intent brings up, so it has
                // to be allowed over the keyguard and to light the screen by itself.
                setShowWhenLocked(alerting)
                setTurnScreenOn(alerting)
                // Re-evaluates the bar rule above for the new phase.
                enableEdgeToEdge(systemBarStyle, systemBarStyle)
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        if (granted != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
