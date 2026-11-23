// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.ui.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rhjihan.slackwake.BuildConfig
import io.github.rhjihan.slackwake.R
import io.github.rhjihan.slackwake.alert.AlertActivity
import io.github.rhjihan.slackwake.core.color.ColorUtil
import io.github.rhjihan.slackwake.core.settings.AppSettings
import io.github.rhjihan.slackwake.core.settings.SettingsClamps
import io.github.rhjihan.slackwake.core.status.DurationParts
import io.github.rhjihan.slackwake.ui.components.BatteryTip
import io.github.rhjihan.slackwake.ui.components.CheckboxRow
import io.github.rhjihan.slackwake.ui.components.ColorPickerDialog
import io.github.rhjihan.slackwake.ui.components.PhotosensitivityDialog
import io.github.rhjihan.slackwake.ui.components.PrerequisiteList
import io.github.rhjihan.slackwake.ui.components.SliderField
import io.github.rhjihan.slackwake.ui.components.SoundPickerDialog
import io.github.rhjihan.slackwake.ui.components.StatusPill
import io.github.rhjihan.slackwake.ui.components.SwitchRow
import io.github.rhjihan.slackwake.ui.components.imeOverlapPadding
import io.github.rhjihan.slackwake.ui.components.setupIncomplete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SettingsRoute(viewModel: SettingsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val soundOptions by viewModel.soundOptions.collectAsStateWithLifecycle()
    val defaultLabel = stringResource(R.string.sound_default)
    LaunchedEffect(defaultLabel) { viewModel.setDefaultSoundLabel(defaultLabel) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stopPreview() }
    SettingsScreen(state = state, soundOptions = soundOptions, viewModel = viewModel)
}

/**
 * Top-level destinations. Material 3 uses a navigation bar (rail on wide screens) for an
 * app's 3–5 top-level sections; tabs are for views inside one screen.
 */
