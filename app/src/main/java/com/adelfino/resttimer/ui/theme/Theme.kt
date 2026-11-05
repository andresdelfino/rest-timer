package com.adelfino.resttimer.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

val LocalRestColors: ProvidableCompositionLocal<RestColors> = staticCompositionLocalOf { DarkColors }

/**
 * @param alerting whether a rest has elapsed. The palette crosses over rather than cutting, which
 *   at this speed still reads as a flip but without the strobe of an instant full-screen inversion.
 */
@Composable
fun RestTimerTheme(alerting: Boolean, content: @Composable () -> Unit) {
    val flip by animateFloatAsState(if (alerting) 1f else 0f, tween(FLIP_MILLIS), label = "flip")

    fun cross(color: RestColors.() -> Color) = lerp(DarkColors.color(), AlertColors.color(), flip)

    val colors = RestColors(
        background = cross { background },
        surface = cross { surface },
        border = cross { border },
        accent = cross { accent },
        accentDim = cross { accentDim },
        text = cross { text },
        muted = cross { muted },
        readOnlyBackground = cross { readOnlyBackground },
    )

    CompositionLocalProvider(LocalRestColors provides colors, content = content)
}

private const val FLIP_MILLIS = 150
