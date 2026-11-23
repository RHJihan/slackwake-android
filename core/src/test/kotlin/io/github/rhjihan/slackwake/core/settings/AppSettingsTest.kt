// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.core.settings

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {
    @Test fun `defaults match the Windows AppSettings source`() {
        val s = AppSettings()
        assertTrue(s.enabled)
        assertEquals(300, s.idleTimeoutSeconds)
        assertTrue(s.soundEnabled)
        assertEquals(5, s.soundDelaySeconds)
        assertTrue(s.soundLoop)
        assertEquals("", s.soundUri)
        assertTrue(s.flashEnabled)
        assertEquals(500, s.flashIntervalMs)
        assertEquals("#000000", s.flashColorA)
        assertEquals("#FFFFFF", s.flashColorB)
        assertTrue(s.alertAutoStopEnabled)
        assertEquals(600, s.alertMaxDurationSeconds)
        assertTrue(s.alertAutoStopIncludesSound)
        assertTrue(s.alertAutoStopIncludesVisual)
        assertFalse(s.keywordFilterEnabled)
        assertEquals("", s.keywordFilterText)
        assertFalse(s.keywordAllowEnabled)
        assertEquals("", s.keywordAllowText)
        assertFalse(s.verboseDiagnostics)
        assertEquals(s, s.normalized())
    }

    @Test fun `clamps every numeric field to its bounds`() {
        val low =
            AppSettings(
                idleTimeoutSeconds = 0,
                soundDelaySeconds = -5,
                flashIntervalMs = 100,
                alertMaxDurationSeconds = 1,
            ).normalized()
        assertEquals(10, low.idleTimeoutSeconds)
        assertEquals(0, low.soundDelaySeconds)
        assertEquals(200, low.flashIntervalMs) // Android floor (Windows: 100)
        assertEquals(5, low.alertMaxDurationSeconds)

        val high =
            AppSettings(
                idleTimeoutSeconds = Int.MAX_VALUE,
                soundDelaySeconds = 999,
                flashIntervalMs = 99_999,
                alertMaxDurationSeconds = 99_999,
            ).normalized()
        assertEquals(7200, high.idleTimeoutSeconds)
        assertEquals(120, high.soundDelaySeconds)
        assertEquals(2000, high.flashIntervalMs)
        assertEquals(3600, high.alertMaxDurationSeconds)
    }

    @Test fun `values on the bounds are kept`() {
        val s = AppSettings(idleTimeoutSeconds = 10, soundDelaySeconds = 120, flashIntervalMs = 200, alertMaxDurationSeconds = 3600)
        assertEquals(s, s.normalized())
    }

    @Test fun `flash never exceeds 3 flashes per second at the floor`() {
        val fullCycleMs = 2 * SettingsClamps.FLASH_INTERVAL_MS.first
        assertTrue(1000.0 / fullCycleMs <= 3.0)
    }

    @Test fun `colors are sanitized to RRGGBB`() {
        val s = AppSettings(flashColorA = "abc", flashColorB = "not a color").normalized()
        assertEquals("#AABBCC", s.flashColorA)
        assertEquals("#000000", s.flashColorB)
    }

    @Test fun `json keys are the Windows names and unknown or legacy keys are ignored`() {
        val json = Json { ignoreUnknownKeys = true }
        val windowsFile =
            """
            {"Enabled":false,"IdleTimeoutSeconds":60,"StartWithWindows":true,"StartMinimized":false,
             "SoundFilePath":"C:\\Windows\\Media\\Ring08.wav","FlashIntervalMs":100,"KeywordAllowText":"x"}
            """.trimIndent()
        val s = json.decodeFromString(AppSettings.serializer(), windowsFile).normalized()
        assertFalse(s.enabled)
        assertEquals(60, s.idleTimeoutSeconds)
        assertEquals("", s.soundUri) // SoundFilePath is not carried over
        assertEquals(200, s.flashIntervalMs)
        assertEquals("x", s.keywordAllowText)
        assertTrue(json.encodeToString(AppSettings.serializer(), s).contains("\"IdleTimeoutSeconds\":60"))
    }

    @Test fun `derived flags`() {
        assertFalse(AppSettings(keywordAllowEnabled = true, keywordAllowText = " ").allowListActive)
        assertTrue(AppSettings(keywordAllowEnabled = true, keywordAllowText = "x").allowListActive)
        assertTrue(AppSettings(keywordAllowEnabled = true, keywordFilterEnabled = true).showFilterInteractionNote)
        assertFalse(AppSettings(soundEnabled = false, flashEnabled = false, vibrationEnabled = false).canAutoStopAlerts)
        assertTrue(AppSettings(soundEnabled = false, flashEnabled = true).canAutoStopAlerts)
    }
}
