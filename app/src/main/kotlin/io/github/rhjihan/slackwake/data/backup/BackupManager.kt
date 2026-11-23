// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.data.backup

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.rhjihan.slackwake.BuildConfig
import io.github.rhjihan.slackwake.core.backup.SettingsBackup
import io.github.rhjihan.slackwake.core.settings.AppSettings
import io.github.rhjihan.slackwake.data.diagnostics.DiagnosticLog
import io.github.rhjihan.slackwake.data.settings.SettingsRepository
import io.github.rhjihan.slackwake.sound.SoundResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** A decoded backup waiting for the user's confirmation. */
data class RestorePreview(
    val meta: SettingsBackup.Meta,
    /** What the settings will be after restoring (device-local fields already kept). */
    val merged: AppSettings,
    /** The backup's alert sound can't be played here, so the current sound is kept. */
    val soundKept: Boolean,
)

sealed interface ReadResult {
    data class Ok(
        val preview: RestorePreview,
    ) : ReadResult

    data class Failed(
        val reason: SettingsBackup.Failure?,
    ) : ReadResult // null = the file could not be opened/read
}

/**
 * Export/import of settings through Storage Access Framework URIs (the system "Save as"
 * and file pickers): no storage permission, no network, and the user chooses where the
 * file lives. Nothing is written until the user confirms a restore, and a failed read
 * never touches the current settings. The log records outcomes, never setting values.
 */
@Singleton
class BackupManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val settings: SettingsRepository,
        private val sounds: SoundResolver,
        private val log: DiagnosticLog,
    ) {
        suspend fun export(uri: Uri): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    val text = SettingsBackup.encode(settings.current(), BuildConfig.VERSION_NAME, Instant.now())
                    // "wt" truncates, so overwriting a larger old file leaves no trailing bytes.
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.encodeToByteArray()) }
                        ?: throw IOException("no output stream")
                    log.write("Settings backup written")
                    true
                } catch (e: IOException) {
                    log.write("Settings backup failed: ${e.javaClass.simpleName}")
                    false
                } catch (e: SecurityException) {
                    log.write("Settings backup failed: ${e.javaClass.simpleName}")
                    false
                }
            }

        suspend fun read(uri: Uri): ReadResult =
            withContext(Dispatchers.IO) {
                val text =
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            // Read at most one byte past the limit: enough to detect "too large".
                            val bytes = input.readNBytesCompat(SettingsBackup.MAX_BYTES + 1)
                            bytes.decodeToString()
                        } ?: return@withContext ReadResult.Failed(null)
                    } catch (e: IOException) {
                        log.write("Settings restore: could not read file (${e.javaClass.simpleName})")
                        return@withContext ReadResult.Failed(null)
                    } catch (e: SecurityException) {
                        log.write("Settings restore: could not read file (${e.javaClass.simpleName})")
                        return@withContext ReadResult.Failed(null)
                    }

                when (val r = SettingsBackup.decode(text)) {
                    is SettingsBackup.DecodeResult.Failure -> {
                        log.write("Settings restore refused: ${r.reason}")
                        ReadResult.Failed(r.reason)
                    }

                    is SettingsBackup.DecodeResult.Success -> {
                        val current = settings.current()
                        var merged = SettingsBackup.applyRestore(current, r.settings)
                        // Sound URIs are device-specific (media IDs, document grants).
                        val soundKept = merged.soundUri.isNotBlank() && !sounds.isReadable(merged.soundUri.toUri())
                        if (soundKept) merged = merged.copy(soundUri = current.soundUri)
                        ReadResult.Ok(RestorePreview(r.meta, merged, soundKept))
                    }
                }
            }

        /** Apply a confirmed restore in one atomic write; returns the previous settings for Undo. */
        suspend fun apply(preview: RestorePreview): AppSettings {
            val previous = settings.current()
            settings.update { preview.merged }
            log.write("Settings restored (${preview.meta.source})")
            return previous
        }

        suspend fun undo(previous: AppSettings) {
            settings.update { previous }
            log.write("Settings restore undone")
        }
    }

/** InputStream.readNBytes is API 33+; this reads up to [limit] bytes on any API level. */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8 * 1024)
    while (out.size() < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
