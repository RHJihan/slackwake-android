// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.ui.settings

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import io.github.rhjihan.slackwake.R
import io.github.rhjihan.slackwake.core.schedule.DayWindow
import io.github.rhjihan.slackwake.core.schedule.WeeklySchedule
import io.github.rhjihan.slackwake.core.settings.AppSettings
import io.github.rhjihan.slackwake.ui.components.SwitchRow
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

/** Days in the order the user's locale starts the week (e.g. Sunday first in the US). */
private fun localeWeek(locale: Locale): List<DayOfWeek> {
    val first = WeekFields.of(locale).firstDayOfWeek
    return List(7) { first.plus(it.toLong()) }
}

/** "9:00 AM" or "09:00", following the phone's 12/24-hour setting. */
internal fun formatMinute(
    context: Context,
    minuteOfDay: Int,
    locale: Locale,
): String {
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else DateFormat.getBestDateTimePattern(locale, "hmm a")
    return LocalTime.of(minuteOfDay / 60, minuteOfDay % 60).format(DateTimeFormatter.ofPattern(pattern, locale))
}

/**
 * "Monitor on a schedule": per-day windows in the user's week order, a live "on now /
 * off now" summary, and an editor with Material 3 time pickers. A window that ends at
 * or before its start runs into the next day and is labelled so.
 */
@Composable
fun ScheduleSection(
    s: AppSettings,
    viewModel: SettingsViewModel,
) {
    val context = LocalContext.current
    // Observable: a language change while the screen is open recomposes with the new locale.
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ROOT
    var editing by rememberSaveable { mutableStateOf<Int?>(null) } // ISO index being edited
    var pickingZone by rememberSaveable { mutableStateOf(false) }

    SwitchRow(
        stringResource(R.string.schedule_title),
        s.scheduleEnabled,
        viewModel::setScheduleEnabled,
        caption = stringResource(R.string.schedule_caption),
        modifier = Modifier.testTag("schedule_enabled"),
    )
    if (!s.scheduleEnabled) return

    val now = rememberMinuteClock()
    ScheduleSummary(s, now, context, locale)
    TimeZoneRow(s, now, context, locale, onChange = { pickingZone = true })

    localeWeek(locale).forEach { day ->
        val i = WeeklySchedule.indexOf(day)
        val w = s.scheduleDays[i]
        val dayName = day.getDisplayName(TextStyle.FULL, locale)
        val range =
            if (w.enabled) {
                rangeText(context, w, locale)
            } else {
                stringResource(R.string.schedule_day_off)
            }
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClickLabel = stringResource(R.string.schedule_edit_day, dayName)) { editing = i }
                    .padding(vertical = 8.dp)
                    .testTag("schedule_day_${day.name}"),
            ) {
                Text(dayName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    range,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (w.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val switchLabel = stringResource(R.string.schedule_day_switch, dayName)
            Switch(
                checked = w.enabled,
                onCheckedChange = { viewModel.setScheduleDayEnabled(i, it) },
                modifier = Modifier.semantics { contentDescription = switchLabel },
            )
        }
    }

    if (pickingZone) {
        TimeZonePickerDialog(
            selectedId = s.scheduleTimeZone,
            deviceZone = now.zone,
            locale = locale,
            onSelect = {
                viewModel.setScheduleTimeZone(it)
                pickingZone = false
            },
            onDismiss = { pickingZone = false },
        )
    }

    editing?.let { i ->
        ScheduleEditor(
            index = i,
            window = s.scheduleDays[i],
            zoneLabel = if (s.scheduleTimeZone.isBlank()) null else describeZone(s.scheduleTimeZone, locale, now.toInstant()).city,
            locale = locale,
            onDismiss = { editing = null },
            onSave = { start, end, alsoApplyTo ->
                viewModel.setScheduleWindow(i, start, end, alsoApplyTo)
                editing = null
            },
        )
    }
}

@Composable
private fun rangeText(
    context: Context,
    w: DayWindow,
    locale: Locale,
): String {
    val text = "${formatMinute(context, w.startMinute, locale)} – ${formatMinute(context, w.endMinute, locale)}"
    return when {
        w.startMinute == w.endMinute -> stringResource(R.string.schedule_all_day_from, formatMinute(context, w.startMinute, locale))
        w.crossesMidnight -> stringResource(R.string.schedule_next_day, text)
        else -> text
    }
}

/** Wall-clock time that updates at every minute boundary while on screen. */
@Composable
private fun rememberMinuteClock(): ZonedDateTime {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            delay(
                java.time.Duration
                    .between(now, now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1))
                    .toMillis()
                    .coerceAtLeast(1),
            )
        }
    }
    return now
}

/**
 * "On now · until 6:00 PM" / "Off now · starts Mon 9:00 AM", computed in the schedule's
 * zone. With a zone other than the phone's, the phone-time equivalent is added.
 */
