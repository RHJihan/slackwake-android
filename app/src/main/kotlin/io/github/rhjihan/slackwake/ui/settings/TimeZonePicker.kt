// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.ui.settings

import android.icu.text.TimeZoneNames
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import io.github.rhjihan.slackwake.R
import io.github.rhjihan.slackwake.ui.components.imeOverlapPadding
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/** One selectable zone: "New York · Eastern Time (GMT−5)". */
data class ZoneOption(
    val id: String,
    val city: String,
    val region: String,
    val offsetText: String,
    val offsetSeconds: Int,
)

/** IANA "Area/City" zones; skips legacy aliases (US/…, Etc/GMT+5, SystemV/…). */
private val REGION_ZONE = Regex("^(Africa|America|Antarctica|Asia|Atlantic|Australia|Europe|Indian|Pacific)/[^/].*")

/** "GMT", "GMT+6", "GMT−5", "GMT+5:30" (true minus sign, like system settings). */
fun formatGmtOffset(totalSeconds: Int): String {
    if (totalSeconds == 0) return "GMT"
    val sign = if (totalSeconds > 0) "+" else "−"
    val h = abs(totalSeconds) / 3600
    val m = abs(totalSeconds) % 3600 / 60
    return if (m == 0) "GMT$sign$h" else "GMT$sign$h:%02d".format(m)
}

/** Localized description of one zone at [now] (offsets change with DST). */
fun describeZone(
    id: String,
    locale: Locale,
    now: Instant,
    names: TimeZoneNames = TimeZoneNames.getInstance(locale),
): ZoneOption {
    val zone = ZoneId.of(id)
    val offset = zone.rules.getOffset(now).totalSeconds
    val city = runCatching { names.getExemplarLocationName(id) }.getOrNull() ?: id.substringAfterLast('/').replace('_', ' ')
    return ZoneOption(id, city, zone.getDisplayName(TextStyle.FULL, locale), formatGmtOffset(offset), offset)
}

/** All selectable zones, sorted by current offset then city, like Google Calendar's picker. */
fun zoneOptions(
    locale: Locale,
    now: Instant,
): List<ZoneOption> {
    val names = TimeZoneNames.getInstance(locale)
    return ZoneId
        .getAvailableZoneIds()
        .filter { REGION_ZONE.matches(it) }
        .map { describeZone(it, locale, now, names) }
        .sortedWith(compareBy({ it.offsetSeconds }, { it.city }))
}

/**
 * Full-screen, searchable time-zone picker (Material full-screen dialog). The first row
 * is "Use phone time zone" (stored as ""). Search matches city, zone name, ID or offset.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeZonePickerDialog(
    selectedId: String,
    deviceZone: ZoneId,
    locale: Locale,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val now = remember { Instant.now() }
    val all = remember(locale) { zoneOptions(locale, now) }
    val device = remember(deviceZone, locale) { describeZone(deviceZone.id, locale, now) }
    var query by rememberSaveable { mutableStateOf("") }
    val shown =
        remember(query, all) {
            val q = query.trim()
            if (q.isEmpty()) {
                all
            } else {
                all.filter {
                    it.city.contains(q, true) || it.region.contains(q, true) || it.id.contains(q, true) || it.offsetText.contains(q, true)
                }
            }
        }

    // Full-screen dialogs draw edge-to-edge and handle insets themselves (the platform's
    // window resize glitches while the keyboard animates), as Compose recommends.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // This window draws behind the status bar, so match its icons to our surface.
        val view = LocalView.current
        val lightSurface = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = lightSurface
                    isAppearanceLightNavigationBars = lightSurface
                }
            }
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.tz_title)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close)) }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.tz_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("tz_search"),
                )
                LazyColumn(Modifier.fillMaxSize().imeOverlapPadding()) {
                    if (query.isBlank()) {
                        item(key = "phone") {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.tz_use_phone)) },
                                supportingContent = { Text("${device.city} · ${device.region} (${device.offsetText})") },
                                leadingContent = { Icon(Icons.Filled.PhoneAndroid, contentDescription = null) },
                                trailingContent = { RadioButton(selected = selectedId.isBlank(), onClick = null) },
                                modifier = Modifier.heightIn(min = 56.dp).clickable { onSelect("") }.testTag("tz_phone"),
                            )
                            HorizontalDivider()
                        }
                    }
                    items(shown, key = { it.id }) { z ->
                        ListItem(
                            headlineContent = { Text(z.city) },
                            supportingContent = { Text("${z.region} (${z.offsetText})") },
                            trailingContent = { RadioButton(selected = z.id == selectedId, onClick = null) },
                            modifier = Modifier.heightIn(min = 56.dp).clickable { onSelect(z.id) },
                        )
                    }
                    if (shown.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                stringResource(R.string.tz_no_match),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