enum class SettingsTab(
    val label: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    HOME(R.string.tab_home, Icons.Filled.Home, Icons.Outlined.Home),
    ALERTS(R.string.tab_alerts, Icons.Filled.NotificationsActive, Icons.Outlined.NotificationsActive),
    TIMING(R.string.tab_timing, Icons.Filled.Schedule, Icons.Outlined.Schedule),
    FILTERS(R.string.tab_filters, Icons.Filled.FilterAlt, Icons.Outlined.FilterAlt),
    MORE(R.string.tab_more, Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    soundOptions: List<io.github.rhjihan.slackwake.sound.SoundOption>,
    viewModel: SettingsViewModel,
) {
    val s = state.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val focus = LocalFocusManager.current

    var tab by rememberSaveable { mutableStateOf(SettingsTab.HOME) }
    // Back from another destination returns to Home before leaving the app.
    BackHandler(enabled = tab != SettingsTab.HOME) { tab = SettingsTab.HOME }
    // Each destination keeps its own scroll position.
    val listStates = SettingsTab.entries.associateWith { rememberLazyListState() }

    var flashWarning by rememberSaveable { mutableStateOf(false) }
    var verboseWarning by rememberSaveable { mutableStateOf(false) }
    var soundPicker by rememberSaveable { mutableStateOf(false) }
    var colorPicker by rememberSaveable { mutableStateOf<String?>(null) } // "A" / "B"

    val fileLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.pickSoundFile(uri)
        }

    // Photosensitivity warning whenever flash goes from off to on (any entry point).
    val requestFlash: (Boolean) -> Unit = { on ->
        if (on && !s.flashEnabled && !s.photosensitivityWarningDismissed) flashWarning = true else viewModel.setFlashEnabled(on)
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            SettingsTab.entries.forEach { t ->
                item(
                    selected = t == tab,
                    onClick = { tab = t },
                    icon = { Icon(if (t == tab) t.selectedIcon else t.icon, contentDescription = null) },
                    label = { Text(stringResource(t.label)) },
                    modifier = Modifier.testTag("tab_${t.name.lowercase()}"),
                )
            }
        },
    ) {
        val scroll = TopAppBarDefaults.pinnedScrollBehavior()
        Scaffold(
            // A tap on empty space ends text entry; buttons, switches and scroll drags consume theirs.
            modifier =
                Modifier
                    .nestedScroll(scroll.nestedScrollConnection)
                    .pointerInput(Unit) { detectTapGestures { focus.clearFocus() } },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(if (tab == SettingsTab.HOME) R.string.app_name else tab.label)) },
                    scrollBehavior = scroll,
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            LazyColumn(
                state = listStates.getValue(tab),
                // Edge-to-edge: the keyboard overlays the window, so shrink the list to clear it.
                modifier = Modifier.fillMaxSize().imeOverlapPadding().testTag("settings_list"),
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 24.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (tab) {
                    SettingsTab.HOME -> {
                        homePage(state, viewModel, requestFlash, context)
                    }

                    SettingsTab.ALERTS -> {
                        alertsPage(
                            state = state,
                            viewModel = viewModel,
                            requestFlash = requestFlash,
                            onOpenSoundPicker = {
                                viewModel.loadSoundOptions()
                                soundPicker = true
                            },
                            onPickColor = { colorPicker = it },
                        )
                    }

                    SettingsTab.TIMING -> {
                        timingPage(s, viewModel)
                    }

                    SettingsTab.FILTERS -> {
                        filtersPage(s, viewModel)
                    }

                    SettingsTab.MORE -> {
                        morePage(
                            state = state,
                            viewModel = viewModel,
                            snackbar = snackbar,
                            onVerbose = { on -> if (on) verboseWarning = true else viewModel.setVerbose(false) },
                            onShareLog = { scope.launch { shareLog(context, viewModel.readLog()) } },
                            onClearLog = {
                                viewModel.clearLog()
                                Toast.makeText(context, R.string.log_cleared, Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
            }
        }
    }

    if (flashWarning) {
        PhotosensitivityDialog(
            onConfirm = { dontShowAgain ->
                flashWarning = false
                viewModel.enableFlashFromWarning(dontShowAgain)
            },
            onDismiss = { flashWarning = false },
        )
    }
    if (verboseWarning) {
        AlertDialog(
            onDismissRequest = { verboseWarning = false },
            title = { Text(stringResource(R.string.verbose_warning_title)) },
            text = { Text(stringResource(R.string.verbose_warning_body)) },
            confirmButton = {
                TextButton(onClick = {
                    verboseWarning = false
                    viewModel.setVerbose(true)
                }) { Text(stringResource(R.string.verbose_enable)) }
            },
            dismissButton = { TextButton(onClick = { verboseWarning = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (soundPicker) {
        SoundPickerDialog(
            options = soundOptions,
            selected = s.soundUri,
            customLabel = state.soundLabel,
            playing = state.previewing,
            onPick = viewModel::pickSound,
            onChooseFile = { fileLauncher.launch(arrayOf("audio/*")) },
            onDismiss = {
                soundPicker = false
                viewModel.stopPreview()
            },
        )
    }
    colorPicker?.let { which ->
        ColorPickerDialog(
            initialHex = if (which == "A") s.flashColorA else s.flashColorB,
            onDismiss = { colorPicker = null },
            onSelect = { hex ->
                if (which == "A") viewModel.setFlashColorA(hex) else viewModel.setFlashColorB(hex)
                colorPicker = null
            },
        )
    }
}

/** A titled group: section heading above a card holding the related settings. */
private fun LazyListScope.group(
    title: Int?,
    key: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    item(key = key) {
        Column {
            if (title != null) Section(stringResource(title))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), content = content)
            }
        }
    }
}

// ---- Home: status, what still needs doing, quick toggles, test ----

private fun LazyListScope.homePage(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    requestFlash: (Boolean) -> Unit,
    context: Context,
) {
    val s = state.settings
    item(key = "status") { StatusPill(state.status) }

    // Only the steps that are still open, each disappearing once done; nothing when all set.
    if (setupIncomplete(state.prereqs, s.slackNotificationsConfirmed)) {
        item(key = "setup") {
            Column {
                Section(stringResource(R.string.setup_title))
                PrerequisiteList(state.prereqs, s.slackNotificationsConfirmed, viewModel::setSlackConfirmed, onlyMissing = true)
            }
        }
    }

    // Tray right-click menu parity.
    group(R.string.quick_toggles, "quick") {
        SwitchRow(
            stringResource(R.string.toggle_enabled),
            s.enabled,
            viewModel::setEnabled,
            caption = stringResource(R.string.toggle_enabled_caption),
            emphasized = true,
            modifier = Modifier.testTag("toggle_enabled"),
        )
        HorizontalDivider()
        SwitchRow(stringResource(R.string.toggle_sound), s.soundEnabled, viewModel::setSoundEnabled, Modifier.testTag("toggle_sound"))
        SwitchRow(
            stringResource(R.string.vibration_title),
            s.vibrationEnabled && state.hasVibrator,
            viewModel::setVibrationEnabled,
            Modifier.testTag("toggle_vibration"),
            enabled = state.hasVibrator,
        )
        SwitchRow(stringResource(R.string.toggle_visual), s.flashEnabled, requestFlash, Modifier.testTag("toggle_visual"))
        // Hidden (not greyed out) when sound, flash and vibration are all off.
        if (s.canAutoStopAlerts) {
            SwitchRow(stringResource(R.string.toggle_autostop), s.alertAutoStopEnabled, viewModel::setAutoStop, Modifier.testTag("toggle_autostop"))
        }
    }

    item(key = "test") {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = { if (viewModel.testAlert()) context.startActivity(AlertActivity.intent(context)) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("test_alert"),
            ) { Text(stringResource(R.string.test_overlay)) }
            Text(
                stringResource(R.string.test_overlay_caption),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

// ---- Alerts: sound, vibration, flash, auto-stop ----

private fun LazyListScope.alertsPage(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    requestFlash: (Boolean) -> Unit,
    onOpenSoundPicker: () -> Unit,
    onPickColor: (String) -> Unit,
) {
    val s = state.settings
    group(R.string.section_sound, "sound") {
        SwitchRow(stringResource(R.string.toggle_sound), s.soundEnabled, viewModel::setSoundEnabled, caption = stringResource(R.string.sound_alert_caption))
        // Options that only matter while their switch is on are hidden, not greyed out.
        if (s.soundEnabled) {
            SoundRow(
                label = state.soundLabel,
                playing = state.previewing != null,
                enabled = true,
                onOpen = onOpenSoundPicker,
                onTogglePreview = viewModel::togglePreview,
            )
            SwitchRow(stringResource(R.string.loop_title), s.soundLoop, viewModel::setSoundLoop, caption = stringResource(R.string.loop_caption))
        }
    }
    group(R.string.section_vibration, "vibration") {
        VibrationSection(s, state.hasVibrator, state.hasAmplitudeControl, viewModel)
    }
    group(R.string.section_flash, "flash") {
        SwitchRow(stringResource(R.string.flash_title), s.flashEnabled, requestFlash, caption = stringResource(R.string.flash_caption))
        if (s.flashEnabled) {
            SliderField(
                label = stringResource(R.string.flash_speed),
                value = s.flashIntervalMs,
                onValueChange = viewModel::setFlashInterval,
                clamp = SettingsClamps.FLASH_INTERVAL_MS,
                step = 50,
            )
            Text(stringResource(R.string.flash_colors), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.flash_colors_caption),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Swatch(s.flashColorA, stringResource(R.string.flash_color_a, s.flashColorA), true) { onPickColor("A") }
                Swatch(s.flashColorB, stringResource(R.string.flash_color_b, s.flashColorB), true) { onPickColor("B") }
            }
        }
    }
    // Nothing to stop when sound, flash and vibration are all off.
    if (s.canAutoStopAlerts) {
        group(R.string.section_autostop, "autostop") { AutoStop(s, viewModel) }
    }
}

// ---- Timing: away detection and schedule ----

private fun LazyListScope.timingPage(
    s: AppSettings,
    viewModel: SettingsViewModel,
) {
    group(R.string.section_away, "away") {
        SliderField(
            label = stringResource(R.string.idle_timeout),
            caption = stringResource(R.string.idle_timeout_caption),
            value = s.idleTimeoutSeconds,
            onValueChange = viewModel::setIdleTimeout,
            clamp = SettingsClamps.IDLE_TIMEOUT_SECONDS,
            sliderRange = SettingsClamps.IDLE_TIMEOUT_SLIDER,
            step = 10,
        )
        SliderField(
            label = stringResource(R.string.alert_delay),
            caption = stringResource(R.string.alert_delay_caption),
            value = s.soundDelaySeconds,
            onValueChange = viewModel::setAlertDelay,
            clamp = SettingsClamps.SOUND_DELAY_SECONDS,
        )
    }
    group(R.string.section_schedule, "schedule") { ScheduleSection(s, viewModel) }
}

// ---- Filters: mute and allow lists ----

private fun LazyListScope.filtersPage(
    s: AppSettings,
    viewModel: SettingsViewModel,
) {
    group(R.string.mute_title, "mute") {
        SwitchRow(stringResource(R.string.mute_title), s.keywordFilterEnabled, viewModel::setMuteEnabled, caption = stringResource(R.string.mute_caption))
        if (s.keywordFilterEnabled) {
            KeywordField(
                label = stringResource(R.string.mute_field),
                help = stringResource(R.string.mute_help),
                placeholder = stringResource(R.string.mute_placeholder),
                value = s.keywordFilterText,
                onCommit = viewModel::setMuteText,
                tag = "mute_field",
            )
        }
    }
    group(R.string.allow_title, "allow") {
        SwitchRow(stringResource(R.string.allow_title), s.keywordAllowEnabled, viewModel::setAllowEnabled, caption = stringResource(R.string.allow_caption))
        if (s.keywordAllowEnabled) {
            KeywordField(
                label = stringResource(R.string.allow_field),
                help = stringResource(R.string.allow_help),
                placeholder = stringResource(R.string.allow_placeholder),
                value = s.keywordAllowText,
                onCommit = viewModel::setAllowText,
                tag = "allow_field",
            )
        }
    }
    if (s.showFilterInteractionNote) {
        item(key = "filter_note") {
            Card(Modifier.fillMaxWidth().testTag("filter_note")) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.both_filters_title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.both_filters_body), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// ---- More: backup, battery, diagnostics, about ----

private fun LazyListScope.morePage(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    snackbar: SnackbarHostState,
    onVerbose: (Boolean) -> Unit,
    onShareLog: () -> Unit,
    onClearLog: () -> Unit,
) {
    group(R.string.section_backup, "backup") { BackupSection(viewModel, snackbar) }
    item(key = "battery") {
        Column {
            Section(stringResource(R.string.section_battery))
            BatteryTip(state.prereqs)
        }
    }
    group(R.string.section_diagnostics, "diagnostics") {
        SwitchRow(stringResource(R.string.verbose_title), state.settings.verboseDiagnostics, onVerbose, caption = stringResource(R.string.verbose_caption))
        Text(stringResource(R.string.log_caption), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onShareLog) { Text(stringResource(R.string.log_share)) }
            OutlinedButton(onClick = onClearLog) { Text(stringResource(R.string.log_clear)) }
        }
    }
    // List items bring their own 16dp insets, so the card has no extra padding here.
    item(key = "about") {
        Column {
            Section(stringResource(R.string.section_about))
            Card(Modifier.fillMaxWidth()) { AboutSection() }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp).semantics { heading() },
    )
}

@Composable
private fun AutoStop(
    s: AppSettings,
    viewModel: SettingsViewModel,
) {
    SwitchRow(
        stringResource(R.string.autostop_title),
        s.alertAutoStopEnabled,
        viewModel::setAutoStop,
        caption = stringResource(R.string.autostop_caption),
        modifier = Modifier.testTag("autostop_enabled"),
    )
    if (!s.alertAutoStopEnabled) return
    Text(stringResource(R.string.autostop_which), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    Text(
        stringResource(R.string.autostop_which_caption),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // Only alerts that are switched on can be stopped.
    if (s.soundEnabled) {
        CheckboxRow(stringResource(R.string.autostop_sound), s.alertAutoStopIncludesSound, viewModel::setAutoStopSound)
    }
    if (s.flashEnabled) {
        CheckboxRow(stringResource(R.string.autostop_visual), s.alertAutoStopIncludesVisual, viewModel::setAutoStopVisual)
    }
    if (s.vibrationEnabled) {
        CheckboxRow(stringResource(R.string.autostop_vibration), s.alertAutoStopIncludesVibration, viewModel::setAutoStopVibration)
    }
    SliderField(
        label = stringResource(R.string.autostop_max),
        value = s.alertMaxDurationSeconds,
        onValueChange = viewModel::setMaxDuration,
        clamp = SettingsClamps.ALERT_MAX_DURATION_SECONDS,
        step = 5,
        // Follows the slider/field live, not just the saved value.
        supporting = { live ->
            if (DurationParts.showFriendly(live)) {
                val parts = DurationParts.of(live)
                val text =
                    listOfNotNull(
                        parts.minutes.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.minutes, it, it) },
                        parts.seconds.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.seconds, it, it) },
                    ).joinToString(" ")
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("max_duration_friendly"),
                )
            }
        },
    )
}

@Composable
private fun SoundRow(
    label: String,
    playing: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    onTogglePreview: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clickable(enabled = enabled, onClick = onOpen)
                .padding(vertical = 8.dp),
        ) {
            Text(stringResource(R.string.sound_field), style = MaterialTheme.typography.titleSmall)
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onTogglePreview, enabled = enabled) {
            Icon(
                if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = stringResource(if (playing) R.string.sound_preview_stop else R.string.sound_preview_play),
            )
        }
    }
}

@Composable
private fun Swatch(
    hex: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val bg = ColorUtil.parse(hex)
    val fg = ColorUtil.contrastingTextColor(bg)
    Box(
        Modifier
            .size(width = 120.dp, height = 48.dp)
            .background(Color(bg.toArgb()), RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(hex, color = Color(fg.toArgb()), style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Multi-line keyword list. Edited locally and committed on focus loss or after a short
 * pause in typing, so persisting every keystroke never fights the cursor.
 */
@Composable
private fun KeywordField(
    label: String,
    help: String,
    placeholder: String,
    value: String,
    onCommit: (String) -> Unit,
    tag: String,
) {
    var text by remember { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value) { if (!focused) text = value }
    LaunchedEffect(text) {
        if (text != value) {
            delay(400)
            onCommit(text)
        }
    }
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(help, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            minLines = 3,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .testTag(tag)
                    .onFocusChanged {
                        if (focused && !it.isFocused && text != value) onCommit(text)
                        focused = it.isFocused
                    },
        )
    }
}

private suspend fun shareLog(
    context: Context,
    text: String,
) {
    val file =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            File(dir, "slackwake-diagnostics.txt").apply { writeText(text) }
        }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.diagnostics", file)
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.log_share_subject))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.log_share)))
}
