package com.adelfino.resttimer.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalRestColors: ProvidableCompositionLocal<RestColors> = staticCompositionLocalOf { DarkColors }

/**
 * @param alerting whether a rest has elapsed. The palette crosses over rather than cutting, which
 *   at this speed still reads as a flip but without the strobe of an instant full-screen inversion.
 */
@Composable
fun RestTimerTheme(alerting: Boolean, content: @Composable () -> Unit) {
    val target = if (alerting) AlertColors else DarkColors

    @Composable
    fun cross(color: Color) = animateColorAsState(color, tween(FLIP_MILLIS), label = "color").value

    val colors = RestColors(
        background = cross(target.background),
        surface = cross(target.surface),
        border = cross(target.border),
        accent = cross(target.accent),
        accentDim = cross(target.accentDim),
        text = cross(target.text),
        muted = cross(target.muted),
        readOnlyBackground = cross(target.readOnlyBackground),
    )

    CompositionLocalProvider(LocalRestColors provides colors, content = content)
}

private const val FLIP_MILLIS = 150
