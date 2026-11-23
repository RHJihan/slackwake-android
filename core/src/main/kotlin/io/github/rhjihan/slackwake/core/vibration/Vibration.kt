// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.core.vibration

import kotlin.math.roundToInt

/** One pulse: motor on for [onMs] at [weight] × intensity, then off for [offMs]. */
data class Pulse(
    val onMs: Long,
    val offMs: Long,
    val weight: Float = 1f,
)

/**
 * Alert vibration patterns. Names and rhythms follow the iOS vibration set (Alert,
 * Heartbeat, Rapid, S.O.S., Staccato, Symphony); "Alert" is the classic alarm-clock
 * 500 ms on / 500 ms off used by Android Clock. One cycle is 1–3.5 s and repeats until
 * the alert is dismissed or auto-stopped. Stored in settings by [key].
 */
enum class VibrationPattern(
    val key: String,
    val pulses: List<Pulse>,
) {
    ALERT("Alert", listOf(Pulse(500, 500))),
    HEARTBEAT("Heartbeat", listOf(Pulse(100, 120), Pulse(140, 800, weight = 0.6f))),
    RAPID("Rapid", List(5) { Pulse(80, 80) } + Pulse(80, 600)),
    SOS(
        "SOS",
        // · · ·   — — —   · · ·   (dot 150, dash 450, element gap 150, letter gap 450)
        List(2) { Pulse(150, 150) } + Pulse(150, 450) +
            List(2) { Pulse(450, 150) } + Pulse(450, 450) +
            List(2) { Pulse(150, 150) } + Pulse(150, 1000),
    ),
    STACCATO("Staccato", List(3) { Pulse(50, 150) } + Pulse(50, 900)),
    SYMPHONY(
        "Symphony",
        listOf(Pulse(100, 100, 0.4f), Pulse(150, 100, 0.6f), Pulse(200, 100, 0.8f), Pulse(400, 700, 1f)),
    ),
    ;

    /** Length of one cycle (all pulses and pauses). */
    val cycleMs: Long get() = pulses.sumOf { it.onMs + it.offMs }

    companion object {
        val DEFAULT = ALERT

        /** Unknown / hand-edited values fall back to the default instead of failing. */
        fun fromKey(key: String?): VibrationPattern = entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: DEFAULT
    }
}

/** Fixed: constant intensity. Gradual: crescendo from quiet to the chosen intensity. */
enum class VibrationMode(
    val key: String,
) {
    FIXED("Fixed"),
    GRADUAL("Gradual"),
    ;

    companion object {
        fun fromKey(key: String?): VibrationMode = entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: FIXED
    }
}

/**
 * A ready-to-play waveform for Android's `VibrationEffect.createWaveform(timings,
 * amplitudes, repeat = -1)`: timings alternate off/on starting with an off segment,
 * amplitudes are 0 for off segments and 1..255 for on segments.
 */
data class Waveform(
    val timings: LongArray,
    val amplitudes: IntArray,
) {
    val durationMs: Long get() = timings.sum()

    override fun equals(other: Any?) = other is Waveform && timings.contentEquals(other.timings) && amplitudes.contentEquals(other.amplitudes)

    override fun hashCode() = 31 * timings.contentHashCode() + amplitudes.contentHashCode()
}

object VibrationMath {
    /** Gradual mode starts at this fraction of the chosen intensity (crescendo floor). */
    const val RAMP_START = 0.15f

    const val MAX_AMPLITUDE = 255

    /**
     * Crescendo multiplier after [elapsedMs] of vibrating: linear from [RAMP_START] to 1
     * over [rampMs], then flat. Fixed mode is always 1.
     */
    fun rampScale(
        mode: VibrationMode,
        elapsedMs: Long,
        rampMs: Long,
    ): Float {
        if (mode == VibrationMode.FIXED || rampMs <= 0) return 1f
        val t = (elapsedMs.toFloat() / rampMs).coerceIn(0f, 1f)
        return RAMP_START + (1f - RAMP_START) * t
    }

    /** Motor amplitude (1..255) for an intensity percentage, pulse weight and ramp scale. */
    fun amplitude(
        intensityPercent: Int,
        weight: Float,
        scale: Float,
    ): Int = (MAX_AMPLITUDE * intensityPercent.coerceIn(1, 100) / 100f * weight * scale).roundToInt().coerceIn(1, MAX_AMPLITUDE)

    /** One cycle of [pattern] as a waveform at the given intensity and ramp scale. */
    fun waveform(
        pattern: VibrationPattern,
        intensityPercent: Int,
        scale: Float = 1f,
    ): Waveform {
        val timings = LongArray(pattern.pulses.size * 2)
        val amplitudes = IntArray(pattern.pulses.size * 2)
        // Android waveforms start with an "off" segment; fold each pulse's trailing pause
        // into the next segment pair so the cycle begins with the motor on.
        pattern.pulses.forEachIndexed { i, p ->
            val prevOff = if (i == 0) 0L else pattern.pulses[i - 1].offMs
            timings[2 * i] = prevOff
            amplitudes[2 * i] = 0
            timings[2 * i + 1] = p.onMs
            amplitudes[2 * i + 1] = amplitude(intensityPercent, p.weight, scale)
        }
        return Waveform(timings, amplitudes)
    }

    /** Fixed-mode previews last at least this long (and always at least two cycles). */
    const val PREVIEW_MS = 2_500L

    /** Gradual-mode previews play this many cycles, stepping from [RAMP_START] to full. */
    const val PREVIEW_RAMP_STEPS = 4

    /**
     * A few cycles of [pattern] joined into one waveform for the settings "Preview"
     * button. Gradual mode is demonstrated as a compressed crescendo so the user can feel
     * the ramp without waiting the full ramp time.
     */
    fun preview(
        pattern: VibrationPattern,
        intensityPercent: Int,
        mode: VibrationMode,
    ): Waveform {
        val scales =
            if (mode == VibrationMode.GRADUAL) {
                List(PREVIEW_RAMP_STEPS) { i -> RAMP_START + (1f - RAMP_START) * i / (PREVIEW_RAMP_STEPS - 1) }
            } else {
                List(maxOf(2, ((PREVIEW_MS + pattern.cycleMs - 1) / pattern.cycleMs).toInt())) { 1f }
            }
        val timings = mutableListOf<Long>()
        val amplitudes = mutableListOf<Int>()
        scales.forEachIndexed { k, scale ->
            val cycle = waveform(pattern, intensityPercent, scale)
            cycle.timings.forEachIndexed { i, t ->
                // The leading "off" of every cycle after the first is the previous cycle's tail pause.
                timings += if (i == 0 && k > 0) pattern.pulses.last().offMs else t
                amplitudes += cycle.amplitudes[i]
            }
        }
        return Waveform(timings.toLongArray(), amplitudes.toIntArray())
    }
}
