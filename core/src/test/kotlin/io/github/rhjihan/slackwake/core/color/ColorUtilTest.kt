// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.core.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorUtilTest {
    @Test fun `parses all hex forms with or without hash`() {
        assertEquals(Rgb(0x12, 0x34, 0x56), ColorUtil.tryParseHex("#123456"))
        assertEquals(Rgb(0x12, 0x34, 0x56), ColorUtil.tryParseHex("123456"))
        assertEquals(Rgb(0x11, 0x22, 0x33), ColorUtil.tryParseHex("#123"))
        assertEquals(Rgb(0x11, 0x22, 0x33), ColorUtil.tryParseHex("#f123"))
        assertEquals(Rgb(0x12, 0x34, 0x56), ColorUtil.tryParseHex("#FF123456"))
        assertEquals(Rgb(0xAB, 0xCD, 0xEF), ColorUtil.tryParseHex("  #abcdef  "))
    }

    @Test fun `rejects malformed input instead of guessing`() {
        listOf(null, "", " ", "#", "#12", "#12345", "#1234567", "#GGGGGG", "red", "#12 456").forEach {
            assertNull("input=$it", ColorUtil.tryParseHex(it))
        }
    }

    @Test fun `parse falls back to black and sanitize normalizes`() {
        assertEquals(Rgb.BLACK, ColorUtil.parse("garbage"))
        assertEquals("#000000", ColorUtil.sanitizeHex(null))
        assertEquals("#AABBCC", ColorUtil.sanitizeHex("abc"))
        assertEquals("#FFFFFF", ColorUtil.sanitizeHex("#ffffff"))
    }

    @Test fun `contrast picks black on light and white on dark`() {
        assertEquals(Rgb.WHITE, ColorUtil.contrastingTextColor(Rgb.BLACK))
        assertEquals(Rgb.BLACK, ColorUtil.contrastingTextColor(Rgb.WHITE))
        assertEquals(Rgb.BLACK, ColorUtil.contrastingTextColor(Rgb(0xFF, 0xD4, 0x00))) // yellow
        assertEquals(Rgb.WHITE, ColorUtil.contrastingTextColor(Rgb(0x3D, 0x5A, 0xFE))) // indigo: L≈0.155
        assertEquals(Rgb.BLACK, ColorUtil.contrastingTextColor(Rgb(0x29, 0x79, 0xFF))) // light blue: L≈0.214
        assertEquals(Rgb.BLACK, ColorUtil.contrastingTextColor(Rgb(0xFF, 0x00, 0x00))) // red: L=0.2126 > 0.179
    }

    @Test fun `luminance threshold boundary is 0_179`() {
        // #767676 has L ≈ 0.181 (> 0.179 → black); #757575 has L ≈ 0.178 (→ white).
        assertEquals(Rgb.BLACK, ColorUtil.contrastingTextColor(Rgb(0x76, 0x76, 0x76)))
        assertEquals(Rgb.WHITE, ColorUtil.contrastingTextColor(Rgb(0x75, 0x75, 0x75)))
    }

    @Test fun `relative luminance and contrast ratio match WCAG reference values`() {
        assertEquals(0.0, ColorUtil.relativeLuminance(Rgb.BLACK), 1e-9)
        assertEquals(1.0, ColorUtil.relativeLuminance(Rgb.WHITE), 1e-9)
        assertEquals(21.0, ColorUtil.contrastRatio(Rgb.BLACK, Rgb.WHITE), 1e-9)
        assertEquals(1.0, ColorUtil.contrastRatio(Rgb.WHITE, Rgb.WHITE), 1e-9)
    }

    @Test fun `chosen text color always reaches at least 4_5 to 1 against all presets`() {
        val presets =
            listOf(
                "#000000",
                "#FFFFFF",
                "#FF0000",
                "#FF7A00",
                "#FFD400",
                "#3ACC90",
                "#00C853",
                "#00B8D4",
                "#2979FF",
                "#3D5AFE",
                "#7C4DFF",
                "#D500F9",
                "#FF1744",
                "#FF4081",
                "#795548",
                "#9E9E9E",
            )
        presets.forEach {
            val bg = ColorUtil.parse(it)
            val ratio = ColorUtil.contrastRatio(bg, ColorUtil.contrastingTextColor(bg))
            assertTrue("$it ratio=$ratio", ratio >= 4.5)
        }
    }

    @Test fun `hsv round-trips every 7th rgb value`() {
        for (r in 0..255 step 7) {
            for (g in 0..255 step 7) {
                for (b in 0..255 step 7) {
                    val c = Rgb(r, g, b)
                    val hsv = ColorUtil.toHsv(c)
                    assertEquals(c, ColorUtil.fromHsv(hsv.hue, hsv.saturation, hsv.value))
                }
            }
        }
    }

    @Test fun `hsv of primaries`() {
        assertEquals(Hsv(0.0, 1.0, 1.0), ColorUtil.toHsv(Rgb(255, 0, 0)))
        assertEquals(120.0, ColorUtil.toHsv(Rgb(0, 255, 0)).hue, 1e-9)
        assertEquals(240.0, ColorUtil.toHsv(Rgb(0, 0, 255)).hue, 1e-9)
        assertEquals(Hsv(0.0, 0.0, 0.0), ColorUtil.toHsv(Rgb.BLACK))
    }

    @Test fun `fromHsv wraps hue and clamps saturation and value`() {
        assertEquals(Rgb(255, 0, 0), ColorUtil.fromHsv(360.0, 1.0, 1.0))
        assertEquals(Rgb(255, 0, 0), ColorUtil.fromHsv(-360.0, 2.0, 5.0))
        assertEquals(Rgb(0, 0, 255), ColorUtil.fromHsv(-120.0, 1.0, 1.0))
    }

    @Test fun `palette derives contrast colors with Windows alphas and opaque background`() {
        val p = OverlayPalette.from(Rgb.WHITE)
        assertEquals(0xFFFFFFFF.toInt(), p.background)
        assertEquals(0xFF000000.toInt(), p.foreground)
        assertEquals(0x22000000, p.messageBox)
        assertEquals(0xAA000000.toInt(), p.hint)
    }
}
