// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.core.settings

import io.github.rhjihan.slackwake.core.color.ColorUtil
import io.github.rhjihan.slackwake.core.schedule.DayWindow
import io.github.rhjihan.slackwake.core.schedule.ScheduleZones
import io.github.rhjihan.slackwake.core.schedule.WeeklySchedule
import io.github.rhjihan.slackwake.core.vibration.VibrationMode
import io.github.rhjihan.slackwake.core.vibration.VibrationPattern
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Persisted settings. Port of the Windows `AppSettings` POCO: every key, default and
 * clamp is kept (JSON names are identical, so a settings file reads the same on both
 * platforms), except:
 *  - `StartWithWindows` / `StartMinimized` are dropped (the system rebinds the
 *    notification listener after boot; there is nothing to auto-start);
 *  - `SoundFilePath` is renamed [soundUri] and holds a content/android.resource URI;
 *  - the flash floor is raised from 100 ms to 200 ms per half-cycle (WCAG 2.3.1);
 *  - [verboseDiagnostics], [onboardingCompleted] and the vibration settings are
 *    Android-only additions.
 *
 * Instances coming from storage or the UI must go through [normalized] — the
 * repository does this on both read and write.
 */
@Serializable
data class AppSettings(
    /** Master kill-switch. When false nothing is monitored. */
    @SerialName("Enabled") val enabled: Boolean = true,
    /** How long the device must be locked (away) before Slack pings fire alerts. */
    @SerialName("IdleTimeoutSeconds") val idleTimeoutSeconds: Int = 300,
    /** Play an audible alert in addition to the fullscreen overlay. */
    @SerialName("SoundEnabled") val soundEnabled: Boolean = true,
    /** Delay after the overlay appears before sound, vibration and flash start. 0 = immediate. */
    @SerialName("SoundDelaySeconds") val soundDelaySeconds: Int = 5,
    /** Replay the sound (0.5 s gap) until dismissed or auto-stopped. */
    @SerialName("SoundLoop") val soundLoop: Boolean = true,
    /** Alert sound URI. Blank, or unreadable, means the system default alarm sound. */
    @SerialName("SoundUri") val soundUri: String = "",
    /** Strobe the overlay between two colors. On by default (parity with Windows). */
    @SerialName("FlashEnabled") val flashEnabled: Boolean = true,
    /** Half-cycle of the flash (one color to the other), in ms. */
    @SerialName("FlashIntervalMs") val flashIntervalMs: Int = 500,
    @SerialName("FlashColorA") val flashColorA: String = "#000000",
    @SerialName("FlashColorB") val flashColorB: String = "#FFFFFF",
    /** Master switch for the auto-stop cap on continuous alerts. */
    @SerialName("AlertAutoStopEnabled") val alertAutoStopEnabled: Boolean = true,
    /** Cap in seconds; measured from when sound/flash actually start (after the delay). */
    @SerialName("AlertMaxDurationSeconds") val alertMaxDurationSeconds: Int = 600,
    @SerialName("AlertAutoStopIncludesSound") val alertAutoStopIncludesSound: Boolean = true,
    @SerialName("AlertAutoStopIncludesVisual") val alertAutoStopIncludesVisual: Boolean = true,
    /** Android-only: the auto-stop cap also stops the vibration. */
    @SerialName("AlertAutoStopIncludesVibration") val alertAutoStopIncludesVibration: Boolean = true,
    /**
     * Android-only: vibrate with the alert (alarm usage, so it works in silent mode).
     * On by default, like alarm apps; waits out the same alert delay as sound and flash.
     */
    @SerialName("VibrationEnabled") val vibrationEnabled: Boolean = true,
    /** [VibrationPattern.key]; unknown values fall back to "Alert". */
    @SerialName("VibrationPattern") val vibrationPattern: String = VibrationPattern.DEFAULT.key,
    /** Peak motor strength in percent. Ignored on devices without amplitude control. */
    @SerialName("VibrationIntensityPercent") val vibrationIntensityPercent: Int = 100,
    /** [VibrationMode.key]: "Fixed" (constant) or "Gradual" (crescendo). */
    @SerialName("VibrationMode") val vibrationMode: String = VibrationMode.FIXED.key,
    /** Gradual mode: seconds to build from 15% to the chosen intensity. */
    @SerialName("VibrationRampSeconds") val vibrationRampSeconds: Int = 30,
    /** Mute-by-keyword (block-list). */
    @SerialName("KeywordFilterEnabled") val keywordFilterEnabled: Boolean = false,
    @SerialName("KeywordFilterText") val keywordFilterText: String = "",
    /** Alert-only-by-keyword (allow-list). An empty list is inert. */
    @SerialName("KeywordAllowEnabled") val keywordAllowEnabled: Boolean = false,
    @SerialName("KeywordAllowText") val keywordAllowText: String = "",
    /** Android-only: log notification title/body to the diagnostic log. Off by default. */
    @SerialName("VerboseDiagnostics") val verboseDiagnostics: Boolean = false,
    /** Android-only: first-run onboarding has been completed or skipped. */
    @SerialName("OnboardingCompleted") val onboardingCompleted: Boolean = false,
    /**
     * Android-only: the user confirmed Slack's own mobile notifications are on. Android
     * can't read another app's notification settings, so this is a manual checklist tick.
     */
    @SerialName("SlackNotificationsConfirmed") val slackNotificationsConfirmed: Boolean = false,
    /** Android-only: the user ticked "Don't show again" on the photosensitivity warning. */
    @SerialName("PhotosensitivityWarningDismissed") val photosensitivityWarningDismissed: Boolean = false,
    /**
     * Android-only: only alert inside the per-day windows in [scheduleDays]. Checked when a
     * ping arrives (no alarms/timers), so it is exact and unaffected by Doze.
     */
    @SerialName("ScheduleEnabled") val scheduleEnabled: Boolean = false,
    /** Monday..Sunday windows; a window ending at/before its start runs into the next day. */
    @SerialName("ScheduleDays") val scheduleDays: List<DayWindow> = WeeklySchedule.DEFAULT,
    /**
     * IANA zone ID (e.g. "America/New_York") the schedule's times are in; "" = follow the
     * phone's zone. Stored as an ID, not an offset, so the zone's DST rules apply.
     */
    @SerialName("ScheduleTimeZone") val scheduleTimeZone: String = "",
) {
    /** Clamp every numeric field and sanitize colors to `#RRGGBB`. Idempotent. */
    fun normalized(): AppSettings =
        copy(
            idleTimeoutSeconds = idleTimeoutSeconds.coerceIn(SettingsClamps.IDLE_TIMEOUT_SECONDS),
            soundDelaySeconds = soundDelaySeconds.coerceIn(SettingsClamps.SOUND_DELAY_SECONDS),
            flashIntervalMs = flashIntervalMs.coerceIn(SettingsClamps.FLASH_INTERVAL_MS),
            alertMaxDurationSeconds = alertMaxDurationSeconds.coerceIn(SettingsClamps.ALERT_MAX_DURATION_SECONDS),
            flashColorA = ColorUtil.sanitizeHex(flashColorA),
            flashColorB = ColorUtil.sanitizeHex(flashColorB),
            vibrationPattern = VibrationPattern.fromKey(vibrationPattern).key,
            vibrationMode = VibrationMode.fromKey(vibrationMode).key,
            vibrationIntensityPercent = vibrationIntensityPercent.coerceIn(SettingsClamps.VIBRATION_INTENSITY_PERCENT),
            vibrationRampSeconds = vibrationRampSeconds.coerceIn(SettingsClamps.VIBRATION_RAMP_SECONDS),
            scheduleDays = WeeklySchedule.normalized(scheduleDays),
            scheduleTimeZone = ScheduleZones.sanitize(scheduleTimeZone),
        )

    /** The zone the schedule's times are in, given the phone's current zone. */
    fun scheduleZone(device: ZoneId): ZoneId = ScheduleZones.resolve(scheduleTimeZone, device)

    /** [now] converted to the schedule's own wall-clock time. */
    fun scheduleLocalTime(now: ZonedDateTime): LocalDateTime = now.withZoneSameInstant(scheduleZone(now.zone)).toLocalDateTime()

    /** True when the schedule (if any) allows alerts at the instant [now]. */
    fun inSchedule(now: ZonedDateTime): Boolean = !scheduleEnabled || WeeklySchedule.isActive(scheduleDays, scheduleLocalTime(now))

    /** True when the allow-list is on AND has text — only then does it restrict anything. */
    val allowListActive: Boolean
        get() = keywordAllowEnabled && keywordAllowText.isNotBlank()

    /** Both filters switched on: the UI explains how they combine (muting wins). */
    val showFilterInteractionNote: Boolean
        get() = keywordFilterEnabled && keywordAllowEnabled

    /** Auto-stop can only do something while sound or flash is on. */
    val canAutoStopAlerts: Boolean
        get() = soundEnabled || flashEnabled || vibrationEnabled
}

/** Bounds shared by the repository (read + write), the UI and the alert pipeline. */
object SettingsClamps {
    /** Windows: 10–7200 s; the slider only exposes 10–1800 s. */
    val IDLE_TIMEOUT_SECONDS = 10..7200
    val IDLE_TIMEOUT_SLIDER = 10..1800

    val SOUND_DELAY_SECONDS = 0..120

    /**
     * Windows allowed 100 ms, i.e. 5 full flashes/s — over the WCAG 2.3.1 general flash
     * limit of 3/s. 200 ms per half-cycle caps it at 2.5 flashes/s.
     */
    val FLASH_INTERVAL_MS = 200..2000

    val ALERT_MAX_DURATION_SECONDS = 5..3600

    /** Below 10% most motors are imperceptible. */
    val VIBRATION_INTENSITY_PERCENT = 10..100

    /** Gradual vibration ramp, matching the range of alarm apps' "gradually increase". */
    val VIBRATION_RAMP_SECONDS = 5..120
}
