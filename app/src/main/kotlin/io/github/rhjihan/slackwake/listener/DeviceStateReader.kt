// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.listener

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.rhjihan.slackwake.core.gate.DeviceState
import javax.inject.Inject
import javax.inject.Singleton

/** Live lock/screen state, sampled when a notification is evaluated. */
@Singleton
class DeviceStateReader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun read(): DeviceState {
            val keyguard = context.getSystemService<KeyguardManager>()
            val power = context.getSystemService<PowerManager>()
            return DeviceState(
                keyguardLocked = keyguard?.isKeyguardLocked ?: false,
                interactive = power?.isInteractive ?: true,
            )
        }
    }
