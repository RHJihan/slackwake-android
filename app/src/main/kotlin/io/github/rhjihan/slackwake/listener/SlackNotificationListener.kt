// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.listener

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Event-driven replacement for the Windows 1.5 s UserNotificationListener poll. Bound by
 * the system once the user grants notification access, and re-bound after boot — which
 * is why "Start with Windows" has no Android equivalent.
 *
 * Also owns the runtime screen-state receiver: SCREEN_OFF / SCREEN_ON / USER_PRESENT
 * cannot be declared in the manifest, so they are registered for as long as the system
 * keeps this service bound.
 */
@AndroidEntryPoint
class SlackNotificationListener : NotificationListenerService() {
    @Inject lateinit var monitor: SlackMonitor

    private var receiverRegistered = false

    private val screenReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> monitor.onScreenOff()
                    Intent.ACTION_SCREEN_ON -> monitor.onScreenOn()
                    Intent.ACTION_USER_PRESENT -> monitor.onUserPresent()
                }
            }
        }

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (!receiverRegistered) {
            val filter =
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_USER_PRESENT)
                }
            ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
        val active = runCatching { activeNotifications.orEmpty() }.getOrDefault(emptyArray())
        monitor.onConnected(active.map { it.toPosted() })
    }

    override fun onListenerDisconnected() {
        unregisterScreenReceiver()
        monitor.onDisconnected()
        requestRebind(ComponentName(this, SlackNotificationListener::class.java))
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        monitor.onPosted(sbn.toPosted())
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == SLACK_PACKAGE) monitor.onRemoved(sbn.key)
    }

    override fun onDestroy() {
        unregisterScreenReceiver()
        super.onDestroy()
    }

    private fun unregisterScreenReceiver() {
        if (receiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            receiverRegistered = false
        }
    }

    private fun StatusBarNotification.toPosted() = Posted(key, packageName, notification)
}
