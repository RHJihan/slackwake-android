// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.ui.settings

import android.content.ActivityNotFoundException
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rhjihan.slackwake.R
import io.github.rhjihan.slackwake.core.backup.SettingsBackup
import io.github.rhjihan.slackwake.data.backup.RestorePreview
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/** Openable types: some file providers label JSON as text or as generic binary. */
private val RESTORE_MIME_TYPES = arrayOf(SettingsBackup.MIME_TYPE, "text/*", "application/octet-stream")

/**
 * "Backup & restore": Save to a file the user picks (system "Save as"), or restore from
 * one (system file picker) after a confirmation that says what will and won't change,
 * with Undo afterwards. No storage permission, nothing leaves the phone by itself.
 */
@Composable
fun BackupSection(
    viewModel: SettingsViewModel,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val pending by viewModel.pendingRestore.collectAsStateWithLifecycle()

    val createLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(SettingsBackup.MIME_TYPE)) { uri ->
            if (uri != null) viewModel.exportSettings(uri)
        }
    val openLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.readBackup(uri)
        }

    val saved = stringResource(R.string.backup_saved)
    val saveFailed = stringResource(R.string.backup_save_failed)
    val noPicker = stringResource(R.string.backup_error_no_picker)
    val restored = stringResource(R.string.backup_restored)
    val undo = stringResource(R.string.backup_undo)
    val failures =
        mapOf(
            null to stringResource(R.string.backup_error_read),
            SettingsBackup.Failure.TOO_LARGE to stringResource(R.string.backup_error_too_large),
            SettingsBackup.Failure.NOT_JSON to stringResource(R.string.backup_error_not_slackwake),
            SettingsBackup.Failure.NOT_SLACKWAKE to stringResource(R.string.backup_error_not_slackwake),
            SettingsBackup.Failure.NEWER_VERSION to stringResource(R.string.backup_error_newer),
            SettingsBackup.Failure.INVALID to stringResource(R.string.backup_error_invalid),
        )
    LaunchedEffect(viewModel) {
        viewModel.backupMessages.collect { m ->
            when (m) {
                BackupMessage.Saved -> {
                    snackbar.showSnackbar(saved)
                }

                BackupMessage.SaveFailed -> {
                    snackbar.showSnackbar(saveFailed)
                }

                BackupMessage.NoFilePicker -> {
                    snackbar.showSnackbar(noPicker)
                }

                is BackupMessage.RestoreFailed -> {
                    snackbar.showSnackbar(failures.getValue(m.reason), duration = SnackbarDuration.Long)
                }

                is BackupMessage.Restored -> {
                    val r = snackbar.showSnackbar(restored, actionLabel = undo, duration = SnackbarDuration.Long)
                    if (r == SnackbarResult.ActionPerformed) viewModel.undoRestore(m.previous)
                }
            }
        }
    }

    Column {
        Text(
            stringResource(R.string.backup_caption),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    try {
                        createLauncher.launch(SettingsBackup.fileName(LocalDate.now()))
                    } catch (_: ActivityNotFoundException) {
                        viewModel.reportNoFilePicker()
                    }
                },
                modifier = Modifier.testTag("backup_export"),
            ) {
                Icon(Icons.Filled.Save, contentDescription = null)
                Text(stringResource(R.string.backup_export), Modifier.padding(start = 8.dp))
            }
            OutlinedButton(
                onClick = {
                    try {
                        openLauncher.launch(RESTORE_MIME_TYPES)
                    } catch (_: ActivityNotFoundException) {
                        viewModel.reportNoFilePicker()
                    }
                },
                modifier = Modifier.testTag("backup_restore"),
            ) {
                Icon(Icons.Filled.Restore, contentDescription = null)
                Text(stringResource(R.string.backup_restore), Modifier.padding(start = 8.dp))
            }
        }
    }

    pending?.let { preview ->
        RestoreConfirmDialog(
            preview = preview,
            dateText =
                preview.meta.exportedAt?.let {
                    val d = Date.from(it.atZone(ZoneId.systemDefault()).toInstant())
                    "${DateFormat.getMediumDateFormat(context).format(d)} ${DateFormat.getTimeFormat(context).format(d)}"
                },
            onConfirm = viewModel::confirmRestore,
            onDismiss = viewModel::cancelRestore,
        )
    }
}

@Composable
private fun RestoreConfirmDialog(
    preview: RestorePreview,
    dateText: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val version = preview.meta.appVersion
                val source =
                    when {
                        preview.meta.source == SettingsBackup.Source.PLAIN_SETTINGS -> {
                            stringResource(R.string.backup_source_plain)
                        }

                        dateText != null && version != null -> {
                            stringResource(R.string.backup_source_backup, dateText, version)
                        }

                        else -> {
                            stringResource(R.string.backup_source_backup_undated)
                        }
                    }
                Text(source, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.backup_confirm_body), style = MaterialTheme.typography.bodyMedium)
                if (preview.soundKept) {
                    Text(
                        stringResource(R.string.backup_sound_kept),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("backup_confirm")) { Text(stringResource(R.string.backup_restore)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
