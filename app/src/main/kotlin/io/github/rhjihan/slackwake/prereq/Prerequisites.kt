// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.prereq

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.rhjihan.slackwake.alert.AlertNotifier
import io.github.rhjihan.slackwake.core.status.Problem
import io.github.rhjihan.slackwake.listener.SLACK_PACKAGE
import io.github.rhjihan.slackwake.listener.SlackNotificationListener
import javax.inject.Inject
import javax.inject.Singleton

/** Snapshot of everything SlackWake needs from the system. */
data class PrereqState(
    val listenerAccess: Boolean,
    val notificationsAllowed: Boolean,
    val alertChannelOn: Boolean,
    /** Always true below Android 14, where the manifest permission is enough. */
    val fullScreenAllowed: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val slackInstalled: Boolean,
) {
    /** Problems that change what an alert can do (battery is guidance only). */
    fun problems(listenerConnected: Boolean): List<Problem> =
        buildList {
            if (!listenerAccess) {
                add(Problem.LISTENER_ACCESS_MISSING)
            } else if (!listenerConnected) {
                add(Problem.LISTENER_DISCONNECTED)
            }
            if (!notificationsAllowed) add(Problem.NOTIFICATIONS_BLOCKED)
            if (notificationsAllowed && !alertChannelOn) add(Problem.ALERT_CHANNEL_BLOCKED)
            if (!fullScreenAllowed) add(Problem.FULL_SCREEN_DENIED)
        }

    /** Missing items the settings screen should flag. */
    val missingCount: Int
        get() = listOf(listenerAccess, notificationsAllowed && alertChannelOn, fullScreenAllowed).count { !it }

    companion object {
        val ALL_GRANTED = PrereqState(true, true, true, true, true, true)
    }
}

@Singleton
class PrerequisiteChecker
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val notifier: AlertNotifier,
    ) {
        fun check(): PrereqState {
            notifier.ensureChannel()
            return PrereqState(
                listenerAccess = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName),
                notificationsAllowed = notifier.canPostNotifications(),
                alertChannelOn = !notifier.isChannelBlocked(),
                fullScreenAllowed = notifier.canUseFullScreenIntent(),
                ignoringBatteryOptimizations =
                    context.getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(context.packageName) ?: true,
                slackInstalled = isInstalled(SLACK_PACKAGE),
            )
        }

        private fun isInstalled(pkg: String): Boolean =
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
    }

/** Deep links to the exact system screens. Every intent falls back to a broader screen. */
object PrereqIntents {
    fun listenerAccess(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, SlackNotificationListener::class.java).flattenToString(),
            )
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }

    fun listenerAccessFallback(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun appNotifications(context: Context): Intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun alertChannel(context: Context): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, AlertNotifier.CHANNEL_ID)

    fun fullScreen(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${context.packageName}".toUri())
        } else {
            appNotifications(context)
        }

    /** The list screen — not the REQUEST_IGNORE_BATTERY_OPTIMIZATIONS dialog (no such permission is declared). */
    fun batteryOptimization(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun slackNotifications(): Intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, SLACK_PACKAGE)

    fun appDetails(context: Context): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
}
