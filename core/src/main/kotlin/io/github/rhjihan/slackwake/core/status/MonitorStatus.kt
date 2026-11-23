// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.core.status

/**
 * Tile subtitle / status-pill state. Windows called these Active / Idle / Disabled
 * (from SlackWake's own perspective); Android uses Armed / Paused / Disabled.
 */
enum class MonitorState { ARMED, PAUSED, DISABLED }

/** A prerequisite that stops alerts from working as intended, most severe first. */
enum class Problem {
    /** Notification listener access not granted: nothing can be detected. */
    LISTENER_ACCESS_MISSING,

    /** Access granted but the system has not (re)bound the listener yet. */
    LISTENER_DISCONNECTED,

    /** POST_NOTIFICATIONS denied (13+): only the sound will alert. */
    NOTIFICATIONS_BLOCKED,

    /** Alerts channel turned off/down by the user in system settings. */
    ALERT_CHANNEL_BLOCKED,

    /** Full-screen intent not allowed (14+): alerts fall back to a heads-up notification. */
    FULL_SCREEN_DENIED,
}

/** Which status sentence to show; mapped to string resources by the app. */
enum class StatusDetail {
    DISABLED,

    /** Port of "Idle: alerts paused while using the computer". */
    PAUSED_PRESENT,

    /** Android-only: monitoring is on but the current time is outside the schedule. */
    PAUSED_OFF_SCHEDULE,

    /** Port of "Active: overlay armed for the next Slack ping". */
    ARMED_ALL,

    /** Port of "Active: overlay armed only for pings matching your keywords". */
    ARMED_KEYWORDS_ONLY,
}

data class MonitorStatus(
    val state: MonitorState,
    val detail: StatusDetail,
    /** Most severe outstanding problem, surfaced instead of / next to the detail. */
    val problem: Problem?,
)

/** Port of MainViewModel.ComputeStatusText. */
object StatusCalculator {
    fun compute(
        enabled: Boolean,
        armed: Boolean,
        allowListActive: Boolean,
        problems: Collection<Problem>,
        inSchedule: Boolean = true,
    ): MonitorStatus {
        if (!enabled) return MonitorStatus(MonitorState.DISABLED, StatusDetail.DISABLED, null)
        val problem = Problem.entries.firstOrNull { it in problems }
        return when {
            !inSchedule -> MonitorStatus(MonitorState.PAUSED, StatusDetail.PAUSED_OFF_SCHEDULE, problem)
            !armed -> MonitorStatus(MonitorState.PAUSED, StatusDetail.PAUSED_PRESENT, problem)
            allowListActive -> MonitorStatus(MonitorState.ARMED, StatusDetail.ARMED_KEYWORDS_ONLY, problem)
            else -> MonitorStatus(MonitorState.ARMED, StatusDetail.ARMED_ALL, problem)
        }
    }
}

/** Minutes/seconds breakdown for the auto-stop caption (Windows FormatDuration). */
data class DurationParts(
    val minutes: Int,
    val seconds: Int,
) {
    companion object {
        fun of(totalSeconds: Int) = DurationParts(totalSeconds / 60, totalSeconds % 60)

        /** Only worth spelling out past a minute (ShowAlertMaxDurationFriendly). */
        fun showFriendly(totalSeconds: Int) = totalSeconds > 60
    }
}
