package com.adelfino.resttimer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 in the colour scheme the system derives from the wallpaper (always available, as the
 * app's minimum SDK is Android 12), light or dark as the system is set.
 *
 * @param alerting whether a rest has elapsed. The scheme then flips to its opposite brightness, the
 *   Material counterpart of the full-page inversion the web version applied on elapse.
 */
@Composable
fun RestTimerTheme(
    alerting: Boolean,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = darkTheme != alerting

    MaterialTheme(
        colorScheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
        content = content,
    )
}
