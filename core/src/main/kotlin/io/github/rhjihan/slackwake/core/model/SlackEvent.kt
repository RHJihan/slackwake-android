// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.core.model

/** Minimal Slack ping payload shown on the alert screen. Never persisted. */
data class SlackEvent(
    val sender: String?,
    val channel: String?,
    val text: String?,
) {
    companion object {
        /** Sample shown by "Test overlay". */
        val TEST =
            SlackEvent(
                sender = "Test user",
                channel = "#slackwake-test",
                text = "This is a preview of your SlackWake overlay. Tap anywhere, press back, or press any key to dismiss.",
            )
    }
}
