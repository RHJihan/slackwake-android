// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.listener

import android.app.Notification
import io.github.rhjihan.slackwake.alert.AlertController
import io.github.rhjihan.slackwake.core.gate.AlertGate
import io.github.rhjihan.slackwake.core.gate.AwayTracker
import io.github.rhjihan.slackwake.core.gate.Clock
import io.github.rhjihan.slackwake.core.gate.GateDecision
import io.github.rhjihan.slackwake.core.schedule.WallClock
import io.github.rhjihan.slackwake.core.settings.AppSettings
import io.github.rhjihan.slackwake.data.diagnostics.DiagnosticLog
import io.github.rhjihan.slackwake.data.settings.SettingsRepository
import io.github.rhjihan.slackwake.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** A posted notification, reduced to what the monitor needs (keeps it testable without a real StatusBarNotification). */
data class Posted(
    val key: String,
    val packageName: String,
    val notification: Notification,
)

/**
 * The Android counterpart of MainViewModel's gate + SlackMonitorService's filtering.
 * All entry points are called on the main thread (listener callbacks, the screen-state
 * receiver, and Main-dispatched coroutines), so the gate state needs no locking.
 */
@Singleton
class SlackMonitor
    @Inject
    constructor(
        clock: Clock,
        private val wallClock: WallClock,
        private val settingsRepository: SettingsRepository,
        private val deviceState: DeviceStateReader,
        private val alerts: AlertController,
        private val log: DiagnosticLog,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        private val tracker = AwayTracker(clock)
        private val gate = AlertGate(tracker)
        private val deduper = NotificationDeduper()

        private val _connected = MutableStateFlow(false)

        /** True while the system has the listener bound. */
        val connected: StateFlow<Boolean> = _connected.asStateFlow()

        private val _presenceChanges = MutableStateFlow(0L)

        /** Bumps on every screen/unlock transition so status observers can recompute. */
        val presenceChanges: StateFlow<Long> = _presenceChanges.asStateFlow()

        fun onConnected(active: List<Posted>) {
            _connected.value = true
            tracker.initialize(deviceState.read())
            deduper.clear()
            // Baseline what is already in the shade so a later repost with unchanged
            // content does not replay as a new ping (Windows' startup "first pass").
            var seeded = 0
            for (p in active) {
                val r = NotificationParser.parse(p.packageName, p.notification)
                if (r is ParseResult.Event) {
                    deduper.seed(p.key, r.event)
                    seeded++
                }
            }
            log.write("Listener connected; baseline=$seeded away=${tracker.awaySinceMs != null}")
            bump()
        }

        fun onDisconnected() {
            _connected.value = false
            log.write("Listener disconnected; requesting rebind")
            bump()
        }

        fun onScreenOff() {
            gate.onScreenOff()
            bump()
        }

        fun onScreenOn() {
            val locked = deviceState.read().keyguardLocked
            gate.onScreenOn(locked)
            bump()
        }

        fun onUserPresent() {
            gate.onUserPresent()
            bump()
        }

        fun onRemoved(key: String) = deduper.onRemoved(key)

        fun onPosted(posted: Posted) {
            // Non-Slack notifications are dropped here, before anything is parsed or
            // logged: SlackWake never records anything about other apps.
            if (posted.packageName != SLACK_PACKAGE) return

            val event =
                when (val r = NotificationParser.parse(posted.packageName, posted.notification)) {
                    is ParseResult.Skip -> {
                        log.write("Slack notification skipped (${r.reason})")
                        return
                    }

                    is ParseResult.Event -> {
                        r.event
                    }
                }
            if (!deduper.isNew(posted.key, event)) {
                log.write("Slack notification skipped (unchanged repost)")
                return
            }
            log.content { "Slack event sender='${event.sender}' channel='${event.channel}' text='${event.text}'" }

            scope.launch {
                val settings = settingsRepository.current()
                val device = deviceState.read()
                when (val d = gate.evaluate(event, settings, device, alerts.isShowing, wallClock.now())) {
                    GateDecision.Fire -> {
                        log.write("Slack ping -> alert")
                        alerts.fire(event, settings, isTest = false)
                    }

                    is GateDecision.Suppress -> {
                        log.write("Slack ping suppressed (${d.reason})")
                    }
                }
                bump()
            }
        }

        /** For the tile / status pill. */
        fun isArmed(settings: AppSettings): Boolean = _connected.value && gate.isArmed(settings, deviceState.read(), wallClock.now())

        private fun bump() = _presenceChanges.update { it + 1 }
    }
