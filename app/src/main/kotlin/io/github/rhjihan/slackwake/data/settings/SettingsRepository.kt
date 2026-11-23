// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.data.settings

import androidx.datastore.core.DataStore
import io.github.rhjihan.slackwake.core.schedule.TimeZoneCatalog
import io.github.rhjihan.slackwake.core.settings.AppSettings
import io.github.rhjihan.slackwake.data.diagnostics.DiagnosticLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Typed settings access. Every value is clamped on read and on write, and the schedule
 * zone is kept under its canonical ID (see [TimeZoneCatalog]); every change is persisted
 * immediately (Windows saved on every property change too).
 */
@Singleton
class SettingsRepository
    @Inject
    constructor(
        private val store: DataStore<AppSettings>,
        private val log: DiagnosticLog,
        private val zones: TimeZoneCatalog,
    ) {
        val settings: Flow<AppSettings> =
            store.data
                .catch { e ->
                    // Disk I/O failure: settings are advisory, never load-bearing.
                    if (e is IOException) {
                        log.write("Settings read failed: ${e.javaClass.simpleName}; using defaults")
                        emit(AppSettings())
                    } else {
                        throw e
                    }
                }.map { it.normalized(zones) }
                .distinctUntilChanged()

        suspend fun current(): AppSettings = settings.first()

        suspend fun update(transform: (AppSettings) -> AppSettings) {
            try {
                store.updateData { transform(it.normalized(zones)).normalized(zones) }
            } catch (e: IOException) {
                log.write("Settings write failed: ${e.javaClass.simpleName}")
            }
        }
    }
