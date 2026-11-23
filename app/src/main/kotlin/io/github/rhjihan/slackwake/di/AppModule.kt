// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.di

import android.content.Context
import android.os.SystemClock
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.rhjihan.slackwake.core.gate.Clock
import io.github.rhjihan.slackwake.core.schedule.WallClock
import io.github.rhjihan.slackwake.core.settings.AppSettings
import io.github.rhjihan.slackwake.data.diagnostics.DiagnosticLog
import io.github.rhjihan.slackwake.data.settings.SettingsSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.time.ZonedDateTime
import javax.inject.Qualifier
import javax.inject.Singleton

/** Process-lifetime scope for work that must outlive any one screen or service binding. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Provides @Singleton
    fun clock(): Clock = Clock { SystemClock.elapsedRealtime() }

    /** Wall-clock time in the device's current time zone, for the monitoring schedule. */
    @Provides @Singleton
    fun wallClock(): WallClock = WallClock { ZonedDateTime.now() }

    @Provides @Singleton
    fun diagnosticLog(
        @ApplicationContext context: Context,
    ): DiagnosticLog = DiagnosticLog(File(context.filesDir, "logs"))

    @Provides @Singleton
    fun settingsDataStore(
        @ApplicationContext context: Context,
        log: DiagnosticLog,
    ): DataStore<AppSettings> =
        DataStoreFactory.create(
            serializer = SettingsSerializer,
            corruptionHandler =
                ReplaceFileCorruptionHandler { e ->
                    log.write("Settings file corrupt (${e.message}); reset to defaults")
                    AppSettings()
                },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { context.dataStoreFile(SETTINGS_FILE) },
        )

    const val SETTINGS_FILE = "settings.json"
}
