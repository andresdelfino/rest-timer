package com.adelfino.resttimer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle

/**
 * The app draws from a fixed set of hand-rolled parts rather than Material, so text is just a style
 * and a colour with nothing implicit behind it.
 */
@Composable
fun Text(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) = BasicText(text = text, modifier = modifier, style = style.copy(color = color))

/**
 * A tap target with no ripple: every control here shows its own press feedback, and a Material
 * ripple would be the only soft-edged thing on an otherwise hard-edged screen.
 */
fun Modifier.pressable(
    enabled: Boolean,
    label: String,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
): Modifier = semantics(mergeDescendants = true) { contentDescription = label }
    .clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        role = Role.Button,
        onClick = onClick,
    )
