// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.core.backup

import io.github.rhjihan.slackwake.core.backup.SettingsBackup.DecodeResult
import io.github.rhjihan.slackwake.core.backup.SettingsBackup.Failure
import io.github.rhjihan.slackwake.core.schedule.DayWindow
import io.github.rhjihan.slackwake.core.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Start of a schema-1 backup, up to the exportedAt value. */
private const val V1 = """{"format":"io.github.rhjihan.slackwake.settings","schemaVersion":1,"appVersion":"1","exportedAt":"""

class SettingsBackupTest {
    private val now = Instant.parse("2026-11-23T10:15:00Z")

    private val custom =
        AppSettings(
            idleTimeoutSeconds = 900,
            soundLoop = false,
            flashColorA = "#FF0000",
            keywordAllowEnabled = true,
            keywordAllowText = "\"on call\", #incidents",
            vibrationPattern = "SOS",
            scheduleEnabled = true,
            scheduleDays = List(7) { DayWindow(true, 23 * 60, 7 * 60) },
            // Device-local:
            onboardingCompleted = true,
            slackNotificationsConfirmed = true,
            verboseDiagnostics = true,
            photosensitivityWarningDismissed = true,
        )

    private fun success(text: String) = SettingsBackup.decode(text) as DecodeResult.Success

    @Test fun `round trip keeps every user preference`() {
        val text = SettingsBackup.encode(custom, "1.2.3", now)
        val r = success(text)
        assertEquals(SettingsBackup.Source.BACKUP, r.meta.source)
        assertEquals("1.2.3", r.meta.appVersion)
        assertEquals(now, r.meta.exportedAt)
        val restored = SettingsBackup.applyRestore(current = AppSettings(), restored = r.settings)
        assertEquals(900, restored.idleTimeoutSeconds)
        assertFalse(restored.soundLoop)
        assertEquals("#FF0000", restored.flashColorA)
        assertEquals("\"on call\", #incidents", restored.keywordAllowText)
        assertEquals("SOS", restored.vibrationPattern)
        assertTrue(restored.scheduleEnabled)
        assertEquals(DayWindow(true, 1380, 420), restored.scheduleDays[3])
    }

    @Test fun `file is self-describing and uses the shared setting keys`() {
        val text = SettingsBackup.encode(custom, "1.2.3", now)
        assertTrue(text.contains("\"format\": \"io.github.rhjihan.slackwake.settings\""))
        assertTrue(text.contains("\"schemaVersion\": 1"))
        assertTrue(text.contains("\"exportedAt\": \"2026-11-23T10:15:00Z\""))
        assertTrue(text.contains("\"IdleTimeoutSeconds\": 900"))
    }

    @Test fun `device-local fields are not exported`() {
        val text = SettingsBackup.encode(custom, "1", now)
        val r = success(text).settings
        assertFalse(r.onboardingCompleted)
        assertFalse(r.slackNotificationsConfirmed)
        assertFalse(r.verboseDiagnostics)
        assertFalse(r.photosensitivityWarningDismissed)
    }

    @Test fun `restore keeps this device's local fields`() {
        val current =
            AppSettings(onboardingCompleted = true, slackNotificationsConfirmed = false, verboseDiagnostics = false, photosensitivityWarningDismissed = false)
        val incoming = custom // pretend a file carried them anyway
        val merged = SettingsBackup.applyRestore(current, incoming)
        assertTrue(merged.onboardingCompleted)
        assertFalse(merged.slackNotificationsConfirmed)
        assertFalse(merged.verboseDiagnostics)
        assertFalse(merged.photosensitivityWarningDismissed)
    }

    @Test fun `unknown keys are ignored and out-of-range values clamped`() {
        val text =
            """
            {"format":"io.github.rhjihan.slackwake.settings","schemaVersion":1,"appVersion":"9","exportedAt":"2026-01-01T00:00:00Z",
             "futureField":true,
             "settings":{"IdleTimeoutSeconds":1,"FlashIntervalMs":50,"FlashColorA":"nope","SomethingNew":42}}
            """.trimIndent()
        val s = success(text).settings
        assertEquals(10, s.idleTimeoutSeconds)
        assertEquals(200, s.flashIntervalMs)
        assertEquals("#000000", s.flashColorA)
    }

    @Test fun `a windows settings json is accepted`() {
        val windows =
            """
            {"Enabled":true,"IdleTimeoutSeconds":600,"StartWithWindows":true,"SoundFilePath":"C:\\Windows\\Media\\Ring08.wav",
             "KeywordFilterEnabled":true,"KeywordFilterText":"standup"}
            """.trimIndent()
        val r = success(windows)
        assertEquals(SettingsBackup.Source.PLAIN_SETTINGS, r.meta.source)
        assertEquals(600, r.settings.idleTimeoutSeconds)
        assertEquals("standup", r.settings.keywordFilterText)
        assertEquals("", r.settings.soundUri)
    }

    @Test fun `newer schema is refused`() {
        val text = """{"format":"io.github.rhjihan.slackwake.settings","schemaVersion":2,"appVersion":"9","exportedAt":"x","settings":{}}"""
        assertEquals(DecodeResult.Failure(Failure.NEWER_VERSION), SettingsBackup.decode(text))
    }

    @Test fun `garbage, foreign json and wrong types are refused`() {
        assertEquals(DecodeResult.Failure(Failure.NOT_JSON), SettingsBackup.decode("not json at all"))
        assertEquals(DecodeResult.Failure(Failure.NOT_JSON), SettingsBackup.decode("[1,2,3]"))
        assertEquals(DecodeResult.Failure(Failure.NOT_SLACKWAKE), SettingsBackup.decode("""{"name":"something else"}"""))
        assertEquals(DecodeResult.Failure(Failure.NOT_SLACKWAKE), SettingsBackup.decode("""{"format":"com.other.app","schemaVersion":1}"""))
        val wrongType = V1 + """"x","settings":{"IdleTimeoutSeconds":"soon"}}"""
        assertEquals(DecodeResult.Failure(Failure.INVALID), SettingsBackup.decode(wrongType))
        val noSettings = V1 + """"x","settings":[]}"""
        assertEquals(DecodeResult.Failure(Failure.INVALID), SettingsBackup.decode(noSettings))
    }

    @Test fun `oversized files are refused before parsing`() {
        assertEquals(DecodeResult.Failure(Failure.TOO_LARGE), SettingsBackup.decode(" ".repeat(SettingsBackup.MAX_BYTES + 1)))
    }

    @Test fun `unparseable export date is tolerated`() {
        val text = V1 + """"yesterday","settings":{"Enabled":false}}"""
        val r = success(text)
        assertEquals(null, r.meta.exportedAt)
        assertFalse(r.settings.enabled)
    }

    @Test fun `suggested file name carries the date`() {
        assertEquals("SlackWake-settings-2026-11-23.json", SettingsBackup.fileName(LocalDate.of(2026, 11, 23)))
    }
}
