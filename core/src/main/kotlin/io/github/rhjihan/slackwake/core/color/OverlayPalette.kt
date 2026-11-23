// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.core.color

/**
 * The four colors the alert screen is drawn with, as packed ARGB ints. Port of
 * OverlayWindow.BuildPalette with one deliberate change: the background is opaque
 * (Windows used 0xE6 ≈ 90%), because a translucent window over the lock screen is
 * unreliable on several OEM builds and would show the lock-screen wallpaper through.
 */
data class OverlayPalette(
    val background: Int,
    val foreground: Int,
    val messageBox: Int,
    val hint: Int,
) {
    companion object {
        const val BACKGROUND_ALPHA = 0xFF
        const val MESSAGE_BOX_ALPHA = 0x22
        const val HINT_ALPHA = 0xAA

        /** Palette derived from a flash color: text picks black/white by WCAG contrast. */
        fun from(base: Rgb): OverlayPalette {
            val contrast = ColorUtil.contrastingTextColor(base)
            return OverlayPalette(
                background = base.toArgb(BACKGROUND_ALPHA),
                foreground = contrast.toArgb(),
                messageBox = contrast.toArgb(MESSAGE_BOX_ALPHA),
                hint = contrast.toArgb(HINT_ALPHA),
            )
        }

        /**
         * The static look shown when flash is off, during the alert delay, and after
         * the auto-stop cap ends the flash (Windows reverted to its XAML defaults, not
         * to color A). Hint is #999999 as in OverlayWindow.xaml.
         */
        val DEFAULT =
            OverlayPalette(
                background = Rgb.BLACK.toArgb(BACKGROUND_ALPHA),
                foreground = Rgb.WHITE.toArgb(),
                messageBox = Rgb.WHITE.toArgb(MESSAGE_BOX_ALPHA),
                hint = Rgb(0x99, 0x99, 0x99).toArgb(),
            )
    }
}
