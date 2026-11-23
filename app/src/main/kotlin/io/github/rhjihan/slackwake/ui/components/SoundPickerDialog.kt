// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.rhjihan.slackwake.R
import io.github.rhjihan.slackwake.sound.SoundGroup
import io.github.rhjihan.slackwake.sound.SoundOption

/**
 * Replaces the Windows hover-preview dropdown. Touch has no hover, so tapping a row
 * selects it AND previews it, the way the system ringtone picker works. Closing the
 * dialog stops the preview (Windows stopped on dropdown close).
 */
@Composable
fun SoundPickerDialog(
    options: List<SoundOption>,
    selected: String,
    customLabel: String?,
    playing: String?,
    onPick: (String) -> Unit,
    onChooseFile: () -> Unit,
    onDismiss: () -> Unit,
) {
    val defaultLabel = stringResource(R.string.sound_default)
    val rows =
        buildList {
            add(SoundOption(defaultLabel, "", SoundGroup.DEFAULT))
            if (customLabel != null && selected.isNotBlank() && options.none { it.uri == selected }) {
                add(SoundOption(customLabel, selected, SoundGroup.CUSTOM))
            }
            addAll(options)
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sound_choose)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        dismissButton = {
            TextButton(onClick = onChooseFile) {
                Icon(Icons.Filled.AudioFile, contentDescription = null)
                Text(stringResource(R.string.sound_choose_file), Modifier.padding(start = 8.dp))
            }
        },
        text = {
            Column {
                Text(
                    stringResource(R.string.sound_picker_hint) + " " + stringResource(R.string.sound_volume_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    rows.groupBy { it.group }.forEach { (group, groupRows) ->
                        if (group != SoundGroup.DEFAULT) item(key = "header-$group") { GroupHeader(group) }
                        items(groupRows, key = { "${it.group}-${it.uri}" }) { row ->
                            SoundRow(row, row.uri == selected, row.uri == playing) { onPick(row.uri) }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun GroupHeader(group: SoundGroup) {
    val label =
        when (group) {
            SoundGroup.ALARM -> R.string.sound_group_alarms
            SoundGroup.NOTIFICATION -> R.string.sound_group_notifications
            SoundGroup.RINGTONE -> R.string.sound_group_ringtones
            SoundGroup.CUSTOM -> R.string.sound_group_custom
            SoundGroup.DEFAULT -> R.string.sound_default
        }
    Column {
        HorizontalDivider(Modifier.padding(top = 8.dp))
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp).semantics { heading() },
        )
    }
}

@Composable
private fun SoundRow(
    row: SoundOption,
    selected: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(row.title, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
        if (playing) Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.sound_preview_play))
    }
}
