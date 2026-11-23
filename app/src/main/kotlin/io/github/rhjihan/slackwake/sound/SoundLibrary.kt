// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.sound

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.rhjihan.slackwake.data.diagnostics.DiagnosticLog
import javax.inject.Inject
import javax.inject.Singleton

/** Groups in the sound picker. Replaces the Windows C:\Windows\Media enumeration. */
enum class SoundGroup { DEFAULT, ALARM, NOTIFICATION, RINGTONE, CUSTOM }

/** One row of the sound picker. [uri] is what gets stored; "" = system default alarm. */
data class SoundOption(
    val title: String,
    val uri: String,
    val group: SoundGroup,
)

@Singleton
class SoundLibrary
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val resolver: SoundResolver,
        private val log: DiagnosticLog,
    ) {
        /** Every system alarm, notification and ringtone sound, de-duplicated by URI. */
        fun enumerate(): List<SoundOption> {
            val seen = HashSet<String>()
            return listOf(
                RingtoneManager.TYPE_ALARM to SoundGroup.ALARM,
                RingtoneManager.TYPE_NOTIFICATION to SoundGroup.NOTIFICATION,
                RingtoneManager.TYPE_RINGTONE to SoundGroup.RINGTONE,
            ).flatMap { (type, group) -> list(type, group) }
                .filter { seen.add(it.uri) }
        }

        private fun list(
            type: Int,
            group: SoundGroup,
        ): List<SoundOption> =
            runCatching {
                val manager = RingtoneManager(context).apply { setType(type) }
                val cursor = manager.cursor
                buildList {
                    while (cursor.moveToNext()) {
                        val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX) ?: continue
                        add(SoundOption(title, manager.getRingtoneUri(cursor.position).toString(), group))
                    }
                }
            }.getOrElse {
                log.write("Ringtone enumeration failed for type=$type: ${it.javaClass.simpleName}")
                emptyList()
            }

        /** Human label for a stored URI (system sound title, file name, or "Default alarm sound"). */
        fun label(
            stored: String,
            defaultLabel: String,
        ): String {
            if (stored.isBlank()) return defaultLabel
            val uri = stored.toUri()
            displayName(uri)?.let { return it }
            return runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull() ?: stored
        }

        /** File name of a user-picked document, if it is one. */
        fun displayName(uri: Uri): String? =
            if (uri.scheme != "content" || uri.authority == "media") {
                null
            } else {
                runCatching {
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    }
                }.getOrNull()
            }

        /**
         * Keep read access to a file picked with ACTION_OPEN_DOCUMENT across reboots, and
         * release the grant for a previously picked file so grants do not pile up.
         */
        fun adoptPickedFile(
            uri: Uri,
            previous: String,
        ): Boolean {
            val ok =
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    true
                }.getOrElse {
                    log.write("Could not persist access to the picked sound: ${it.javaClass.simpleName}")
                    false
                }
            if (ok && previous.isNotBlank() && previous != uri.toString()) release(previous)
            return ok && resolver.isReadable(uri)
        }

        fun release(stored: String) {
            val uri = stored.toUri()
            if (context.contentResolver.persistedUriPermissions.any { it.uri == uri }) {
                runCatching { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            }
        }

        /** True for a stored URI that came from "Choose file…" rather than the system list. */
        fun isCustom(stored: String): Boolean = stored.isNotBlank() && displayName(stored.toUri()) != null
    }
