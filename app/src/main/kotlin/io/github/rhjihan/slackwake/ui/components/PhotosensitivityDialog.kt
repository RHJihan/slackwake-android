// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.rhjihan.slackwake.R

/**
 * Shown when the visual flash is about to be turned on. "Don't show again" only takes
 * effect if the user confirms; cancelling never suppresses future warnings.
 */
@Composable
fun PhotosensitivityDialog(
    onConfirm: (dontShowAgain: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var dontShowAgain by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.photosensitivity_title)) },
        text = {
            Column {
                Text(stringResource(R.string.photosensitivity_body))
                CheckboxRow(
                    stringResource(R.string.photosensitivity_dont_show),
                    dontShowAgain,
                    { dontShowAgain = it },
                    modifier = Modifier.padding(top = 8.dp).testTag("photosensitivity_dont_show"),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(dontShowAgain) }) { Text(stringResource(R.string.photosensitivity_enable)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
