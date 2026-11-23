// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.sound

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.rhjihan.slackwake.data.diagnostics.DiagnosticLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Port of SoundLibrary.ResolvePlayablePath: turns the stored selection into something
 * playable. Blank → system default alarm; a saved URI that can no longer be opened (file
 * deleted, permission revoked, restored from backup) → default alarm, logged.
 */
@Singleton
class SoundResolver
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val log: DiagnosticLog,
    ) {
        fun defaultAlarmUri(): Uri =
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_NOTIFICATION)
                ?: Settings.System.DEFAULT_ALARM_ALERT_URI

        fun resolve(stored: String): Uri {
            if (stored.isBlank()) return defaultAlarmUri()
            val uri = stored.toUri()
            return if (isReadable(uri)) {
                uri
            } else {
                log.write("Saved sound is unreadable; falling back to the default alarm sound")
                defaultAlarmUri()
            }
        }

        fun isReadable(uri: Uri): Boolean =
            runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
            }.getOrDefault(false)
    }
