// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.core.color

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Opaque sRGB color, 0–255 per channel. Kept free of android.graphics so it is JVM-testable. */
data class Rgb(
    val r: Int,
    val g: Int,
    val b: Int,
) {
    init {
        require(r in 0..255 && g in 0..255 && b in 0..255) { "channel out of range: $this" }
    }

    /** Packed 0xAARRGGBB with the given alpha, the layout android.graphics.Color uses. */
    fun toArgb(alpha: Int = 0xFF): Int = (alpha and 0xFF shl 24) or (r shl 16) or (g shl 8) or b

    companion object {
        val BLACK = Rgb(0, 0, 0)
        val WHITE = Rgb(255, 255, 255)
    }
}

data class Hsv(
    val hue: Double,
    val saturation: Double,
    val value: Double,
)

/**
 * Port of the Windows `ColorUtil`. Differences: named colors ("red") are not
 * accepted — settings are always stored as `#RRGGBB`, so names only ever mattered
 * for hand-edited files and the picker's text box.
 */
object ColorUtil {
    /** Parse a hex color, falling back to black on malformed input (never throws). */
    fun parse(hex: String?): Rgb = tryParseHex(hex) ?: Rgb.BLACK

    /** `#RRGGBB` (uppercase). */
    fun toHex(c: Rgb): String = "#%02X%02X%02X".format(c.r, c.g, c.b)

    /** Normalize any stored value to `#RRGGBB`; invalid becomes `#000000`. */
    fun sanitizeHex(hex: String?): String = toHex(parse(hex))

    /**
     * Lenient parse for the picker's text field: `#RGB`, `#ARGB`, `#RRGGBB`,
     * `#AARRGGBB`, with or without the leading `#`. Alpha is accepted and discarded
     * (WPF's converter did the same when round-tripped through ToHex). Returns null
     * instead of black so a half-typed value leaves the current color untouched.
     */
    fun tryParseHex(text: String?): Rgb? {
        if (text.isNullOrBlank()) return null
        val digits = text.trim().removePrefix("#")
        if (digits.isEmpty() || !digits.all { it.isHexDigit() }) return null
        val rgb =
            when (digits.length) {
                3 -> digits
                4 -> digits.substring(1)
                6 -> digits
                8 -> digits.substring(2)
                else -> return null
            }
        val expanded = if (rgb.length == 3) rgb.map { "$it$it" }.joinToString("") else rgb
        return Rgb(
            expanded.substring(0, 2).toInt(16),
            expanded.substring(2, 4).toInt(16),
            expanded.substring(4, 6).toInt(16),
        )
    }

    private fun Char.isHexDigit() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    /** RGB → HSV (hue 0–360, saturation and value 0–1). */
    fun toHsv(c: Rgb): Hsv {
        val r = c.r / 255.0
        val g = c.g / 255.0
        val b = c.b / 255.0
        val max = max(r, max(g, b))
        val min = min(r, min(g, b))
        val delta = max - min

        var hue = 0.0
        if (delta > 0) {
            hue =
                when (max) {
                    r -> 60 * (((g - b) / delta) % 6)
                    g -> 60 * (((b - r) / delta) + 2)
                    else -> 60 * (((r - g) / delta) + 4)
                }
        }
        if (hue < 0) hue += 360
        val saturation = if (max <= 0) 0.0 else delta / max
        return Hsv(hue, saturation, max)
    }

    /** HSV → opaque RGB. Inverse of [toHsv]. */
    fun fromHsv(
        hueIn: Double,
        saturationIn: Double,
        valueIn: Double,
    ): Rgb {
        var hue = hueIn % 360
        if (hue < 0) hue += 360
        val saturation = saturationIn.coerceIn(0.0, 1.0)
        val value = valueIn.coerceIn(0.0, 1.0)

        val chroma = value * saturation
        val x = chroma * (1 - abs((hue / 60 % 2) - 1))
        val m = value - chroma

        val (r, g, b) =
            when ((hue / 60).toInt() % 6) {
                0 -> Triple(chroma, x, 0.0)
                1 -> Triple(x, chroma, 0.0)
                2 -> Triple(0.0, chroma, x)
                3 -> Triple(0.0, x, chroma)
                4 -> Triple(x, 0.0, chroma)
                else -> Triple(chroma, 0.0, x)
            }
        return Rgb(
            ((r + m) * 255).roundToInt(),
            ((g + m) * 255).roundToInt(),
            ((b + m) * 255).roundToInt(),
        )
    }

    /** WCAG 2.x relative luminance (0 = black, 1 = white). */
    fun relativeLuminance(c: Rgb): Double = 0.2126 * channel(c.r / 255.0) + 0.7152 * channel(c.g / 255.0) + 0.0722 * channel(c.b / 255.0)

    /**
     * Black or white, whichever is more legible on [background]. Same 0.179
     * luminance threshold as the Windows app.
     */
    fun contrastingTextColor(background: Rgb): Rgb = if (relativeLuminance(background) > 0.179) Rgb.BLACK else Rgb.WHITE

    /** WCAG contrast ratio between two colors (1–21). */
    fun contrastRatio(
        a: Rgb,
        b: Rgb,
    ): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun channel(srgb: Double): Double = if (srgb <= 0.03928) srgb / 12.92 else ((srgb + 0.055) / 1.055).pow(2.4)
}
