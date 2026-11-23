// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.core.alert

import io.github.rhjihan.slackwake.core.settings.AppSettings
import io.github.rhjihan.slackwake.core.vibration.VibrationMath
import io.github.rhjihan.slackwake.core.vibration.VibrationMode
import io.github.rhjihan.slackwake.core.vibration.VibrationPattern
import io.github.rhjihan.slackwake.core.vibration.Waveform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Plays one pass of the alert sound. */
interface SoundSink {
    /**
     * Play once and suspend until the clip ends (or fails). Must stop playback promptly
     * when the calling coroutine is cancelled — that is how dismissal and the auto-stop
     * cap cut the sound off mid-clip.
     */
    suspend fun playOnce()
}

/** Drives the vibration motor. */
interface VibrationSink {
    /**
     * Play [waveform] once and suspend for its duration. Must stop the motor promptly
     * when the calling coroutine is cancelled (dismissal / auto-stop).
     */
    suspend fun play(waveform: Waveform)
}

/**
 * Snapshot of the settings that drive one alert, taken when it fires so every display
 * agrees even if the user edits settings mid-alert (as App.ShowOverlay did).
 */
data class AlertPlan(
    val soundEnabled: Boolean,
    val soundLoop: Boolean,
    val soundUri: String,
    val flashEnabled: Boolean,
    val flashIntervalMs: Int,
    val flashColorA: String,
    val flashColorB: String,
    val delay: Duration,
    /** Cap for the sound, measured from when it starts; null = until dismissed. */
    val soundCap: Duration?,
    /** Cap for the flash, measured from when it starts; null = until dismissed. */
    val flashCap: Duration?,
    val vibrationEnabled: Boolean = false,
    val vibrationPattern: VibrationPattern = VibrationPattern.DEFAULT,
    val vibrationIntensityPercent: Int = 100,
    val vibrationMode: VibrationMode = VibrationMode.FIXED,
    val vibrationRamp: Duration = 30.seconds,
    /** Cap for the vibration, measured from when it starts; null = until dismissed. */
    val vibrationCap: Duration? = null,
    /**
     * How long the alert keeps the screen on, measured from when it fires: alert delay +
     * max duration once nothing on screen is still moving, i.e. auto-stop is on and the
     * visual flash is either off or stopped by it. Sound and vibration don't need the
     * screen, so they never keep it lit. Null = hold it until dismissed (the flash keeps
     * running). The alert itself stays until the user dismisses it, as on Windows; this
     * only stops an unattended phone from burning battery/OLED for hours.
     */
    val keepScreenOnFor: Duration? = null,
) {
    companion object {
        /** Gap between the end of one play and the start of the next (LoopingSoundPlayer). */
        val LOOP_GAP = 500.milliseconds

        /**
         * True when the screen may be released at the cap: auto-stop is on and the visual
         * flash (the only thing that needs the screen) is off or stopped by it. Whether
         * sound or vibration keep going doesn't matter.
         */
        fun screenReleasesAtCap(s: AppSettings): Boolean = s.alertAutoStopEnabled && (!s.flashEnabled || s.alertAutoStopIncludesVisual)

        fun from(settings: AppSettings): AlertPlan {
            val s = settings.normalized()
            val cap = s.alertMaxDurationSeconds.seconds
            return AlertPlan(
                soundEnabled = s.soundEnabled,
                soundLoop = s.soundLoop,
                soundUri = s.soundUri,
                flashEnabled = s.flashEnabled,
                flashIntervalMs = s.flashIntervalMs,
                flashColorA = s.flashColorA,
                flashColorB = s.flashColorB,
                delay = s.soundDelaySeconds.seconds,
                soundCap = cap.takeIf { s.alertAutoStopEnabled && s.alertAutoStopIncludesSound },
                flashCap = cap.takeIf { s.alertAutoStopEnabled && s.alertAutoStopIncludesVisual },
                vibrationEnabled = s.vibrationEnabled,
                vibrationPattern = VibrationPattern.fromKey(s.vibrationPattern),
                vibrationIntensityPercent = s.vibrationIntensityPercent,
                vibrationMode = VibrationMode.fromKey(s.vibrationMode),
                vibrationRamp = s.vibrationRampSeconds.seconds,
                vibrationCap = cap.takeIf { s.alertAutoStopEnabled && s.alertAutoStopIncludesVibration },
                keepScreenOnFor = (s.soundDelaySeconds.seconds + cap).takeIf { screenReleasesAtCap(s) },
            )
        }
    }
}

