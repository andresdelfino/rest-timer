package com.adelfino.resttimer.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The palette the screen draws from. Two instances exist: the resting-dark one the app normally
 * wears, and the alert one it flips to when a rest elapses.
 */
@Immutable
data class RestColors(
    val background: Color,
    val surface: Color,
    val border: Color,
    val accent: Color,
    val accentDim: Color,
    val text: Color,
    val muted: Color,
    val readOnlyBackground: Color,
)

val DarkColors = RestColors(
    background = Color(0xFF0A0A0A),
    surface = Color(0xFF111111),
    border = Color(0xFF222222),
    accent = Color(0xFFC8F135),
    accentDim = Color(0xFF8AAB1F),
    text = Color(0xFFF0F0F0),
    muted = Color(0xFF555555),
    readOnlyBackground = Color(0xFF0D1A00),
)

/**
 * [DarkColors] put through the `invert(1) hue-rotate(180deg)` filter the web version applied to the
 * whole page on elapse, evaluated once here instead of per frame. The lime accent lands on a deep
 * green, which is why the alert reads as green-on-white rather than a plain photo negative.
 */
val AlertColors = RestColors(
    background = Color(0xFFF5F5F5),
    surface = Color(0xFFEEEEEE),
    border = Color(0xFFDDDDDD),
    accent = Color(0xFF123B00),
    accentDim = Color(0xFF557600),
    text = Color(0xFF0F0F0F),
    muted = Color(0xFFAAAAAA),
    readOnlyBackground = Color(0xFFE1EED4),
)
