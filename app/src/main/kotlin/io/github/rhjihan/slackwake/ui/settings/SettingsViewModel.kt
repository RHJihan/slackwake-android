// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rhjihan.slackwake.alert.AlertController
import io.github.rhjihan.slackwake.core.backup.SettingsBackup
import io.github.rhjihan.slackwake.core.model.SlackEvent
import io.github.rhjihan.slackwake.core.schedule.DayWindow
import io.github.rhjihan.slackwake.core.schedule.TimeZoneCatalog
import io.github.rhjihan.slackwake.core.settings.AppSettings
import io.github.rhjihan.slackwake.core.status.MonitorState
import io.github.rhjihan.slackwake.core.status.MonitorStatus
import io.github.rhjihan.slackwake.core.status.StatusDetail
import io.github.rhjihan.slackwake.core.vibration.VibrationMode
import io.github.rhjihan.slackwake.core.vibration.VibrationPattern
import io.github.rhjihan.slackwake.data.backup.BackupManager
import io.github.rhjihan.slackwake.data.backup.ReadResult
import io.github.rhjihan.slackwake.data.backup.RestorePreview
import io.github.rhjihan.slackwake.data.diagnostics.DiagnosticLog
import io.github.rhjihan.slackwake.data.settings.SettingsRepository
import io.github.rhjihan.slackwake.prereq.PrereqState
import io.github.rhjihan.slackwake.sound.PreviewPlayer
import io.github.rhjihan.slackwake.sound.SoundLibrary
import io.github.rhjihan.slackwake.sound.SoundOption
import io.github.rhjihan.slackwake.status.StatusRepository
import io.github.rhjihan.slackwake.vibration.AlertVibrationSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Outcome of a backup action, shown once in a snackbar. */
sealed interface BackupMessage {
    data object Saved : BackupMessage

    data object SaveFailed : BackupMessage

    /** No app on the phone can show the system file picker. */
    data object NoFilePicker : BackupMessage

    /** [reason] null = the file could not be opened or read. */
    data class RestoreFailed(
        val reason: SettingsBackup.Failure?,
    ) : BackupMessage

    /** Carries the settings from before the restore, for Undo. */
    data class Restored(
        val previous: AppSettings,
    ) : BackupMessage
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val status: MonitorStatus = MonitorStatus(MonitorState.PAUSED, StatusDetail.PAUSED_PRESENT, null),
    val prereqs: PrereqState = PrereqState.ALL_GRANTED,
    val soundLabel: String = "",
    val previewing: String? = null,
    val loaded: Boolean = false,
    /** Device capabilities: no motor hides vibration; on/off-only motors ignore intensity. */
    val hasVibrator: Boolean = true,
    val hasAmplitudeControl: Boolean = true,
)

