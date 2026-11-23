// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.core.alert

import app.cash.turbine.test
import io.github.rhjihan.slackwake.core.settings.AppSettings
import io.github.rhjihan.slackwake.core.vibration.Waveform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/** Records start/end times of each play on the virtual clock. Each clip lasts [clipMs]. */
private class FakeSound(
    private val scope: TestScope,
    private val clipMs: Long,
) : SoundSink {
    val starts = mutableListOf<Long>()
    val ends = mutableListOf<Long>()
    var cancelledAt: Long? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun playOnce() {
        starts += scope.currentTime
        try {
            delay(clipMs)
            ends += scope.currentTime
        } catch (e: CancellationException) {
            cancelledAt = scope.currentTime
            throw e
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AlertSessionTest {
    private fun plan(s: AppSettings) = AlertPlan.from(s)

    @Test fun `plan snapshot maps caps per channel`() {
        val base = AppSettings(alertMaxDurationSeconds = 30)
        assertEquals(30.seconds, plan(base).soundCap)
        assertEquals(30.seconds, plan(base).flashCap)
        assertNull(plan(base.copy(alertAutoStopIncludesSound = false)).soundCap)
        assertNull(plan(base.copy(alertAutoStopIncludesVisual = false)).flashCap)
        val off = plan(base.copy(alertAutoStopEnabled = false))
        assertNull(off.soundCap)
        assertNull(off.flashCap)
        assertEquals(5.seconds, plan(base).delay)
        assertEquals(200, plan(AppSettings(flashIntervalMs = 50)).flashIntervalMs) // clamped
    }

    @Test fun `delay applies to both sound and flash`() =
        runTest {
            val sound = FakeSound(this, clipMs = 1000)
            val session = AlertSession(plan(AppSettings(soundDelaySeconds = 5, soundLoop = false)), sound, backgroundScope)
            session.flashing.test {
                assertFalse(awaitItem())
                session.start()
                advanceTimeBy(4_999)
                runCurrent()
                expectNoEvents()
                assertTrue(sound.starts.isEmpty())
                advanceTimeBy(1)
                runCurrent()
                assertTrue(awaitItem())
                assertEquals(listOf(5_000L), sound.starts)
                session.stop()
                assertFalse(awaitItem())
            }
        }

    @Test fun `zero delay starts immediately`() =
        runTest {
            val sound = FakeSound(this, clipMs = 1000)
            AlertSession(plan(AppSettings(soundDelaySeconds = 0)), sound, backgroundScope).start()
            runCurrent()
            assertEquals(listOf(0L), sound.starts)
        }

    @Test fun `looping replays with a 500 ms gap after each play ends`() =
        runTest {
            val sound = FakeSound(this, clipMs = 2000)
            val s = AppSettings(soundDelaySeconds = 0, soundLoop = true, alertAutoStopEnabled = false)
            AlertSession(plan(s), sound, backgroundScope).start()
            advanceTimeBy(9_001)
            assertEquals(listOf(0L, 2_500L, 5_000L, 7_500L), sound.starts)
            assertEquals(listOf(2_000L, 4_500L, 7_000L), sound.ends)
        }

    @Test fun `non-looping sound plays exactly once`() =
        runTest {
            val sound = FakeSound(this, clipMs = 1000)
            val s = AppSettings(soundDelaySeconds = 0, soundLoop = false, alertAutoStopEnabled = false)
            val session = AlertSession(plan(s), sound, backgroundScope)
            session.soundPlaying.test {
                assertFalse(awaitItem())
                session.start()
                assertTrue(awaitItem())
                advanceTimeBy(60_000)
                assertFalse(awaitItem())
            }
            assertEquals(1, sound.starts.size)
        }

    @Test fun `auto-stop cuts the looping sound at the cap measured from after the delay`() =
        runTest {
            val sound = FakeSound(this, clipMs = 2000)
            val s = AppSettings(soundDelaySeconds = 5, soundLoop = true, alertMaxDurationSeconds = 6)
            AlertSession(plan(s), sound, backgroundScope).start()
            advanceTimeBy(60_000)
            // starts at 5.0, 7.5, 10.0 (cap at 11.0 cuts the third play mid-clip)
            assertEquals(listOf(5_000L, 7_500L, 10_000L), sound.starts)
            assertEquals(11_000L, sound.cancelledAt)
        }

    @Test fun `auto-stop cuts a single long play at the cap`() =
        runTest {
            val sound = FakeSound(this, clipMs = 30_000)
            val s = AppSettings(soundDelaySeconds = 0, soundLoop = false, alertMaxDurationSeconds = 5)
            AlertSession(plan(s), sound, backgroundScope).start()
            advanceTimeBy(60_000)
            assertEquals(5_000L, sound.cancelledAt)
        }

    @Test fun `flash stops at the cap while sound keeps going when only visual is capped`() =
        runTest {
            val sound = FakeSound(this, clipMs = 1000)
            val s =
                AppSettings(
                    soundDelaySeconds = 0,
                    alertMaxDurationSeconds = 10,
                    alertAutoStopIncludesSound = false,
                    alertAutoStopIncludesVisual = true,
                )
            val session = AlertSession(plan(s), sound, backgroundScope)
            session.flashing.test {
                assertFalse(awaitItem())
                session.start()
                assertTrue(awaitItem())
                advanceTimeBy(9_999)
                expectNoEvents()
                advanceTimeBy(1)
                assertFalse(awaitItem())
            }
            advanceTimeBy(100_000)
            assertTrue(session.soundPlaying.value)
            assertNull(sound.cancelledAt)
        }

    @Test fun `flash keeps going when only sound is capped`() =
        runTest {
            val sound = FakeSound(this, clipMs = 1000)
            val s =
                AppSettings(
                    soundDelaySeconds = 0,
                    alertMaxDurationSeconds = 10,
                    alertAutoStopIncludesSound = true,
                    alertAutoStopIncludesVisual = false,
                )
            val session = AlertSession(plan(s), sound, backgroundScope)
            session.start()
            advanceTimeBy(100_000)
            assertTrue(session.flashing.value)
            assertFalse(session.soundPlaying.value)
        }

    @Test fun `disabled channels never start`() =
        runTest {
            val sound = FakeSound(this, clipMs = 1000)
            val s = AppSettings(soundDelaySeconds = 0, soundEnabled = false, flashEnabled = false)
            val session = AlertSession(plan(s), sound, backgroundScope)
            session.start()
            advanceTimeBy(10_000)
            assertTrue(sound.starts.isEmpty())
            assertFalse(session.flashing.value)
        }

    @Test fun `stop during the delay prevents anything from starting`() =
        runTest {
            val sound = FakeSound(this, clipMs = 1000)
            val session = AlertSession(plan(AppSettings(soundDelaySeconds = 5)), sound, backgroundScope)
            session.start()
            advanceTimeBy(2_000)
            session.stop()
            advanceTimeBy(60_000)
            assertTrue(sound.starts.isEmpty())
            assertFalse(session.flashing.value)
        }

    @Test fun `stop cuts the sound mid-clip`() =
        runTest {
            val sound = FakeSound(this, clipMs = 10_000)
            val session = AlertSession(plan(AppSettings(soundDelaySeconds = 0)), sound, backgroundScope)
            session.start()
            advanceTimeBy(3_000)
            session.stop()
            runCurrent()
            assertEquals(3_000L, sound.cancelledAt)
            assertFalse(session.soundPlaying.value)
        }

    // ---- vibration ----

    private class FakeVibration(
        private val scope: TestScope,
    ) : VibrationSink {
        val starts = mutableListOf<Long>()
        val peaks = mutableListOf<Int>()
        var cancelledAt: Long? = null

        override suspend fun play(waveform: Waveform) {
            starts += scope.currentTime
            peaks += waveform.amplitudes.max()
            try {
                delay(waveform.durationMs)
            } catch (e: CancellationException) {
                cancelledAt = scope.currentTime
                throw e
            }
        }
    }

    private fun vibrationSettings(
        mode: String = "Fixed",
        autoStop: Boolean = false,
    ) = AppSettings(
        soundEnabled = false,
        flashEnabled = false,
        soundDelaySeconds = 5,
        vibrationEnabled = true,
        vibrationPattern = "Alert",
        vibrationIntensityPercent = 100,
        vibrationMode = mode,
        vibrationRampSeconds = 10,
        alertAutoStopEnabled = autoStop,
        alertMaxDurationSeconds = 6,
    )

    @Test fun `vibration waits out the alert delay then repeats each cycle`() =
        runTest {
            val v = FakeVibration(this)
            AlertSession(plan(vibrationSettings()), FakeSound(this, 1000), backgroundScope, v).start()
            advanceTimeBy(4_999)
            runCurrent()
            assertTrue(v.starts.isEmpty())
            advanceTimeBy(4_002)
            // Alert pattern: 1 s cycles starting at 5 s.
            assertEquals(listOf(5_000L, 6_000L, 7_000L, 8_000L, 9_000L), v.starts)
            assertTrue(v.peaks.all { it == 255 })
        }

    @Test fun `gradual vibration crescendos to full strength over the ramp`() =
        runTest {
            val v = FakeVibration(this)
            AlertSession(plan(vibrationSettings(mode = "Gradual")), FakeSound(this, 1000), backgroundScope, v).start()
            advanceTimeBy(5_000 + 14_001)
            assertEquals(38, v.peaks.first()) // 15% of 255
            assertTrue(
                "strictly rising during the ramp",
                v.peaks
                    .take(11)
                    .zipWithNext()
                    .all { (a, b) -> b > a },
            )
            assertEquals(255, v.peaks[10]) // 10 s ramp reached
            assertEquals(255, v.peaks.last())
        }

    @Test fun `vibration auto-stops at the cap measured from its start`() =
        runTest {
            val v = FakeVibration(this)
            val session = AlertSession(plan(vibrationSettings(autoStop = true)), FakeSound(this, 1000), backgroundScope, v)
            session.start()
            advanceTimeBy(60_000)
            assertEquals(6, v.starts.size) // cycles at 5..10 s; cap at 11 s
            assertFalse(session.vibrating.value)
        }

    @Test fun `vibration excluded from auto-stop keeps going`() =
        runTest {
            val v = FakeVibration(this)
            val s = vibrationSettings(autoStop = true).copy(alertAutoStopIncludesVibration = false)
            val session = AlertSession(plan(s), FakeSound(this, 1000), backgroundScope, v)
            session.start()
            advanceTimeBy(60_000)
            assertTrue(session.vibrating.value)
            assertTrue(v.starts.size > 50)
        }

    @Test fun `dismiss stops vibration mid-pulse`() =
        runTest {
            val v = FakeVibration(this)
            val session = AlertSession(plan(vibrationSettings()), FakeSound(this, 1000), backgroundScope, v)
            session.start()
            advanceTimeBy(5_250)
            session.stop()
            runCurrent()
            assertEquals(5_250L, v.cancelledAt)
            assertFalse(session.vibrating.value)
        }

    @Test fun `vibration disabled never touches the motor`() =
        runTest {
            val v = FakeVibration(this)
            AlertSession(plan(vibrationSettings().copy(vibrationEnabled = false)), FakeSound(this, 1000), backgroundScope, v).start()
            advanceTimeBy(60_000)
            assertTrue(v.starts.isEmpty())
        }

    // ---- screen hold ----

    @Test fun `screen is released at delay plus cap when the flash is stopped`() {
        assertEquals(65.seconds, plan(AppSettings(soundDelaySeconds = 5, alertMaxDurationSeconds = 60)).keepScreenOnFor)
    }

    @Test fun `sound or vibration still running does not keep the screen on`() {
        val base = AppSettings(soundDelaySeconds = 5, alertMaxDurationSeconds = 60)
        assertEquals(65.seconds, plan(base.copy(alertAutoStopIncludesSound = false)).keepScreenOnFor)
        assertEquals(65.seconds, plan(base.copy(alertAutoStopIncludesVibration = false)).keepScreenOnFor)
        assertEquals(65.seconds, plan(base.copy(alertAutoStopIncludesSound = false, alertAutoStopIncludesVibration = false)).keepScreenOnFor)
    }

    @Test fun `a flash left running, or auto-stop off, holds the screen until dismissed`() {
        val base = AppSettings(soundDelaySeconds = 5, alertMaxDurationSeconds = 60)
        assertNull(plan(base.copy(alertAutoStopIncludesVisual = false)).keepScreenOnFor)
        assertNull(plan(base.copy(alertAutoStopEnabled = false)).keepScreenOnFor)
    }

    @Test fun `a disabled channel does not need to be covered`() {
        val s = AppSettings(soundDelaySeconds = 0, alertMaxDurationSeconds = 30, flashEnabled = false, alertAutoStopIncludesVisual = false)
        assertEquals(30.seconds, plan(s).keepScreenOnFor)
    }

    @Test fun `screen-only alert releases the screen at the cap too`() {
        val s = AppSettings(soundEnabled = false, flashEnabled = false, vibrationEnabled = false, soundDelaySeconds = 0, alertMaxDurationSeconds = 20)
        assertEquals(20.seconds, plan(s).keepScreenOnFor)
    }
}
