// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.alert

import io.github.rhjihan.slackwake.core.alert.AlertPlan
import io.github.rhjihan.slackwake.core.alert.AlertSession
import io.github.rhjihan.slackwake.core.model.SlackEvent
import io.github.rhjihan.slackwake.core.settings.AppSettings
import io.github.rhjihan.slackwake.data.diagnostics.DiagnosticLog
import io.github.rhjihan.slackwake.di.ApplicationScope
import io.github.rhjihan.slackwake.sound.AlertSoundSource
import io.github.rhjihan.slackwake.vibration.AlertVibrationSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** The alert currently on screen. */
data class ActiveAlert(
    val id: Long,
    val event: SlackEvent,
    val plan: AlertPlan,
    val isTest: Boolean,
)

/**
 * Owns the one alert that may exist at a time — Windows' `_overlayOpen` guard plus the
 * per-overlay sound/flash timers, moved out of the window so that:
 *
 *  - the sound and vibration ALWAYS play (after the alert delay, looping/capped per
 *    settings) even when the full-screen screen cannot be shown — no full-screen-intent permission,
 *    notifications blocked, or the activity failing to launch;
 *  - the notification is ALWAYS posted (full-screen intent when allowed, otherwise a
 *    heads-up with a Dismiss action), so there is always a way to stop the sound;
 *  - every surface (alert screen, presentations on other displays, the notification's
 *    Dismiss action / swipe, disabling monitoring) funnels into one [dismiss].
 */
@Singleton
class AlertController
    @Inject
    constructor(
        private val notifier: AlertNotifier,
        private val sounds: AlertSoundSource,
        private val vibrations: AlertVibrationSource,
        private val log: DiagnosticLog,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        private val _active = MutableStateFlow<ActiveAlert?>(null)
        val active: StateFlow<ActiveAlert?> = _active.asStateFlow()

        private val sessionFlow = MutableStateFlow<AlertSession?>(null)
        private var focusJob: Job? = null
        private var screenJob: Job? = null

        private val _keepScreenOn = MutableStateFlow(false)

        /**
         * True while the alert should hold the screen on. Drops to false once auto-stop has
         * ended everything; the alert and its notification stay until dismissed.
         */
        val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()
        private var nextId = 1L

        @OptIn(ExperimentalCoroutinesApi::class)
        val flashing: StateFlow<Boolean> =
            sessionFlow
                .flatMapLatest { it?.flashing ?: flowOf(false) }
                .stateIn(scope, SharingStarted.Eagerly, false)

        val isShowing: Boolean get() = _active.value != null

        /**
         * Start an alert. Returns false (and does nothing) if one is already showing —
         * pings arriving during an alert are dropped, as on Windows.
         */
        fun fire(
            event: SlackEvent,
            settings: AppSettings,
            isTest: Boolean,
        ): Boolean {
            if (_active.value != null) {
                log.write("Alert dropped: one already showing")
                return false
            }
            val plan = AlertPlan.from(settings)
            val alert = ActiveAlert(nextId++, event, plan, isTest)
            val session = AlertSession(plan, sounds.create(plan.soundUri), scope, vibrations.create())
            _active.value = alert
            sessionFlow.value = session

            focusJob =
                scope.launch {
                    session.soundPlaying.collect { playing -> if (playing) sounds.acquireFocus() else sounds.releaseFocus() }
                }

            // A test alert opens the screen directly: its notification is quiet from the start.
            val route = notifier.post(alert, quiet = isTest)
            screenShown = false
            log.write("Alert ${alert.id} started (test=$isTest, route=$route)")
            session.start()

            _keepScreenOn.value = true
            plan.keepScreenOnFor?.let { hold ->
                screenJob =
                    scope.launch {
                        delay(hold)
                        _keepScreenOn.value = false
                        log.write("Alert ${alert.id}: auto-stop ended everything; screen may time out")
                    }
            }
            return true
        }

        private var screenShown = false

        /**
         * The alert screen became visible. The overlay is the alert now, so the notification
         * moves to the quiet channel: no heads-up pop-up over it, shade entry kept.
         */
        fun onAlertScreenShown() {
            val alert = _active.value ?: return
            if (screenShown) return
            screenShown = true
            notifier.showQuietly(alert)
        }

        fun dismiss(source: String) {
            val alert = _active.value ?: return
            sessionFlow.value?.stop()
            sessionFlow.value = null
            focusJob?.cancel()
            focusJob = null
            screenJob?.cancel()
            screenJob = null
            _keepScreenOn.value = false
            sounds.releaseFocus()
            vibrations.cancel()
            notifier.cancel()
            _active.value = null
            log.write("Alert ${alert.id} dismissed via $source")
        }
    }