/**
 * Port of MainViewModel's bindable surface. Unidirectional: the UI renders [state] and
 * calls intents; every intent writes through [SettingsRepository], which clamps and
 * persists immediately, and the new value flows back into [state].
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val repository: SettingsRepository,
        private val statusRepository: StatusRepository,
        private val alerts: AlertController,
        private val sounds: SoundLibrary,
        private val preview: PreviewPlayer,
        private val log: DiagnosticLog,
        private val vibrator: AlertVibrationSource,
        private val backup: BackupManager,
        private val zones: TimeZoneCatalog,
    ) : ViewModel() {
        private val soundLabel = MutableStateFlow("")

        /** Zone IDs for the schedule's time-zone picker. */
        val selectableZoneIds: List<String> get() = zones.selectableIds()

        val state: StateFlow<SettingsUiState> =
            combine(
                repository.settings,
                statusRepository.status,
                statusRepository.prerequisites,
                soundLabel,
                preview.playing,
            ) { s, status, prereqs, label, playing ->
                SettingsUiState(
                    s,
                    status,
                    prereqs,
                    label,
                    playing,
                    loaded = true,
                    hasVibrator = vibrator.hasVibrator,
                    hasAmplitudeControl = vibrator.hasAmplitudeControl,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

        /** System sounds for the picker, loaded off the main thread on demand. */
        private val _soundOptions = MutableStateFlow<List<SoundOption>>(emptyList())
        val soundOptions: StateFlow<List<SoundOption>> = _soundOptions

        private var defaultSoundLabel = ""

        init {
            viewModelScope.launch {
                repository.settings.collect { refreshSoundLabel(it.soundUri) }
            }
        }

        fun onResume() = statusRepository.refreshPrerequisites()

        fun setDefaultSoundLabel(label: String) {
            defaultSoundLabel = label
            viewModelScope.launch { refreshSoundLabel(repository.current().soundUri) }
        }

        private suspend fun refreshSoundLabel(uri: String) {
            soundLabel.value = withContext(Dispatchers.IO) { sounds.label(uri, defaultSoundLabel) }
        }

        private fun update(transform: (AppSettings) -> AppSettings) {
            viewModelScope.launch { repository.update(transform) }
        }

        // ---- toggles ----
        fun setEnabled(v: Boolean) = update { it.copy(enabled = v) }

        fun setSoundEnabled(v: Boolean) = update { it.copy(soundEnabled = v) }

        fun setSoundLoop(v: Boolean) = update { it.copy(soundLoop = v) }

        fun setFlashEnabled(v: Boolean) = update { it.copy(flashEnabled = v) }

        /** Confirmed the photosensitivity warning; optionally never show it again. */
        fun enableFlashFromWarning(dontShowAgain: Boolean) =
            update { it.copy(flashEnabled = true, photosensitivityWarningDismissed = it.photosensitivityWarningDismissed || dontShowAgain) }

        fun setAutoStop(v: Boolean) = update { it.copy(alertAutoStopEnabled = v) }

        fun setAutoStopSound(v: Boolean) = update { it.copy(alertAutoStopIncludesSound = v) }

        fun setAutoStopVisual(v: Boolean) = update { it.copy(alertAutoStopIncludesVisual = v) }

        fun setAutoStopVibration(v: Boolean) = update { it.copy(alertAutoStopIncludesVibration = v) }

        // ---- vibration ----
        fun setVibrationEnabled(v: Boolean) = update { it.copy(vibrationEnabled = v) }

        fun setVibrationIntensity(v: Int) = update { it.copy(vibrationIntensityPercent = v) }

        fun setVibrationRamp(v: Int) = update { it.copy(vibrationRampSeconds = v) }

        fun setVibrationMode(mode: VibrationMode) {
            update { it.copy(vibrationMode = mode.key) }
            previewVibration(mode = mode)
        }

        /** Selecting a pattern also plays it, like picking a ringtone. */
        fun setVibrationPattern(pattern: VibrationPattern) {
            update { it.copy(vibrationPattern = pattern.key) }
            previewVibration(pattern = pattern)
        }

        fun previewVibration(
            pattern: VibrationPattern? = null,
            mode: VibrationMode? = null,
        ) {
            val s = state.value.settings
            vibrator.cancel()
            vibrator.preview(
                pattern ?: VibrationPattern.fromKey(s.vibrationPattern),
                s.vibrationIntensityPercent,
                mode ?: VibrationMode.fromKey(s.vibrationMode),
            )
        }

        /** Intensity slider released: feel the new strength immediately. */
        fun commitVibrationIntensity(v: Int) {
            setVibrationIntensity(v)
            val s = state.value.settings
            vibrator.cancel()
            vibrator.preview(VibrationPattern.fromKey(s.vibrationPattern), v, VibrationMode.FIXED)
        }

        fun setMuteEnabled(v: Boolean) = update { it.copy(keywordFilterEnabled = v) }

        fun setAllowEnabled(v: Boolean) = update { it.copy(keywordAllowEnabled = v) }

        fun setVerbose(v: Boolean) = update { it.copy(verboseDiagnostics = v) }

        fun setSlackConfirmed(v: Boolean) = update { it.copy(slackNotificationsConfirmed = v) }

        // ---- schedule ----
        fun setScheduleEnabled(v: Boolean) = update { it.copy(scheduleEnabled = v) }

        /** IANA zone ID, or "" to follow the phone's zone. */
        fun setScheduleTimeZone(id: String) = update { it.copy(scheduleTimeZone = id) }

        fun setScheduleDayEnabled(
            index: Int,
            on: Boolean,
        ) = update { s -> s.copy(scheduleDays = s.scheduleDays.mapIndexed { i, w -> if (i == index) w.copy(enabled = on) else w }) }

        /** Save a day's window; [alsoApplyTo] (ISO indices) get the same times and are switched on. */
        fun setScheduleWindow(
            index: Int,
            startMinute: Int,
            endMinute: Int,
            alsoApplyTo: Set<Int>,
        ) = update { s ->
            s.copy(
                scheduleDays =
                    s.scheduleDays.mapIndexed { i, w ->
                        when (i) {
                            index -> w.copy(enabled = true, startMinute = startMinute, endMinute = endMinute)
                            in alsoApplyTo -> DayWindow(true, startMinute, endMinute)
                            else -> w
                        }
                    },
            )
        }

        // ---- numbers (clamped by the repository) ----
        fun setIdleTimeout(v: Int) = update { it.copy(idleTimeoutSeconds = v) }

        fun setAlertDelay(v: Int) = update { it.copy(soundDelaySeconds = v) }

        fun setFlashInterval(v: Int) = update { it.copy(flashIntervalMs = v) }

        fun setMaxDuration(v: Int) = update { it.copy(alertMaxDurationSeconds = v) }

        // ---- text ----
        fun setMuteText(v: String) = update { it.copy(keywordFilterText = v) }

        fun setAllowText(v: String) = update { it.copy(keywordAllowText = v) }

        fun setFlashColorA(hex: String) = update { it.copy(flashColorA = hex) }

        fun setFlashColorB(hex: String) = update { it.copy(flashColorB = hex) }

        // ---- sound ----
        fun loadSoundOptions() {
            if (_soundOptions.value.isNotEmpty()) return
            viewModelScope.launch { _soundOptions.value = withContext(Dispatchers.IO) { sounds.enumerate() } }
        }

        /** Tapping a row selects AND previews it, like the system ringtone picker. */
        fun pickSound(uri: String) {
            viewModelScope.launch {
                val previous = repository.current().soundUri
                if (previous != uri && sounds.isCustom(previous)) sounds.release(previous)
                repository.update { it.copy(soundUri = uri) }
                preview.play(uri)
            }
        }

        fun pickSoundFile(uri: Uri) {
            viewModelScope.launch {
                val previous = repository.current().soundUri
                val ok = withContext(Dispatchers.IO) { sounds.adoptPickedFile(uri, previous) }
                if (ok) {
                    repository.update { it.copy(soundUri = uri.toString()) }
                    preview.play(uri.toString())
                }
            }
        }

        fun togglePreview() {
            viewModelScope.launch { preview.toggle(repository.current().soundUri) }
        }

        fun stopPreview() {
            preview.stop()
            vibrator.cancel()
        }

        // ---- actions ----

        /** Bypasses the away gate, uses current settings (Windows TestOverlay). */
        fun testAlert(): Boolean {
            preview.stop()
            return alerts.fire(SlackEvent.TEST, state.value.settings, isTest = true)
        }

        suspend fun readLog(): String = withContext(Dispatchers.IO) { log.readAll() }

        // ---- backup & restore ----

        /** One-shot results for the snackbar. */
        private val _backupMessages = Channel<BackupMessage>(Channel.BUFFERED)
        val backupMessages: Flow<BackupMessage> = _backupMessages.receiveAsFlow()

        /** A decoded backup awaiting the user's confirmation (drives the confirm dialog). */
        private val _pendingRestore = MutableStateFlow<RestorePreview?>(null)
        val pendingRestore: StateFlow<RestorePreview?> = _pendingRestore

        fun exportSettings(uri: Uri) {
            viewModelScope.launch {
                _backupMessages.send(if (backup.export(uri)) BackupMessage.Saved else BackupMessage.SaveFailed)
            }
        }

        fun readBackup(uri: Uri) {
            viewModelScope.launch {
                when (val r = backup.read(uri)) {
                    is ReadResult.Ok -> _pendingRestore.value = r.preview
                    is ReadResult.Failed -> _backupMessages.send(BackupMessage.RestoreFailed(r.reason))
                }
            }
        }

        fun reportNoFilePicker() {
            viewModelScope.launch { _backupMessages.send(BackupMessage.NoFilePicker) }
        }

        fun cancelRestore() {
            _pendingRestore.value = null
        }

        fun confirmRestore() {
            val preview = _pendingRestore.value ?: return
            _pendingRestore.value = null
            viewModelScope.launch {
                val previous = backup.apply(preview)
                _backupMessages.send(BackupMessage.Restored(previous))
            }
        }

        fun undoRestore(previous: AppSettings) {
            viewModelScope.launch { backup.undo(previous) }
        }

        fun clearLog() {
            viewModelScope.launch(Dispatchers.IO) { log.clear() }
        }

        override fun onCleared() {
            stopPreview()
        }
    }
