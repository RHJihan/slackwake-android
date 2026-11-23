// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.vibration

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.rhjihan.slackwake.core.alert.VibrationSink
import io.github.rhjihan.slackwake.core.vibration.VibrationMath
import io.github.rhjihan.slackwake.core.vibration.VibrationMode
import io.github.rhjihan.slackwake.core.vibration.VibrationPattern
import io.github.rhjihan.slackwake.core.vibration.Waveform
import io.github.rhjihan.slackwake.data.diagnostics.DiagnosticLog
import io.github.rhjihan.slackwake.sound.AlarmSoundFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/** The vibration motor, behind an interface so tests can substitute it. */
interface AlertVibrationSource {
    val hasVibrator: Boolean

    /** False on motors that are only on/off: intensity and gradual mode then have no effect. */
    val hasAmplitudeControl: Boolean

    /** A sink for one alert, or null when the device cannot vibrate. */
    fun create(): VibrationSink?

    /** Short demo of a pattern (gradual mode is shown as a compressed crescendo). */
    fun preview(
        pattern: VibrationPattern,
        intensityPercent: Int,
        mode: VibrationMode,
    )

    fun cancel()
}

/**
 * Vibrates with ALARM usage: Android 13+ drops vibrations from background apps unless
 * they are alarm/ringtone/notification class, and alarm vibrations are delivered in
 * silent/vibrate ringer modes and through DND's "Alarms" exception — the same treatment
 * the alarm sound gets.
 */
@Singleton
class DeviceVibrator
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val log: DiagnosticLog,
    ) : AlertVibrationSource {
        private val vibrator: Vibrator? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                context.getSystemService(Vibrator::class.java)
            }

        override val hasVibrator: Boolean get() = vibrator?.hasVibrator() == true

        override val hasAmplitudeControl: Boolean get() = vibrator?.hasAmplitudeControl() == true

        override fun create(): VibrationSink? =
            if (!hasVibrator) {
                log.write("No vibrator on this device; vibration skipped")
                null
            } else {
                object : VibrationSink {
                    override suspend fun play(waveform: Waveform) {
                        vibrate(waveform)
                        try {
                            delay(waveform.durationMs)
                        } catch (e: CancellationException) {
                            cancel()
                            throw e
                        }
                    }
                }
            }

        override fun preview(
            pattern: VibrationPattern,
            intensityPercent: Int,
            mode: VibrationMode,
        ) {
            if (hasVibrator) vibrate(VibrationMath.preview(pattern, intensityPercent, mode))
        }

        override fun cancel() {
            runCatching { vibrator?.cancel() }
        }

        private fun vibrate(waveform: Waveform) {
            val v = vibrator ?: return
            val effect =
                if (v.hasAmplitudeControl()) {
                    VibrationEffect.createWaveform(waveform.timings, waveform.amplitudes, NO_REPEAT)
                } else {
                    VibrationEffect.createWaveform(waveform.timings, NO_REPEAT)
                }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
                } else {
                    @Suppress("DEPRECATION") // the AudioAttributes overload is the only way to tag ALARM usage before 33
                    v.vibrate(effect, AlarmSoundFactory.ALARM_ATTRIBUTES)
                }
            }.onFailure { log.write("Vibration failed: ${it.javaClass.simpleName}") }
        }

        private companion object {
            const val NO_REPEAT = -1
        }
    }