/**
 * Timing for one alert: port of OverlayWindow.BeginAlertsAfterDelay + LoopingSoundPlayer
 * + the flash-stop timer.
 *
 *  - Sound and flash both wait out [AlertPlan.delay] ("alert delay"), then start together.
 *  - Sound: one play; if looping, replay with a 0.5 s gap after each play ends. The cap
 *    applies even to a single non-looping play (a clip longer than the cap is cut off).
 *  - Flash: runs until the cap (if any), then the screen returns to the static look.
 *  - Vibration (Android-only): repeats the pattern until the cap; in gradual mode each
 *    cycle is scaled up from 15% to the chosen intensity over the ramp time.
 *  - [stop] (dismissal) cancels everything immediately.
 */
class AlertSession(
    private val plan: AlertPlan,
    private val sound: SoundSink,
    private val scope: CoroutineScope,
    private val vibration: VibrationSink? = null,
) {
    private val _flashing = MutableStateFlow(false)

    /** True while the alert screen should be strobing. */
    val flashing: StateFlow<Boolean> = _flashing.asStateFlow()

    private val _soundPlaying = MutableStateFlow(false)
    val soundPlaying: StateFlow<Boolean> = _soundPlaying.asStateFlow()

    private val _vibrating = MutableStateFlow(false)
    val vibrating: StateFlow<Boolean> = _vibrating.asStateFlow()

    private var job: Job? = null

    fun start() {
        check(job == null) { "AlertSession already started" }
        job =
            scope.launch {
                if (plan.delay.isPositive()) delay(plan.delay)
                launch { runFlash() }
                launch { runSound() }
                launch { runVibration() }
            }
    }

    fun stop() {
        job?.cancel()
        _flashing.value = false
        _soundPlaying.value = false
        _vibrating.value = false
    }

    private suspend fun runFlash() {
        if (!plan.flashEnabled) return
        _flashing.value = true
        try {
            val cap = plan.flashCap ?: return // runs until stop() cancels us
            delay(cap)
        } finally {
            if (plan.flashCap != null) _flashing.value = false
        }
    }

    private suspend fun runSound() {
        if (!plan.soundEnabled) return
        _soundPlaying.value = true
        try {
            val loop: suspend () -> Unit = {
                do {
                    sound.playOnce()
                    if (!plan.soundLoop) break
                    delay(AlertPlan.LOOP_GAP)
                } while (true)
            }
            val cap = plan.soundCap
            if (cap == null) loop() else withTimeoutOrNull(cap) { loop() }
        } finally {
            withContext(NonCancellable) { _soundPlaying.value = false }
        }
    }

    private suspend fun runVibration() {
        val sink = vibration ?: return
        if (!plan.vibrationEnabled) return
        _vibrating.value = true
        try {
            val pattern = plan.vibrationPattern
            val trailingPause = pattern.pulses.last().offMs
            val loop: suspend () -> Unit = {
                // Elapsed vibrating time = whole cycles played so far (no clock needed).
                var elapsedMs = 0L
                while (true) {
                    val scale = VibrationMath.rampScale(plan.vibrationMode, elapsedMs, plan.vibrationRamp.inWholeMilliseconds)
                    sink.play(VibrationMath.waveform(pattern, plan.vibrationIntensityPercent, scale))
                    delay(trailingPause)
                    elapsedMs += pattern.cycleMs
                }
            }
            val cap = plan.vibrationCap
            if (cap == null) loop() else withTimeoutOrNull(cap) { loop() }
        } finally {
            withContext(NonCancellable) { _vibrating.value = false }
        }
    }
}
