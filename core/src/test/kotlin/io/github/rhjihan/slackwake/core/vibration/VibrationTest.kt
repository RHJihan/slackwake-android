// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.core.vibration

import io.github.rhjihan.slackwake.core.settings.AppSettings
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VibrationTest {
    @Test fun `alert pattern is the classic 500 on 500 off`() {
        val w = VibrationMath.waveform(VibrationPattern.ALERT, 100)
        assertArrayEquals(longArrayOf(0, 500), w.timings)
        assertArrayEquals(intArrayOf(0, 255), w.amplitudes)
        assertEquals(1000, VibrationPattern.ALERT.cycleMs)
    }

    @Test fun `waveform starts with the motor on and keeps every pause but the last`() {
        val p = VibrationPattern.HEARTBEAT
        val w = VibrationMath.waveform(p, 100)
        assertArrayEquals(longArrayOf(0, 100, 120, 140), w.timings)
        assertArrayEquals(intArrayOf(0, 255, 0, 153), w.amplitudes) // second beat at 60%
        assertEquals(p.cycleMs, w.durationMs + p.pulses.last().offMs)
    }

    @Test fun `every pattern has a sensible cycle and valid amplitudes`() {
        VibrationPattern.entries.forEach { p ->
            assertTrue("${p.key} cycle ${p.cycleMs}", p.cycleMs in 900..6000)
            val w = VibrationMath.waveform(p, 50)
            assertEquals(p.pulses.size * 2, w.timings.size)
            w.amplitudes.forEachIndexed { i, a -> if (i % 2 == 0) assertEquals(0, a) else assertTrue(a in 1..255) }
        }
    }

    @Test fun `sos spells three short three long three short`() {
        val ons = VibrationPattern.SOS.pulses.map { it.onMs }
        assertEquals(listOf(150L, 150, 150, 450, 450, 450, 150, 150, 150), ons)
    }

    @Test fun `intensity scales amplitude and never reaches zero`() {
        assertEquals(255, VibrationMath.amplitude(100, 1f, 1f))
        assertEquals(128, VibrationMath.amplitude(50, 1f, 1f))
        assertEquals(26, VibrationMath.amplitude(10, 1f, 1f))
        assertEquals(1, VibrationMath.amplitude(10, 0.01f, 0.01f))
        assertEquals(255, VibrationMath.amplitude(500, 1f, 1f)) // out-of-range input clamped
    }

    @Test fun `fixed mode ignores the ramp`() {
        assertEquals(1f, VibrationMath.rampScale(VibrationMode.FIXED, 0, 30_000))
    }

    @Test fun `gradual mode ramps linearly from 15 percent to full then holds`() {
        assertEquals(0.15f, VibrationMath.rampScale(VibrationMode.GRADUAL, 0, 30_000), 1e-6f)
        assertEquals(0.575f, VibrationMath.rampScale(VibrationMode.GRADUAL, 15_000, 30_000), 1e-6f)
        assertEquals(1f, VibrationMath.rampScale(VibrationMode.GRADUAL, 30_000, 30_000), 1e-6f)
        assertEquals(1f, VibrationMath.rampScale(VibrationMode.GRADUAL, 90_000, 30_000), 1e-6f)
    }

    @Test fun `unknown pattern and mode keys fall back to defaults`() {
        assertEquals(VibrationPattern.ALERT, VibrationPattern.fromKey("Disco"))
        assertEquals(VibrationPattern.SOS, VibrationPattern.fromKey("sos"))
        assertEquals(VibrationMode.FIXED, VibrationMode.fromKey(null))
        assertEquals(VibrationMode.GRADUAL, VibrationMode.fromKey("gradual"))
    }

    @Test fun `settings defaults and clamps`() {
        val d = AppSettings()
        assertTrue(d.vibrationEnabled)
        assertEquals("Alert", d.vibrationPattern)
        assertEquals(100, d.vibrationIntensityPercent)
        assertEquals("Fixed", d.vibrationMode)
        assertEquals(30, d.vibrationRampSeconds)
        assertTrue(d.alertAutoStopIncludesVibration)

        val n =
            AppSettings(
                vibrationIntensityPercent = 0,
                vibrationRampSeconds = 999,
                vibrationPattern = "nope",
                vibrationMode = "x",
            ).normalized()
        assertEquals(10, n.vibrationIntensityPercent)
        assertEquals(120, n.vibrationRampSeconds)
        assertEquals("Alert", n.vibrationPattern)
        assertEquals("Fixed", n.vibrationMode)
    }

    @Test fun `fixed preview repeats the pattern for about two and a half seconds`() {
        val w = VibrationMath.preview(VibrationPattern.ALERT, 100, VibrationMode.FIXED)
        assertArrayEquals(longArrayOf(0, 500, 500, 500, 500, 500), w.timings)
        assertTrue(VibrationMath.preview(VibrationPattern.SOS, 100, VibrationMode.FIXED).timings.size >= 2 * 2 * 9)
    }

    @Test fun `gradual preview is a four step crescendo`() {
        val w = VibrationMath.preview(VibrationPattern.ALERT, 100, VibrationMode.GRADUAL)
        assertArrayEquals(intArrayOf(0, 38, 0, 111, 0, 183, 0, 255), w.amplitudes)
    }

    @Test fun `vibration alone keeps auto-stop available`() {
        assertTrue(AppSettings(soundEnabled = false, flashEnabled = false, vibrationEnabled = true).canAutoStopAlerts)
    }
}
