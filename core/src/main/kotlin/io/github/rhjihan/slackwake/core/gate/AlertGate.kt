// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.core.gate

import io.github.rhjihan.slackwake.core.filter.FilterResult
import io.github.rhjihan.slackwake.core.filter.KeywordFilter
import io.github.rhjihan.slackwake.core.model.SlackEvent
import io.github.rhjihan.slackwake.core.settings.AppSettings
import java.time.ZonedDateTime

sealed interface GateDecision {
    data object Fire : GateDecision

    data class Suppress(
        val reason: String,
    ) : GateDecision
}

/**
 * Decides whether a Slack ping becomes an alert. Port of MainViewModel.OnSlackNotification
 * plus two Android rules:
 *  - one alert at a time: pings arriving while an alert is showing are dropped;
 *  - re-arm only after an unlock: once an alert has fired, nothing fires again until the
 *    user unlocks and then becomes away again (Windows re-armed as soon as input stopped,
 *    but an alert dismissed on the lock screen does not prove the user came back).
 *
 * Order matches Windows: enabled → away → keyword filters (cheapest, most common first).
 */
class AlertGate(
    private val tracker: AwayTracker,
) {
    /** Set when a real alert fires; cleared by an unlock. */
    var rearmPending: Boolean = false
        private set

    // Broadcast entry points. Presence (an unlock, or screen-on without a keyguard)
    // both ends the away period and re-arms.

    fun onScreenOff() = tracker.onScreenOff()

    fun onScreenOn(keyguardLocked: Boolean) {
        tracker.onScreenOn(keyguardLocked)
        if (!keyguardLocked) rearmPending = false
    }

    fun onUserPresent() {
        rearmPending = false
        tracker.onUserPresent()
    }

    /** Current status for the tile / status pill (no side effects). */
    fun isArmed(
        settings: AppSettings,
        device: DeviceState,
        now: ZonedDateTime,
    ): Boolean = settings.enabled && settings.inSchedule(now) && !rearmPending && tracker.isAway(device, settings.idleTimeoutSeconds)

    fun evaluate(
        event: SlackEvent,
        settings: AppSettings,
        device: DeviceState,
        alertShowing: Boolean,
        now: ZonedDateTime,
    ): GateDecision {
        if (!settings.enabled) return GateDecision.Suppress("disabled")
        if (!settings.inSchedule(now)) return GateDecision.Suppress("outside the monitoring schedule")
        if (alertShowing) return GateDecision.Suppress("an alert is already showing")
        if (!tracker.isAway(device, settings.idleTimeoutSeconds)) {
            return GateDecision.Suppress("not away (away for ${tracker.awayForMs(device) / 1000}s, need ${settings.idleTimeoutSeconds}s)")
        }
        if (rearmPending) return GateDecision.Suppress("already alerted; waiting for unlock")
        return when (val f = KeywordFilter.evaluate(event, settings)) {
            FilterResult.Allowed -> {
                rearmPending = true
                GateDecision.Fire
            }

            is FilterResult.Suppressed -> {
                GateDecision.Suppress(f.reason)
            }
        }
    }
}
