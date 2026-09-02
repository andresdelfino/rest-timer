package com.adelfino.resttimer.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.adelfino.resttimer.R

val BebasNeue = FontFamily(Font(R.font.bebas_neue, FontWeight.Normal))

val IbmPlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)

/** The small tracked-out uppercase captions above every field. */
val LabelStyle = TextStyle(
    fontFamily = IbmPlexMono,
    fontSize = 11.sp,
    letterSpacing = 0.25.em,
    lineHeight = 16.sp,
)

/** The large numerals inside the steppers. */
val ValueStyle = TextStyle(
    fontFamily = BebasNeue,
    fontSize = 56.sp,
    letterSpacing = 0.06.em,
    lineHeight = 56.sp,
    textAlign = TextAlign.Center,
)

/** The countdown itself. */
val CountdownStyle = TextStyle(
    fontFamily = BebasNeue,
    fontSize = 96.sp,
    letterSpacing = 0.05.em,
    lineHeight = 96.sp,
)

val ButtonStyle = TextStyle(
    fontFamily = BebasNeue,
    fontSize = 26.sp,
    letterSpacing = 0.12.em,
    lineHeight = 26.sp,
)

val StepperSymbolStyle = TextStyle(
    fontFamily = IbmPlexMono,
    fontWeight = FontWeight.SemiBold,
    fontSize = 19.sp,
    lineHeight = 19.sp,
)