@Composable
private fun ScheduleSummary(
    s: AppSettings,
    now: ZonedDateTime,
    context: Context,
    locale: Locale,
) {
    val zone = s.scheduleZone(now.zone)
    val local = s.scheduleLocalTime(now)
    val active = WeeklySchedule.isActive(s.scheduleDays, local)
    val next = WeeklySchedule.nextChange(s.scheduleDays, local)
    val whenText =
        next?.let {
            val time = formatMinute(context, it.hour * 60 + it.minute, locale)
            val inZone = if (it.toLocalDate() == local.toLocalDate()) time else "${it.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)} $time"
            if (zone == now.zone) {
                inZone
            } else {
                val here = it.atZone(zone).withZoneSameInstant(now.zone)
                val hereTime = formatMinute(context, here.hour * 60 + here.minute, locale)
                val hereText = if (here.toLocalDate() == now.toLocalDate()) hereTime else "${here.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)} $hereTime"
                stringResource(R.string.schedule_with_phone_time, inZone, hereText)
            }
        }
    val text =
        when {
            active && whenText != null -> stringResource(R.string.schedule_on_until, whenText)
            active -> stringResource(R.string.schedule_on_always)
            whenText != null -> stringResource(R.string.schedule_off_until, whenText)
            else -> stringResource(R.string.schedule_no_days)
        }
    Row(Modifier.padding(vertical = 4.dp).testTag("schedule_summary"), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
    }
}

/**
 * Which zone the times are in. A chosen zone also shows its current time (live), so the
 * user can see at a glance what "9:00 AM" means there right now.
 */
@Composable
private fun TimeZoneRow(
    s: AppSettings,
    now: ZonedDateTime,
    context: Context,
    locale: Locale,
    onChange: () -> Unit,
) {
    val usingPhone = s.scheduleTimeZone.isBlank()
    val zone = s.scheduleZone(now.zone)
    val d = describeZone(zone.id, locale, now.toInstant())
    val label = "${d.city} · ${d.region} (${d.offsetText})"
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("schedule_zone"), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                if (usingPhone) stringResource(R.string.tz_row_phone, label) else label,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!usingPhone) {
                val there = now.withZoneSameInstant(zone)
                Text(
                    stringResource(R.string.tz_row_now_there, formatMinute(context, there.hour * 60 + there.minute, locale)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("schedule_zone_now"),
                )
            }
        }
        TextButton(onClick = onChange, modifier = Modifier.testTag("schedule_zone_change")) { Text(stringResource(R.string.tz_change)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleEditor(
    index: Int,
    window: DayWindow,
    zoneLabel: String?,
    locale: Locale,
    onDismiss: () -> Unit,
    onSave: (start: Int, end: Int, alsoApplyTo: Set<Int>) -> Unit,
) {
    val context = LocalContext.current
    var start by rememberSaveable { mutableIntStateOf(window.startMinute) }
    var end by rememberSaveable { mutableIntStateOf(window.endMinute) }
    var copyTo by rememberSaveable { mutableStateOf(setOf<Int>()) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) } // "start" / "end"
    val day = DayOfWeek.of(index + 1)
    val preview = DayWindow(true, start, end)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(day.getDisplayName(TextStyle.FULL, locale)) },
        confirmButton = { TextButton(onClick = { onSave(start, end, copyTo) }) { Text(stringResource(R.string.schedule_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeTile(
                        label = stringResource(R.string.schedule_start_label),
                        time = formatMinute(context, start, locale),
                        onClick = { picking = "start" },
                        modifier = Modifier.weight(1f).testTag("schedule_start"),
                    )
                    TimeTile(
                        label = stringResource(R.string.schedule_end_label),
                        time = formatMinute(context, end, locale),
                        onClick = { picking = "end" },
                        modifier = Modifier.weight(1f).testTag("schedule_end"),
                    )
                }
                Text(
                    when {
                        start == end -> {
                            stringResource(R.string.schedule_hint_all_day)
                        }

                        preview.crossesMidnight -> {
                            stringResource(
                                R.string.schedule_hint_next_day,
                                DayOfWeek.of(index + 1).plus(1).getDisplayName(TextStyle.FULL, locale),
                            )
                        }

                        else -> {
                            stringResource(R.string.schedule_hint_same_day)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("schedule_hint"),
                )
                if (zoneLabel != null) {
                    Text(
                        stringResource(R.string.tz_editor_hint, zoneLabel),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(stringResource(R.string.schedule_copy_to), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    localeWeek(locale).filter { it != day }.forEach { other ->
                        val j = WeeklySchedule.indexOf(other)
                        FilterChip(
                            selected = j in copyTo,
                            onClick = { copyTo = if (j in copyTo) copyTo - j else copyTo + j },
                            label = { Text(other.getDisplayName(TextStyle.SHORT, locale)) },
                            modifier = Modifier.semantics { contentDescription = other.getDisplayName(TextStyle.FULL, locale) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { copyTo = (0..4).toSet() - index }) { Text(stringResource(R.string.schedule_copy_weekdays)) }
                    TextButton(onClick = { copyTo = (0..6).toSet() - index }) { Text(stringResource(R.string.schedule_copy_all)) }
                }
            }
        },
    )

    picking?.let { which ->
        val initial = if (which == "start") start else end
        val state = rememberTimePickerState(initial / 60, initial % 60, is24Hour = DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(stringResource(if (which == "start") R.string.schedule_pick_start else R.string.schedule_pick_end)) },
            confirmButton = {
                TextButton(onClick = {
                    val m = state.hour * 60 + state.minute
                    if (which == "start") start = m else end = m
                    picking = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state = state) },
        )
    }
}

/** Tappable "Starts / 9:00 AM" tile (label over a large, single-line time). */
@Composable
private fun TimeTile(
    label: String,
    time: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.heightIn(min = 64.dp).semantics(mergeDescendants = true) {},
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(time, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        }
    }
}
