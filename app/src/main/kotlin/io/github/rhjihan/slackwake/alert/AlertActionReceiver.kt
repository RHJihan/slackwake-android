// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** The notification's Dismiss action and swipe-away (delete intent). Not exported. */
@AndroidEntryPoint
class AlertActionReceiver : BroadcastReceiver() {
    @Inject lateinit var controller: AlertController

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == ACTION_DISMISS) controller.dismiss("notification")
    }

    companion object {
        const val ACTION_DISMISS = "io.github.rhjihan.slackwake.action.DISMISS_ALERT"
    }
}
