// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.status

import io.github.rhjihan.slackwake.core.schedule.WallClock
import io.github.rhjihan.slackwake.core.settings.AppSettings
import io.github.rhjihan.slackwake.core.status.MonitorStatus
import io.github.rhjihan.slackwake.core.status.StatusCalculator
import io.github.rhjihan.slackwake.data.settings.SettingsRepository
import io.github.rhjihan.slackwake.listener.SlackMonitor
import io.github.rhjihan.slackwake.prereq.PrereqState
import io.github.rhjihan.slackwake.prereq.PrerequisiteChecker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One source of truth for the status pill, the Quick Settings tile and app shortcuts,
 * so they always agree (Windows kept the tray icon and status dot in lockstep the same
 * way). Prerequisites are re-read on [refreshPrerequisites] — they change in system
 * settings, outside the app, so screens call it on resume.
 */
@Singleton
class StatusRepository
    @Inject
    constructor(
        private val settings: SettingsRepository,
        private val monitor: SlackMonitor,
        private val checker: PrerequisiteChecker,
        private val wallClock: WallClock,
    ) {
        private val _prerequisites = MutableStateFlow(checker.check())
        val prerequisites: StateFlow<PrereqState> = _prerequisites.asStateFlow()

        fun refreshPrerequisites() {
            _prerequisites.value = checker.check()
        }

        val status: Flow<MonitorStatus> =
            combine(settings.settings, monitor.connected, monitor.presenceChanges, _prerequisites, minuteTicks()) { s, connected, _, prereqs, _ ->
                compute(s, connected, prereqs)
            }

        /**
         * Emits now and at every minute boundary while collected, so a status on screen
         * flips exactly when a schedule window opens or closes. Nothing runs when nobody
         * is looking; alert decisions never depend on this (they read the clock directly).
         */
        private fun minuteTicks(): Flow<Unit> =
            flow {
                while (true) {
                    emit(Unit)
                    val now = wallClock.now()
                    delay(Duration.between(now, now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)).toMillis().coerceAtLeast(1))
                }
            }

        suspend fun snapshot(): MonitorStatus {
            refreshPrerequisites()
            return compute(settings.current(), monitor.connected.value, _prerequisites.value)
        }

        private fun compute(
            s: AppSettings,
            connected: Boolean,
            prereqs: PrereqState,
        ): MonitorStatus =
            StatusCalculator.compute(
                enabled = s.enabled,
                armed = monitor.isArmed(s),
                inSchedule = s.inSchedule(wallClock.now()),
                allowListActive = s.allowListActive,
                problems = prereqs.problems(connected),
            )
    }
