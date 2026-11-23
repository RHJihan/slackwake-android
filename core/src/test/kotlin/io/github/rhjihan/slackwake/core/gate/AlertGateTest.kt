// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.core.gate

import io.github.rhjihan.slackwake.core.model.SlackEvent
import io.github.rhjihan.slackwake.core.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

private class FakeClock(
    var now: Long = 1_000_000,
) : Clock {
    override fun nowMs() = now

    fun advanceSeconds(s: Int) {
        now += s * 1000L
    }
}

/** Any fixed wall-clock time: the schedule is off in these tests unless a test turns it on. */
private val NOW: ZonedDateTime = LocalDateTime.of(2026, 11, 23, 12, 0).atZone(ZoneOffset.UTC) // Monday noon

class AlertGateTest {
    private val clock = FakeClock()
    private val tracker = AwayTracker(clock)
    private val gate = AlertGate(tracker)
    private val settings = AppSettings(idleTimeoutSeconds = 300)
    private val ping = SlackEvent("Alice", "#ops", "hello")

    private val lockedOff = DeviceState(keyguardLocked = true, interactive = false)
    private val lockedOn = DeviceState(keyguardLocked = true, interactive = true) // notification woke the screen
    private val unlocked = DeviceState(keyguardLocked = false, interactive = true)
    private val noLockScreenOff = DeviceState(keyguardLocked = false, interactive = false)

    private fun evaluate(
        device: DeviceState,
        s: AppSettings = settings,
        showing: Boolean = false,
    ) = gate.evaluate(ping, s, device, showing, NOW)

    private fun goAway() {
        tracker.initialize(unlocked)
        gate.onScreenOff()
    }

    @Test fun `present user is never alerted`() {
        tracker.initialize(unlocked)
        clock.advanceSeconds(10_000)
        assertTrue(evaluate(unlocked) is GateDecision.Suppress)
    }

    @Test fun `fires only once locked for at least the timeout`() {
        goAway()
        clock.advanceSeconds(299)
        assertTrue(evaluate(lockedOff) is GateDecision.Suppress)
        clock.advanceSeconds(1)
        assertEquals(GateDecision.Fire, evaluate(lockedOff))
    }

    @Test fun `screen woken by a notification does not count as presence`() {
        goAway()
        clock.advanceSeconds(200)
        gate.onScreenOn(keyguardLocked = true)
        clock.advanceSeconds(100)
        assertEquals(GateDecision.Fire, evaluate(lockedOn))
    }

    @Test fun `unlock resets the away period`() {
        goAway()
        clock.advanceSeconds(400)
        gate.onUserPresent()
        gate.onScreenOff()
        clock.advanceSeconds(299)
        assertTrue(evaluate(lockedOff) is GateDecision.Suppress)
        clock.advanceSeconds(1)
        assertEquals(GateDecision.Fire, evaluate(lockedOff))
    }

    @Test fun `repeated screen-off does not restart the away period`() {
        goAway()
        clock.advanceSeconds(200)
        gate.onScreenOn(keyguardLocked = true)
        gate.onScreenOff()
        clock.advanceSeconds(100)
        assertEquals(GateDecision.Fire, evaluate(lockedOff))
    }

    @Test fun `device without a lock screen falls back to screen-off duration`() {
        goAway()
        clock.advanceSeconds(300)
        assertEquals(GateDecision.Fire, evaluate(noLockScreenOff))
    }

    @Test fun `device without a lock screen counts screen-on as presence`() {
        goAway()
        clock.advanceSeconds(200)
        gate.onScreenOn(keyguardLocked = false)
        clock.advanceSeconds(200)
        assertTrue(evaluate(noLockScreenOff) is GateDecision.Suppress)
    }

    @Test fun `missed unlock broadcast is caught by the live device state`() {
        goAway()
        clock.advanceSeconds(1000)
        assertTrue(evaluate(unlocked) is GateDecision.Suppress)
    }

    @Test fun `service connecting while locked starts counting from connect time`() {
        tracker.initialize(lockedOff)
        clock.advanceSeconds(299)
        assertTrue(evaluate(lockedOff) is GateDecision.Suppress)
        clock.advanceSeconds(1)
        assertEquals(GateDecision.Fire, evaluate(lockedOff))
    }

    @Test fun `service connecting while unlocked is present`() {
        tracker.initialize(unlocked)
        assertNull(tracker.awaySinceMs)
    }

    @Test fun `disabled suppresses before anything else`() {
        goAway()
        clock.advanceSeconds(1000)
        assertEquals(GateDecision.Suppress("disabled"), evaluate(lockedOff, settings.copy(enabled = false)))
    }

    @Test fun `one alert at a time`() {
        goAway()
        clock.advanceSeconds(1000)
        assertEquals(GateDecision.Suppress("an alert is already showing"), evaluate(lockedOff, showing = true))
    }

    @Test fun `re-arms only after unlock and a new away period`() {
        goAway()
        clock.advanceSeconds(300)
        assertEquals(GateDecision.Fire, evaluate(lockedOff))
        // Dismissed on the lock screen; the screen times out again; still away.
        clock.advanceSeconds(1000)
        gate.onScreenOff()
        assertEquals(GateDecision.Suppress("already alerted; waiting for unlock"), evaluate(lockedOff))
        assertFalse(gate.isArmed(settings, lockedOff, NOW))
        // User comes back, then leaves again.
        gate.onUserPresent()
        gate.onScreenOff()
        clock.advanceSeconds(300)
        assertTrue(gate.isArmed(settings, lockedOff, NOW))
        assertEquals(GateDecision.Fire, evaluate(lockedOff))
    }

    @Test fun `keyword suppression does not consume the arm`() {
        goAway()
        clock.advanceSeconds(300)
        val muted = settings.copy(keywordFilterEnabled = true, keywordFilterText = "alice")
        assertTrue(evaluate(lockedOff, muted) is GateDecision.Suppress)
        assertFalse(gate.rearmPending)
        assertEquals(GateDecision.Fire, evaluate(lockedOff))
    }

    @Test fun `isArmed reflects timeout and enabled`() {
        goAway()
        assertFalse(gate.isArmed(settings, lockedOff, NOW))
        clock.advanceSeconds(300)
        assertTrue(gate.isArmed(settings, lockedOff, NOW))
        assertFalse(gate.isArmed(settings.copy(enabled = false), lockedOff, NOW))
    }

    @Test fun `outside the schedule nothing fires even when away`() {
        goAway()
        clock.advanceSeconds(300)
        val nightOnly =
            settings.copy(
                scheduleEnabled = true,
                scheduleDays =
                    List(7) {
                        io.github.rhjihan.slackwake.core.schedule
                            .DayWindow(true, 23 * 60, 7 * 60)
                    },
            )
        assertEquals(GateDecision.Suppress("outside the monitoring schedule"), gate.evaluate(ping, nightOnly, lockedOff, false, NOW))
        assertFalse(gate.isArmed(nightOnly, lockedOff, NOW))
        val lateNight = LocalDateTime.of(2026, 11, 24, 2, 30).atZone(ZoneOffset.UTC) // Tuesday 02:30, inside Monday's window
        assertTrue(gate.isArmed(nightOnly, lockedOff, lateNight))
        assertEquals(GateDecision.Fire, gate.evaluate(ping, nightOnly, lockedOff, false, lateNight))
    }
}
