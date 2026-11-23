// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.data.diagnostics

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Capped, rotating diagnostic log in app-private storage (never backed up; see
 * data_extraction_rules.xml). Two files of [maxFileBytes] each — `diagnostic.log` and
 * the previous `diagnostic.log.1` — so the total stays at ~256 KB and rotation keeps the
 * most recent history instead of wiping it (Windows truncated to empty).
 *
 * Privacy: callers log decisions and metadata only. Notification title/body go through
 * [content], which is dropped unless "Verbose diagnostics" is on.
 *
 * Writes happen on a private single thread so callers (including the main thread) never
 * block on disk I/O. Logging never throws into the app.
 */
class DiagnosticLog(
    private val dir: File,
    private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "SlackWake-log").apply { isDaemon = true } }
    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile var verbose: Boolean = false

    val current: File get() = File(dir, FILE_NAME)
    val previous: File get() = File(dir, "$FILE_NAME.1")

    fun write(message: String) {
        val line = "${stamp.format(Date(now()))} $message\n"
        executor.execute { append(line) }
    }

    /** Message containing notification content: only recorded with verbose diagnostics on. */
    fun content(message: () -> String) {
        if (verbose) write("[verbose] ${message()}")
    }

    /** Oldest-first text of both files. Blocks; call off the main thread. */
    fun readAll(): String {
        flush()
        return listOf(previous, current).filter { it.exists() }.joinToString("") { runCatching { it.readText() }.getOrDefault("") }
    }

    fun clear() {
        executor.execute {
            runCatching { current.delete() }
            runCatching { previous.delete() }
        }
        flush()
    }

    /** Wait for queued writes (tests, share). */
    fun flush() {
        runCatching { executor.submit {}.get(2, TimeUnit.SECONDS) }
    }

    private fun append(line: String) {
        runCatching {
            dir.mkdirs()
            val file = current
            if (file.exists() && file.length() + line.length > maxFileBytes) {
                previous.delete()
                file.renameTo(previous)
            }
            file.appendText(line)
        }
    }

    companion object {
        const val FILE_NAME = "diagnostic.log"
        const val DEFAULT_MAX_FILE_BYTES = 128L * 1024
    }
}
