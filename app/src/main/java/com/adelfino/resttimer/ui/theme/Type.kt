package com.adelfino.resttimer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/**
 * The countdown, which has to be legible from across the gym and so outgrows even the largest role
 * in the type scale. Tabular figures keep it from shifting sideways as the digits change.
 */
val Typography.countdown: TextStyle
    get() = displayLarge.copy(
        fontSize = 96.sp,
        lineHeight = 104.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum",
    )
