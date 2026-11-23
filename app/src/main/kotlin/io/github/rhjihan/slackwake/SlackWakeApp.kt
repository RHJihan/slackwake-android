// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake

import android.app.Application
import android.content.ComponentName
import android.service.quicksettings.TileService
import dagger.hilt.android.HiltAndroidApp
import io.github.rhjihan.slackwake.alert.AlertController
import io.github.rhjihan.slackwake.alert.AlertNotifier
import io.github.rhjihan.slackwake.data.diagnostics.DiagnosticLog
import io.github.rhjihan.slackwake.data.settings.SettingsRepository
import io.github.rhjihan.slackwake.di.ApplicationScope
import io.github.rhjihan.slackwake.shortcuts.ShortcutPublisher
import io.github.rhjihan.slackwake.tile.MonitorTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SlackWakeApp : Application() {
    @Inject lateinit var notifier: AlertNotifier

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var alerts: AlertController

    @Inject lateinit var log: DiagnosticLog

    @Inject lateinit var shortcuts: ShortcutPublisher

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannel()
        scope.launch {
            settings.settings.collect { s ->
                log.verbose = s.verboseDiagnostics
                // Windows' master switch stopped everything; an alert must not outlive it.
                if (!s.enabled) alerts.dismiss("monitoring disabled")
                shortcuts.publish(s)
                runCatching {
                    TileService.requestListeningState(
                        this@SlackWakeApp,
                        ComponentName(this@SlackWakeApp, MonitorTileService::class.java),
                    )
                }
            }
        }
    }
}
