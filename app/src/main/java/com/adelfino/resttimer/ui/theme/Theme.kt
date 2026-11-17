package com.adelfino.resttimer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the scheme is dark: as the system is set, until a rest elapses and flips it to its
 * opposite brightness, the Material counterpart of the full-page inversion the web version applied
 * on elapse. `MainActivity` applies the same rule to the system-bar icons.
 */
fun isDarkScheme(systemDark: Boolean, alerting: Boolean): Boolean = systemDark != alerting

/**
 * Material 3 in the colour scheme the system derives from the wallpaper (always available on
 * Android 17), dark or light as [isDarkScheme] decides.
 *
 * @param alerting whether a rest has elapsed.
 */
@Composable
fun RestTimerTheme(alerting: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isDarkScheme(isSystemInDarkTheme(), alerting)

    MaterialTheme(
        colorScheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
        content = content,
    )
}
