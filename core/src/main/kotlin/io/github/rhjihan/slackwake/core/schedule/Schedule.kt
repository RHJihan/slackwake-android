// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.core.schedule

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * One day's monitoring window. Times are minutes after local midnight (0–1439).
 *
 * The window belongs to the day it STARTS on. If [endMinute] <= [startMinute] it crosses
 * midnight and ends on the next day: Monday 23:00–07:00 covers Monday 23:00 → Tuesday
 * 07:00. Start == end means the whole 24 hours from the start.
 */
@Serializable
data class DayWindow(
    @SerialName("Enabled") val enabled: Boolean,
    @SerialName("StartMinute") val startMinute: Int,
    @SerialName("EndMinute") val endMinute: Int,
) {
    val crossesMidnight: Boolean get() = endMinute <= startMinute

    /** Length in minutes (1..1440). */
    val durationMinutes: Int get() = if (crossesMidnight) MINUTES_PER_DAY - startMinute + endMinute else endMinute - startMinute

    fun normalized(): DayWindow = copy(startMinute = startMinute.coerceIn(0, MINUTES_PER_DAY - 1), endMinute = endMinute.coerceIn(0, MINUTES_PER_DAY - 1))

    companion object {
        const val MINUTES_PER_DAY = 24 * 60

        fun of(
            enabled: Boolean,
            start: LocalTime,
            end: LocalTime,
        ) = DayWindow(enabled, start.hour * 60 + start.minute, end.hour * 60 + end.minute)
    }
}

/** Wall-clock time in the phone's current zone (ZonedDateTime.now() on device; fixed in tests). */
fun interface WallClock {
    fun now(): ZonedDateTime
}

/** Time-zone helpers for the schedule. */
object ScheduleZones {
    /** The schedule's zone: [id] when it is a valid IANA zone ID, else the phone's zone. */
    fun resolve(
        id: String,
        device: ZoneId,
    ): ZoneId = if (id.isBlank()) device else runCatching { ZoneId.of(id) }.getOrDefault(device)

    /** Keeps only valid zone IDs; anything else (typo, old backup) becomes "" = phone zone. */
    fun sanitize(id: String): String = if (id.isNotBlank() && runCatching { ZoneId.of(id) }.isSuccess) id else ""

    /**
     * [sanitize], then the catalog's canonical ID, so one zone is always stored under one
     * name (an alias saved by an older version or another device is migrated). An ID valid
     * for java.time but unknown to the catalog is kept as is rather than dropped.
     */
    fun canonicalize(
        id: String,
        catalog: TimeZoneCatalog,
    ): String = sanitize(id).let { valid -> if (valid.isEmpty()) "" else catalog.canonicalId(valid) ?: valid }
}

/**
 * Weekly schedule: index 0 = Monday … 6 = Sunday (ISO order, independent of how the UI
 * orders the days for the user's locale).
 */
object WeeklySchedule {
    const val DAYS = 7

    /** Monday–Friday 09:00–18:00, weekend off: the common "working hours" default. */
    val DEFAULT: List<DayWindow> =
        List(DAYS) { i -> DayWindow(enabled = i < 5, startMinute = 9 * 60, endMinute = 18 * 60) }

    /** Always exactly 7 valid windows: pads/truncates and clamps hand-edited data. */
    fun normalized(days: List<DayWindow>): List<DayWindow> = List(DAYS) { i -> (days.getOrNull(i) ?: DEFAULT[i]).normalized() }

    fun indexOf(day: DayOfWeek): Int = day.value - 1

    /** Start and (exclusive) end of the window that starts on [date]. */
    fun span(
        days: List<DayWindow>,
        date: LocalDate,
    ): Pair<LocalDateTime, LocalDateTime>? {
        val w = days[indexOf(date.dayOfWeek)]
        if (!w.enabled) return null
        val start = date.atStartOfDay().plusMinutes(w.startMinute.toLong())
        return start to start.plusMinutes(w.durationMinutes.toLong())
    }

    /**
     * True if [now] falls inside today's window or inside yesterday's window that runs
     * past midnight. Overlapping windows simply merge.
     */
    fun isActive(
        days: List<DayWindow>,
        now: LocalDateTime,
    ): Boolean {
        val today = now.toLocalDate()
        return listOf(today.minusDays(1), today).any { d ->
            val s = span(days, d) ?: return@any false
            !now.isBefore(s.first) && now.isBefore(s.second)
        }
    }

    /**
     * The next moment the active/inactive state flips, or null if it never does (no day
     * enabled, or windows chained so it is always on). Looks up to 8 days ahead, enough
     * to cover any weekly pattern.
     */
    fun nextChange(
        days: List<DayWindow>,
        now: LocalDateTime,
    ): LocalDateTime? {
        val current = isActive(days, now)
        val today = now.toLocalDate()
        val candidates =
            (-1L..8L)
                .mapNotNull { span(days, today.plusDays(it)) }
                .flatMap { listOf(it.first, it.second) }
                .filter { it.isAfter(now) }
                .sorted()
        // A boundary only counts if the state really changes there (adjacent windows
        // that touch, e.g. 18:00 end and 18:00 start, are one continuous block).
        return candidates.firstOrNull { isActive(days, it) != current }
    }
}
