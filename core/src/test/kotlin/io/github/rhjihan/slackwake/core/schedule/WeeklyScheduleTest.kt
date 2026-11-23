// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.core.schedule

import io.github.rhjihan.slackwake.core.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class WeeklyScheduleTest {
    // 2026-11-23 is a Monday.
    private fun at(
        day: Int,
        h: Int,
        m: Int = 0,
    ) = LocalDateTime.of(2026, 11, 22 + day, h, m) // day 1 = Mon … 7 = Sun

    private fun win(
        start: Int,
        end: Int,
        on: Boolean = true,
    ) = DayWindow(on, start * 60, end * 60)

    private val off = DayWindow(false, 9 * 60, 17 * 60)

    /** Only [index] (0 = Mon) has a window. */
    private fun only(
        index: Int,
        w: DayWindow,
    ) = List(7) { if (it == index) w else off }

    @Test fun `same-day window is start-inclusive and end-exclusive`() {
        val days = only(0, win(9, 17))
        assertFalse(WeeklySchedule.isActive(days, at(1, 8, 59)))
        assertTrue(WeeklySchedule.isActive(days, at(1, 9)))
        assertTrue(WeeklySchedule.isActive(days, at(1, 16, 59)))
        assertFalse(WeeklySchedule.isActive(days, at(1, 17)))
        assertFalse("other days unaffected", WeeklySchedule.isActive(days, at(2, 10)))
    }

    @Test fun `11 PM to 7 AM runs into the next date`() {
        val days = only(0, win(23, 7)) // Monday night only
        assertTrue(days[0].crossesMidnight)
        assertFalse(WeeklySchedule.isActive(days, at(1, 22, 59)))
        assertTrue(WeeklySchedule.isActive(days, at(1, 23)))
        assertTrue("past midnight, on Tuesday's date", WeeklySchedule.isActive(days, at(2, 0, 30)))
        assertTrue(WeeklySchedule.isActive(days, at(2, 6, 59)))
        assertFalse(WeeklySchedule.isActive(days, at(2, 7)))
        assertFalse("Tuesday has no window of its own", WeeklySchedule.isActive(days, at(2, 23, 30)))
        assertFalse("Monday early morning belongs to Sunday, which is off", WeeklySchedule.isActive(days, at(1, 2)))
    }

    @Test fun `sunday night window wraps into monday morning`() {
        val days = only(6, win(22, 6))
        assertTrue(WeeklySchedule.isActive(days, at(7, 23)))
        assertTrue(WeeklySchedule.isActive(days, at(1, 5, 59))) // the following Monday
        assertFalse(WeeklySchedule.isActive(days, at(1, 6)))
    }

    @Test fun `start equal to end means a full 24 hours`() {
        val days = only(2, win(8, 8)) // Wednesday 08:00 → Thursday 08:00
        assertEquals(1440, days[2].durationMinutes)
        assertTrue(WeeklySchedule.isActive(days, at(3, 8)))
        assertTrue(WeeklySchedule.isActive(days, at(4, 7, 59)))
        assertFalse(WeeklySchedule.isActive(days, at(4, 8)))
    }

    @Test fun `overlapping windows merge`() {
        val days =
            List(7) { i ->
                if (i == 0) {
                    win(22, 8)
                } else if (i == 1) {
                    win(6, 12)
                } else {
                    off
                }
            }
        assertTrue(WeeklySchedule.isActive(days, at(2, 7))) // in both
        assertTrue(WeeklySchedule.isActive(days, at(2, 11)))
        assertEquals(at(2, 12), WeeklySchedule.nextChange(days, at(2, 7)))
    }

    @Test fun `next change finds the end of the current window across midnight`() {
        val days = only(0, win(23, 7))
        assertEquals(at(2, 7), WeeklySchedule.nextChange(days, at(1, 23, 30)))
        assertEquals(at(1, 23), WeeklySchedule.nextChange(days, at(1, 12)))
    }

    @Test fun `next change skips to the next enabled day a week later`() {
        val days = only(0, win(9, 17))
        assertEquals(LocalDateTime.of(2026, 11, 30, 9, 0), WeeklySchedule.nextChange(days, at(1, 18)))
    }

    @Test fun `touching windows are one block`() {
        val days = List(7) { win(0, 0) } // every day 24h → always on
        assertTrue(WeeklySchedule.isActive(days, at(3, 12)))
        assertNull(WeeklySchedule.nextChange(days, at(3, 12)))
    }

    @Test fun `no enabled day never changes and is never active`() {
        val days = List(7) { off }
        assertFalse(WeeklySchedule.isActive(days, at(1, 10)))
        assertNull(WeeklySchedule.nextChange(days, at(1, 10)))
    }

    @Test fun `default is weekdays nine to six and the schedule itself starts off`() {
        val s = AppSettings()
        assertFalse(s.scheduleEnabled)
        assertTrue(s.inSchedule(at(6, 3).atZone(ZoneOffset.UTC))) // schedule off → always allowed
        val on = s.copy(scheduleEnabled = true)
        assertTrue(on.inSchedule(at(1, 9).atZone(ZoneOffset.UTC)))
        assertFalse(on.inSchedule(at(1, 18).atZone(ZoneOffset.UTC)))
        assertFalse(on.inSchedule(at(6, 12).atZone(ZoneOffset.UTC))) // Saturday
    }

    @Test fun `hand-edited schedules are normalized to seven valid days`() {
        val n = AppSettings(scheduleDays = listOf(DayWindow(true, -5, 99_999))).normalized()
        assertEquals(7, n.scheduleDays.size)
        assertEquals(DayWindow(true, 0, 1439), n.scheduleDays[0])
        assertEquals(WeeklySchedule.DEFAULT[1], n.scheduleDays[1])
    }

    // ---- time zones ----

    private val newYork = ZoneId.of("America/New_York")
    private val dhaka = ZoneId.of("Asia/Dhaka") // UTC+6, no DST

    @Test fun `schedule in another zone is evaluated in that zone`() {
        // New York weekdays 09:00-18:00; the phone is in Dhaka.
        val s = AppSettings(scheduleEnabled = true, scheduleTimeZone = "America/New_York")
        // Mon 2026-11-23 09:30 in New York (EST, UTC-5) = 20:30 Monday in Dhaka.
        val phoneTime = LocalDateTime.of(2026, 11, 23, 20, 30).atZone(dhaka)
        assertTrue(s.inSchedule(phoneTime))
        // 10:00 Monday in Dhaka = 23:00 Sunday in New York: weekend, off.
        assertFalse(s.inSchedule(LocalDateTime.of(2026, 11, 23, 10, 0).atZone(dhaka)))
    }

    @Test fun `a night window crosses midnight in the schedule's zone, not the phone's`() {
        // Every night 23:00-07:00 New York time; phone in Dhaka.
        val s = AppSettings(scheduleEnabled = true, scheduleTimeZone = "America/New_York", scheduleDays = List(7) { win(23, 7) })
        // 02:00 Tue New York = 13:00 Tue Dhaka (daytime on the phone) → on.
        assertTrue(s.inSchedule(LocalDateTime.of(2026, 11, 24, 13, 0).atZone(dhaka)))
        // 08:00 Tue New York = 19:00 Tue Dhaka → off.
        assertFalse(s.inSchedule(LocalDateTime.of(2026, 11, 24, 19, 0).atZone(dhaka)))
    }

    @Test fun `blank or invalid zone follows the phone`() {
        assertEquals(dhaka, AppSettings(scheduleTimeZone = "").scheduleZone(dhaka))
        assertEquals("", AppSettings(scheduleTimeZone = "Mars/Olympus_Mons").normalized().scheduleTimeZone)
        assertEquals(dhaka, AppSettings(scheduleTimeZone = "Mars/Olympus_Mons").scheduleZone(dhaka))
        assertEquals("America/New_York", AppSettings(scheduleTimeZone = "America/New_York").normalized().scheduleTimeZone)
    }

    @Test fun `zone DST rules apply because the ID is stored, not an offset`() {
        val s = AppSettings(scheduleEnabled = true, scheduleTimeZone = "America/New_York")
        // 09:30 New York in July is EDT (UTC-4) = 13:30 UTC; in November EST (UTC-5) = 14:30 UTC.
        assertTrue(s.inSchedule(LocalDateTime.of(2026, 7, 6, 13, 30).atZone(ZoneOffset.UTC))) // Mon in July
        assertFalse(s.inSchedule(LocalDateTime.of(2026, 11, 23, 13, 30).atZone(ZoneOffset.UTC))) // 08:30 EST Mon
        assertTrue(s.inSchedule(LocalDateTime.of(2026, 11, 23, 14, 30).atZone(ZoneOffset.UTC))) // 09:30 EST Mon
        assertEquals(newYork, s.scheduleZone(ZoneOffset.UTC))
    }
}
