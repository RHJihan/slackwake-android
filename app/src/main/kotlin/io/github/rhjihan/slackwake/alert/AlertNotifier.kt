// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.alert

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.rhjihan.slackwake.R
import io.github.rhjihan.slackwake.data.diagnostics.DiagnosticLog
import javax.inject.Inject
import javax.inject.Singleton

/** How an alert reached the user, for the log and the status line. */
enum class AlertRoute {
    /** Full-screen intent: AlertActivity launches over the lock screen. */
    FULL_SCREEN,

    /** Full-screen intent not allowed (Android 14+): heads-up notification only. */
    HEADS_UP,

    /** Notifications blocked: sound only. Dismiss by opening SlackWake. */
    SOUND_ONLY,

    /** Test alert: the screen is opened directly, so the notification is quiet from the start. */
    SCREEN_DIRECT,
}

/** Open only so tests can pin [canUseFullScreenIntent] (Robolectric has no shadow for it). */
@Singleton
open class AlertNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val log: DiagnosticLog,
    ) {
        private val manager = NotificationManagerCompat.from(context)

        /**
         * The Alerts channel is deliberately silent with no vibration: SlackWake plays its
         * own looping, cappable alarm sound. CATEGORY_ALARM + high importance is what lets
         * the full-screen intent fire and the heads-up peek.
         */
        fun ensureChannel() {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.channel_alerts_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.channel_alerts_description)
                    setSound(null, null)
                    enableVibration(false)
                    enableLights(false)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                }
            // Quiet channel used while the alert screen itself is showing: the overlay is the
            // alert, so the notification is only a shade entry (no pop-up, no sound).
            val active =
                NotificationChannel(
                    ACTIVE_CHANNEL_ID,
                    context.getString(R.string.channel_active_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.channel_active_description)
                    setSound(null, null)
                    enableVibration(false)
                    enableLights(false)
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannels(listOf(channel, active))
        }

        fun canPostNotifications(): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            return manager.areNotificationsEnabled()
        }

        fun isChannelBlocked(): Boolean = manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE

        /** Android 14+ gates full-screen intents behind a user-grantable permission. */
        open fun canUseFullScreenIntent(): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true
            } else {
                true
            }

        /**
         * Post the alert notification.
         *
         * [quiet]: the alert screen is being opened directly (Test alert), so post on the
         * quiet "Active alert" channel from the start: shade entry, no pop-up over the overlay.
         * Otherwise post on the high-importance channel, which is what lets the full-screen
         * intent launch the alert screen (or, without that permission, makes the heads-up
         * pop-up the alert itself). Once the screen is showing, [showQuietly] demotes it.
         */
        fun post(
            alert: ActiveAlert,
            quiet: Boolean = false,
        ): AlertRoute {
            ensureChannel()
            if (!canPostNotifications() || isChannelBlocked()) {
                log.write("Notifications blocked; alert is sound-only")
                return AlertRoute.SOUND_ONLY
            }
            if (quiet) {
                notify(build(alert, ACTIVE_CHANNEL_ID, fullScreen = false))
                return AlertRoute.SCREEN_DIRECT
            }
            val fullScreen = canUseFullScreenIntent()
            notify(build(alert, CHANNEL_ID, fullScreen))
            return if (fullScreen) AlertRoute.FULL_SCREEN else AlertRoute.HEADS_UP
        }

        /**
         * The alert screen is visible: re-post the same notification on the quiet channel.
         * Replacing it removes any heads-up pop-up still covering the overlay, while the
         * shade keeps the entry (with Dismiss) as a record.
         */
        fun showQuietly(alert: ActiveAlert) {
            if (!canPostNotifications() || isChannelBlocked()) return
            notify(build(alert, ACTIVE_CHANNEL_ID, fullScreen = false))
        }

        private fun build(
            alert: ActiveAlert,
            channelId: String,
            fullScreen: Boolean,
        ): Notification {
            val event = alert.event
            val title = context.getString(R.string.alert_title)
            val body =
                listOfNotNull(event.channel, event.sender).joinToString(" · ").ifEmpty { null }
                    ?: context.getString(R.string.alert_no_preview)

            val publicVersion =
                NotificationCompat
                    .Builder(context, channelId)
                    .setSmallIcon(R.drawable.ic_stat_bell)
                    .setContentTitle(title)
                    .build()

            val builder =
                NotificationCompat
                    .Builder(context, channelId)
                    .setSmallIcon(R.drawable.ic_stat_bell)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull(body, event.text).joinToString("\n")))
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setPriority(if (channelId == CHANNEL_ID) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_LOW)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(publicVersion)
                    .setOnlyAlertOnce(true)
                    .setSilent(channelId != CHANNEL_ID)
                    .setAutoCancel(false)
                    .setContentIntent(activityIntent())
                    .setDeleteIntent(dismissIntent(REQUEST_DELETE))
                    .addAction(0, context.getString(R.string.alert_action_dismiss), dismissIntent(REQUEST_DISMISS))
            if (fullScreen) builder.setFullScreenIntent(activityIntent(), true)
            return builder.build()
        }

        @Suppress("MissingPermission") // callers check canPostNotifications() first
        private fun notify(notification: Notification) = manager.notify(NOTIFICATION_ID, notification)

        fun cancel() = manager.cancel(NOTIFICATION_ID)

        private fun activityIntent(): PendingIntent =
            PendingIntent.getActivity(
                context,
                REQUEST_ACTIVITY,
                AlertActivity.intent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun dismissIntent(requestCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, AlertActionReceiver::class.java).setAction(AlertActionReceiver.ACTION_DISMISS),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        companion object {
            const val CHANNEL_ID = "alerts"

            /** Quiet channel while the alert screen is visible (no heads-up over the overlay). */
            const val ACTIVE_CHANNEL_ID = "alert_active"
            const val NOTIFICATION_ID = 1
            private const val REQUEST_ACTIVITY = 1
            private const val REQUEST_DISMISS = 2
            private const val REQUEST_DELETE = 3
        }
    }
