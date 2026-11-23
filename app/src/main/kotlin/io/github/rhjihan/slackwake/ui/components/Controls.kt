// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Title + optional caption + trailing switch; the whole row is one 48dp+ toggle target. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    enabled: Boolean = true,
    emphasized: Boolean = false,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                title,
                style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            if (caption != null) {
                Text(caption, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Semantics live on the row; the switch itself is decorative to avoid a double announcement.
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun CheckboxRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(
            title,
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
    }
}

/**
 * Slider paired with a digits-only field (Windows NumericTextBoxBehavior). The field
 * accepts any digits while typing and commits on IME Done or focus loss; the store then
 * clamps to [clamp], and the field snaps to the stored value. The slider may cover a
 * narrower [sliderRange] than [clamp] (idle timeout: slider 10–1800, field 10–7200).
 *
 * Field and slider mirror each other live: typing moves the slider, dragging rewrites
 * the field, and [supporting] (e.g. "6 minutes 15 seconds") is rendered from that live
 * value — only persisting waits for Done / focus loss / slider release.
 */
@Composable
fun SliderField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    clamp: IntRange,
    modifier: Modifier = Modifier,
    sliderRange: IntRange = clamp,
    caption: String? = null,
    enabled: Boolean = true,
    step: Int = 1,
    supporting: (@Composable (liveValue: Int) -> Unit)? = null,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    var dragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(value.coerceIn(sliderRange).toFloat()) }
    LaunchedEffect(value) { if (!dragging) sliderValue = value.coerceIn(sliderRange).toFloat() }
    val focus = LocalFocusManager.current

    fun commit() {
        val parsed = text.toIntOrNull()
        if (parsed == null) {
            text = value.toString()
        } else {
            val clamped = parsed.coerceIn(clamp)
            text = clamped.toString()
            if (clamped != value) onValueChange(clamped)
        }
    }

    Column(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { new ->
                    if (new.length <= 6 && new.all(Char::isDigit)) {
                        text = new
                        new.toIntOrNull()?.let { sliderValue = it.coerceIn(sliderRange).toFloat() }
                    }
                },
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions =
                    KeyboardActions(onDone = {
                        commit()
                        focus.clearFocus()
                    }),
                modifier =
                    Modifier
                        .width(112.dp)
                        .semantics { contentDescription = label }
                        .onFocusChanged { if (!it.isFocused) commit() },
            )
            Slider(
                value = sliderValue,
                onValueChange = {
                    dragging = true
                    sliderValue = it
                    text = snap(it, sliderRange, step).toString()
                },
                onValueChangeFinished = {
                    dragging = false
                    onValueChange(snap(sliderValue, sliderRange, step))
                },
                valueRange = sliderRange.first.toFloat()..sliderRange.last.toFloat(),
                enabled = enabled,
                modifier = Modifier.weight(1f).semantics { contentDescription = label },
            )
        }
        supporting?.invoke(text.toIntOrNull()?.coerceIn(clamp) ?: value)
    }
}

private fun snap(
    v: Float,
    range: IntRange,
    step: Int,
): Int {
    val stepped = ((v - range.first) / step).roundToInt() * step + range.first
    return stepped.coerceIn(range)
}
