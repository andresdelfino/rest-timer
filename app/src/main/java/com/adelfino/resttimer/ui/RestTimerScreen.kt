package com.adelfino.resttimer.ui

import android.content.res.Configuration
import android.os.SystemClock
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Wallpapers
import androidx.compose.ui.unit.dp
import com.adelfino.resttimer.Phase
import com.adelfino.resttimer.R
import com.adelfino.resttimer.REST_SECONDS
import com.adelfino.resttimer.TimerState
import com.adelfino.resttimer.formatTime
import com.adelfino.resttimer.ui.theme.RestTimerTheme
import com.adelfino.resttimer.ui.theme.countdown
import kotlinx.coroutines.delay

/** Taller than Material's default 40dp: these are hit mid-workout, often without looking. */
private val ActionButtonHeight = 56.dp

/** Likewise up from the default 40dp, which is the minimum touch target and no more. */
private val StepButtonSize = 48.dp

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
    Surface(modifier.fillMaxSize()) {
        Column(
            Modifier
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StepperCard(
                        label = stringResource(R.string.label_sets),
                        value = state.sets,
                        range = state.setsRange,
                        onValueChange = onSetsChange,
                        modifier = Modifier.weight(1f),
                    )
                    StepperCard(
                        label = stringResource(R.string.label_reps),
                        value = state.reps,
                        range = state.repsRange,
                        onValueChange = onRepsChange,
                        modifier = Modifier.weight(1f),
                    )
                }
                StepperCard(
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

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onRest,
                        enabled = state.canRest,
                        modifier = Modifier.weight(1f).height(ActionButtonHeight),
                    ) {
                        ActionLabel(stringResource(R.string.action_rest))
                    }
                    Button(
                        onClick = onResume,
                        enabled = state.canResume,
                        modifier = Modifier.weight(1f).height(ActionButtonHeight),
                    ) {
                        ActionLabel(stringResource(R.string.action_resume))
                    }
                }
                OutlinedButton(
                    onClick = onStartAgain,
                    enabled = state.canStartAgain,
                    modifier = Modifier.fillMaxWidth().height(ActionButtonHeight),
                ) {
                    ActionLabel(stringResource(R.string.action_start_again))
                }
            }
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
private fun StepperCard(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Merged so that a screen reader reads the field as one item: "Sets, 4".
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = value.toString(), style = MaterialTheme.typography.displayMedium)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StepButton(
                    icon = R.drawable.ic_add,
                    label = stringResource(R.string.cd_increment, label),
                    enabled = value + 1 in range,
                    onClick = { onValueChange(value + 1) },
                )
                StepButton(
                    icon = R.drawable.ic_remove,
                    label = stringResource(R.string.cd_decrement, label),
                    enabled = value - 1 in range,
                    onClick = { onValueChange(value - 1) },
                )
            }
        }
    }
}

@Composable
private fun StepButton(
    @DrawableRes icon: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(StepButtonSize),
    ) {
        Icon(painter = painterResource(icon), contentDescription = label)
    }
}

@Composable
private fun TimerSection(state: TimerState, modifier: Modifier = Modifier) {
    val secondsLeft = countdownSeconds(state)
    val running = state.phase != Phase.IDLE
    val fraction = if (running) secondsLeft / REST_SECONDS.toFloat() else 0f
    // The bar slides between whole seconds at exactly the rate the countdown falls. It is only
    // read while drawing, so the slide redraws the bar without recomposing the section.
    val progress by animateFloatAsState(fraction, tween(1000, easing = LinearEasing), label = "bar")

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.label_rest_countdown),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatTime(secondsLeft),
            style = MaterialTheme.typography.countdown,
            color = if (running) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(8.dp),
        )
    }
}

@Composable
private fun ActionLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}

@Preview(name = "Idle", widthDp = 400, heightDp = 880, wallpaper = Wallpapers.GREEN_DOMINATED_EXAMPLE)
@Preview(
    name = "Idle, dark",
    widthDp = 400,
    heightDp = 880,
    wallpaper = Wallpapers.GREEN_DOMINATED_EXAMPLE,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Composable
private fun RestTimerScreenPreview() {
    RestTimerTheme(alerting = false) {
        RestTimerScreen(TimerState(), {}, {}, {}, {}, {}, {})
    }
}

@Preview(
    name = "Rest over, dark",
    widthDp = 400,
    heightDp = 880,
    wallpaper = Wallpapers.GREEN_DOMINATED_EXAMPLE,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Composable
private fun RestTimerScreenAlertingPreview() {
    RestTimerTheme(alerting = true) {
        RestTimerScreen(TimerState(currentSet = 2, phase = Phase.ELAPSED), {}, {}, {}, {}, {}, {})
    }
}
