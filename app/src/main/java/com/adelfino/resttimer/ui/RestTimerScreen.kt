package com.adelfino.resttimer.ui

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.adelfino.resttimer.Phase
import com.adelfino.resttimer.R
import com.adelfino.resttimer.REST_SECONDS
import com.adelfino.resttimer.TimerState
import com.adelfino.resttimer.formatTime
import com.adelfino.resttimer.ui.theme.ButtonStyle
import com.adelfino.resttimer.ui.theme.CountdownStyle
import com.adelfino.resttimer.ui.theme.LabelStyle
import com.adelfino.resttimer.ui.theme.LocalRestColors
import com.adelfino.resttimer.ui.theme.RestTimerTheme
import com.adelfino.resttimer.ui.theme.StepperSymbolStyle
import com.adelfino.resttimer.ui.theme.ValueStyle
import kotlinx.coroutines.delay

private val FieldShape = RoundedCornerShape(4.dp)
private val ValueShape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
private val ButtonsShape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)

private const val DISABLED_ALPHA = 0.25f

@Composable
fun RestTimerScreen(
    state: TimerState,
    onSetsChange: (Int) -> Unit,
    onRepsChange: (Int) -> Unit,
    onCurrentSetChange: (Int) -> Unit,
    onRest: () -> Unit,
    onResume: () -> Unit,
    onStartAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRestColors.current

    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StepperField(
                    label = stringResource(R.string.label_sets),
                    value = state.sets,
                    range = state.setsRange,
                    onValueChange = onSetsChange,
                    modifier = Modifier.weight(1f),
                )
                StepperField(
                    label = stringResource(R.string.label_reps),
                    value = state.reps,
                    range = state.repsRange,
                    onValueChange = onRepsChange,
                    modifier = Modifier.weight(1f),
                )
            }
            StepperField(
                label = stringResource(R.string.label_current_set),
                value = state.currentSet,
                range = state.currentSetRange,
                onValueChange = onCurrentSetChange,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.weight(1f))

        TimerSection(state)

        Spacer(Modifier.weight(1f))

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ActionButton(
                    text = stringResource(R.string.action_rest),
                    enabled = state.canRest,
                    onClick = onRest,
                    modifier = Modifier.weight(1f),
                )
                ActionButton(
                    text = stringResource(R.string.action_resume),
                    enabled = state.canResume,
                    onClick = onResume,
                    modifier = Modifier.weight(1f),
                )
            }
            ActionButton(
                text = stringResource(R.string.action_start_again),
                enabled = state.canStartAgain,
                onClick = onStartAgain,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The countdown as displayed: recomputed from the monotonic deadline rather than accumulated from a
 * tick count, and re-scheduled onto the next whole-second boundary on every pass. Returning from a
 * locked screen therefore shows the true remaining time straight away, with no catching up.
 */
@Composable
private fun countdownSeconds(state: TimerState): Int {
    val seconds by produceState(state.remainingSeconds(), state.phase, state.deadlineRealtime) {
        while (true) {
            val now = SystemClock.elapsedRealtime()
            value = state.remainingSeconds(now)
            if (state.phase != Phase.RESTING || value <= 0) break
            delay(state.remainingMillis(now) - (value - 1) * 1000L)
        }
    }
    return seconds
}

@Composable
private fun StepperField(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRestColors.current

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label.uppercase(), style = LabelStyle, color = colors.muted)

        Row(Modifier.height(IntrinsicSize.Min)) {
            // A 1dp inset over a solid fill draws each part's border, so the numeral and the
            // buttons meet on a single hairline instead of two abutting outlines.
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(ValueShape)
                    .background(colors.accentDim)
                    .padding(start = 1.dp, top = 1.dp, bottom = 1.dp)
                    .background(colors.readOnlyBackground),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = value.toString(),
                    style = ValueStyle,
                    color = colors.accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 8.dp, start = 8.dp, end = 8.dp),
                )
            }

            Column(
                Modifier
                    .width(44.dp)
                    .fillMaxHeight()
                    .clip(ButtonsShape)
                    .background(colors.border)
                    .padding(top = 1.dp, end = 1.dp, bottom = 1.dp),
            ) {
                StepperButton(
                    symbol = "+",
                    label = stringResource(R.string.cd_increment, label),
                    enabled = value + 1 in range,
                    onClick = { onValueChange(value + 1) },
                    modifier = Modifier.weight(1f),
                )
                Hairline()
                StepperButton(
                    symbol = "−",
                    label = stringResource(R.string.cd_decrement, label),
                    enabled = value - 1 in range,
                    onClick = { onValueChange(value - 1) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StepperButton(
    symbol: String,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRestColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier
            .fillMaxWidth()
            .background(if (pressed) colors.border else colors.surface)
            .pressable(enabled, label, interactionSource, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            style = StepperSymbolStyle,
            color = if (pressed) colors.accent else colors.text,
            modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA),
        )
    }
}

@Composable
private fun TimerSection(state: TimerState, modifier: Modifier = Modifier) {
    val colors = LocalRestColors.current
    val secondsLeft = countdownSeconds(state)
    val running = state.phase != Phase.IDLE
    val fraction = if (running) secondsLeft / REST_SECONDS.toFloat() else 0f
    // The bar slides between whole seconds at exactly the rate the countdown falls. It is only
    // read while drawing, so the slide redraws the bar without recomposing the section.
    val width by animateFloatAsState(fraction, tween(1000, easing = LinearEasing), label = "bar")

    Column(modifier.fillMaxWidth()) {
        Hairline()
        Column(
            Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.label_rest_countdown).uppercase(),
                style = LabelStyle,
                color = colors.muted,
            )
            Text(
                text = formatTime(secondsLeft),
                style = CountdownStyle,
                color = if (running) colors.accent else colors.muted,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.border)
                    .drawBehind {
                        val filled = size.width * width.coerceIn(0f, 1f)
                        drawRect(colors.accent, size = size.copy(width = filled))
                    },
            )
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalRestColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    val outline = if (enabled) colors.accent else colors.muted

    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(FieldShape)
            .background(outline)
            .padding(2.dp)
            .background(colors.background)
            .pressable(enabled, text, interactionSource, onClick)
            .padding(vertical = 18.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text.uppercase(), style = ButtonStyle, color = outline)
    }
}

/** The 1dp rule that separates parts of the screen. */
@Composable
private fun Hairline() {
    Spacer(Modifier.fillMaxWidth().height(1.dp).background(LocalRestColors.current.border))
}

@Preview(name = "Idle", widthDp = 400, heightDp = 880)
@Composable
private fun RestTimerScreenPreview() {
    RestTimerTheme(alerting = false) {
        RestTimerScreen(TimerState(), {}, {}, {}, {}, {}, {})
    }
}

@Preview(name = "Rest over", widthDp = 400, heightDp = 880)
@Composable
private fun RestTimerScreenAlertingPreview() {
    RestTimerTheme(alerting = true) {
        RestTimerScreen(TimerState(currentSet = 2, phase = Phase.ELAPSED), {}, {}, {}, {}, {}, {})
    }
}
