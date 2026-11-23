// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.core.status

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusCalculatorTest {
    @Test fun `disabled hides problems`() {
        val s = StatusCalculator.compute(enabled = false, armed = true, allowListActive = true, problems = Problem.entries)
        assertEquals(MonitorStatus(MonitorState.DISABLED, StatusDetail.DISABLED, null), s)
    }

    @Test fun `paused while present`() {
        val s = StatusCalculator.compute(enabled = true, armed = false, allowListActive = true, problems = emptyList())
        assertEquals(MonitorState.PAUSED, s.state)
        assertEquals(StatusDetail.PAUSED_PRESENT, s.detail)
        assertNull(s.problem)
    }

    @Test fun `armed text calls out an active allow-list`() {
        assertEquals(StatusDetail.ARMED_ALL, StatusCalculator.compute(true, true, false, emptyList()).detail)
        assertEquals(StatusDetail.ARMED_KEYWORDS_ONLY, StatusCalculator.compute(true, true, true, emptyList()).detail)
    }

    @Test fun `most severe problem wins regardless of input order`() {
        val s =
            StatusCalculator.compute(
                true,
                true,
                false,
                listOf(Problem.FULL_SCREEN_DENIED, Problem.LISTENER_ACCESS_MISSING),
            )
        assertEquals(Problem.LISTENER_ACCESS_MISSING, s.problem)
    }

    @Test fun `duration parts`() {
        assertEquals(DurationParts(6, 15), DurationParts.of(375))
        assertEquals(DurationParts(0, 45), DurationParts.of(45))
        assertFalse(DurationParts.showFriendly(60))
        assertTrue(DurationParts.showFriendly(61))
    }

    @Test fun `outside the schedule is paused with its own sentence`() {
        val s = StatusCalculator.compute(enabled = true, armed = false, allowListActive = false, problems = emptyList(), inSchedule = false)
        assertEquals(MonitorStatus(MonitorState.PAUSED, StatusDetail.PAUSED_OFF_SCHEDULE, null), s)
        val off = StatusCalculator.compute(enabled = false, armed = false, allowListActive = false, problems = emptyList(), inSchedule = false)
        assertEquals("disabled wins over schedule", MonitorState.DISABLED, off.state)
    }
}
