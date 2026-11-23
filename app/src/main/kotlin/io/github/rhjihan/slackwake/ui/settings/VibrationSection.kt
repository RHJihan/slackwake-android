// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.rhjihan.slackwake.R
import io.github.rhjihan.slackwake.core.settings.AppSettings
import io.github.rhjihan.slackwake.core.settings.SettingsClamps
import io.github.rhjihan.slackwake.core.vibration.VibrationMode
import io.github.rhjihan.slackwake.core.vibration.VibrationPattern
import io.github.rhjihan.slackwake.ui.components.SliderField
import io.github.rhjihan.slackwake.ui.components.SwitchRow

fun VibrationPattern.label(): Int =
    when (this) {
        VibrationPattern.ALERT -> R.string.vibration_pattern_alert
        VibrationPattern.HEARTBEAT -> R.string.vibration_pattern_heartbeat
        VibrationPattern.RAPID -> R.string.vibration_pattern_rapid
        VibrationPattern.SOS -> R.string.vibration_pattern_sos
        VibrationPattern.STACCATO -> R.string.vibration_pattern_staccato
        VibrationPattern.SYMPHONY -> R.string.vibration_pattern_symphony
    }

/**
 * Vibration controls: on/off, pattern (tap to select + feel it), Fixed vs Gradual
 * (crescendo), intensity and ramp time, and a preview button. On motors without
 * amplitude control the intensity/build-up controls are replaced by an explanation, and
 * with vibration off only the switch is shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibrationSection(
    s: AppSettings,
    hasVibrator: Boolean,
    hasAmplitudeControl: Boolean,
    viewModel: SettingsViewModel,
) {
    var patternPicker by rememberSaveable { mutableStateOf(false) }
    val pattern = VibrationPattern.fromKey(s.vibrationPattern)
    val mode = VibrationMode.fromKey(s.vibrationMode)

    SwitchRow(
        stringResource(R.string.vibration_title),
        s.vibrationEnabled && hasVibrator,
        viewModel::setVibrationEnabled,
        caption = stringResource(if (hasVibrator) R.string.vibration_caption else R.string.vibration_unsupported),
        enabled = hasVibrator,
        modifier = Modifier.testTag("vibration_enabled"),
    )
    // Everything below only matters while vibration is on: hide it rather than grey it out.
    if (!hasVibrator || !s.vibrationEnabled) return

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clickable { patternPicker = true }
                .padding(vertical = 8.dp),
        ) {
            Text(stringResource(R.string.vibration_pattern), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(pattern.label()), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = { viewModel.previewVibration() }) {
            Icon(Icons.Filled.Vibration, contentDescription = stringResource(R.string.vibration_preview))
        }
    }

    Text(stringResource(R.string.vibration_mode), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        VibrationMode.entries.forEachIndexed { i, m ->
            SegmentedButton(
                selected = mode == m,
                onClick = { viewModel.setVibrationMode(m) },
                shape = SegmentedButtonDefaults.itemShape(i, VibrationMode.entries.size),
                // Gradual needs amplitude control; Fixed is always available.
                enabled = m == VibrationMode.FIXED || hasAmplitudeControl,
                modifier = Modifier.testTag("vibration_mode_${m.key}"),
            ) {
                Text(stringResource(if (m == VibrationMode.FIXED) R.string.vibration_mode_fixed else R.string.vibration_mode_gradual))
            }
        }
    }
    Text(
        stringResource(if (mode == VibrationMode.GRADUAL) R.string.vibration_mode_gradual_caption else R.string.vibration_mode_fixed_caption),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (!hasAmplitudeControl) {
        // On/off-only motor: strength settings can't do anything, so explain and hide them.
        Text(
            stringResource(R.string.vibration_no_amplitude),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp).testTag("vibration_no_amplitude"),
        )
    } else {
        SliderField(
            label = stringResource(R.string.vibration_intensity),
            value = s.vibrationIntensityPercent,
            onValueChange = viewModel::commitVibrationIntensity,
            clamp = SettingsClamps.VIBRATION_INTENSITY_PERCENT,
            step = 5,
        )
        if (mode == VibrationMode.GRADUAL) {
            SliderField(
                label = stringResource(R.string.vibration_ramp),
                caption = stringResource(R.string.vibration_ramp_caption),
                value = s.vibrationRampSeconds,
                onValueChange = viewModel::setVibrationRamp,
                clamp = SettingsClamps.VIBRATION_RAMP_SECONDS,
                step = 5,
            )
        }
    }

    if (patternPicker) {
        AlertDialog(
            onDismissRequest = {
                patternPicker = false
                viewModel.stopPreview()
            },
            title = { Text(stringResource(R.string.vibration_pattern)) },
            confirmButton = {
                TextButton(onClick = {
                    patternPicker = false
                    viewModel.stopPreview()
                }) { Text(stringResource(R.string.ok)) }
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(R.string.vibration_pattern_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    VibrationPattern.entries.forEach { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(selected = p == pattern, role = Role.RadioButton) { viewModel.setVibrationPattern(p) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = p == pattern, onClick = null)
                            Text(stringResource(p.label()), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
        )
    }
}
