// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.ui.components

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.rhjihan.slackwake.R
import io.github.rhjihan.slackwake.prereq.PrereqIntents
import io.github.rhjihan.slackwake.prereq.PrereqState
import io.github.rhjihan.slackwake.ui.theme.StatusColors

private fun Context.launch(
    primary: Intent,
    fallback: Intent? = null,
) {
    try {
        startActivity(primary)
    } catch (_: ActivityNotFoundException) {
        fallback?.let { runCatching { startActivity(it) } }
    }
}

/**
 * Every prerequisite with its state and a deep link to the right system screen. Used by
 * onboarding (all rows) and the settings screen (always visible; flags what's missing).
 */
@Composable
fun PrerequisiteList(
    prereqs: PrereqState,
    slackConfirmed: Boolean,
    onSlackConfirmedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onlyMissing: Boolean = false,
) {
    val context = LocalContext.current
    // POST_NOTIFICATIONS: ask in-app first; once Android stops showing the prompt the
    // result stays "denied", so fall through to the app's notification settings.
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) context.launch(PrereqIntents.appNotifications(context), PrereqIntents.appDetails(context))
        }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!onlyMissing || !prereqs.listenerAccess) {
            PrereqRow(
                title = stringResource(R.string.prereq_listener),
                body = stringResource(R.string.prereq_listener_body),
                done = prereqs.listenerAccess,
                action = stringResource(R.string.prereq_open),
            ) { context.launch(PrereqIntents.listenerAccess(context), PrereqIntents.listenerAccessFallback()) }
        }

        if (!onlyMissing || !(prereqs.notificationsAllowed && prereqs.alertChannelOn)) {
            PrereqRow(
                title = stringResource(R.string.prereq_notifications),
                body = stringResource(R.string.prereq_notifications_body),
                done = prereqs.notificationsAllowed && prereqs.alertChannelOn,
                action = stringResource(if (prereqs.notificationsAllowed) R.string.prereq_open else R.string.prereq_allow),
            ) {
                when {
                    !prereqs.notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }

                    !prereqs.notificationsAllowed -> {
                        context.launch(PrereqIntents.appNotifications(context), PrereqIntents.appDetails(context))
                    }

                    else -> {
                        context.launch(PrereqIntents.alertChannel(context), PrereqIntents.appNotifications(context))
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && (!onlyMissing || !prereqs.fullScreenAllowed)) {
            PrereqRow(
                title = stringResource(R.string.prereq_full_screen),
                body = stringResource(R.string.prereq_full_screen_body),
                done = prereqs.fullScreenAllowed,
                action = stringResource(R.string.prereq_open),
            ) { context.launch(PrereqIntents.fullScreen(context), PrereqIntents.appNotifications(context)) }
        }

        // Optional guidance: not nagged about on Home (it lives under More there).
        if (!onlyMissing) BatteryTip(prereqs)

        if (!onlyMissing || !slackConfirmed) {
            PrereqRow(
                title = stringResource(R.string.prereq_slack),
                body =
                    stringResource(R.string.prereq_slack_body) +
                        if (prereqs.slackInstalled) "" else "\n" + stringResource(R.string.prereq_slack_missing),
                // Android can't read Slack's notification settings: the user ticks this off.
                done = slackConfirmed,
                action = if (prereqs.slackInstalled) stringResource(R.string.prereq_open_slack) else null,
                confirm = if (prereqs.slackInstalled) stringResource(R.string.prereq_slack_confirm) else null,
                onConfirm = { onSlackConfirmedChange(true) },
                onUndo = { onSlackConfirmedChange(false) },
                tag = "prereq_slack",
            ) { context.launch(PrereqIntents.slackNotifications()) }
        }
    }
}

/** True while any required setup step (or the Slack tick) is still open. */
fun setupIncomplete(
    prereqs: PrereqState,
    slackConfirmed: Boolean,
): Boolean = prereqs.missingCount > 0 || !slackConfirmed

/** Optional battery-optimization guidance with its current state and a deep link. */
@Composable
fun BatteryTip(prereqs: PrereqState) {
    val context = LocalContext.current
    PrereqRow(
        title = stringResource(R.string.prereq_battery),
        body = stringResource(R.string.prereq_battery_body),
        done = prereqs.ignoringBatteryOptimizations,
        optional = true,
        action = stringResource(R.string.prereq_open),
    ) { context.launch(PrereqIntents.batteryOptimization(), PrereqIntents.appDetails(context)) }
}

/** [done] null = guidance only (no pass/fail). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrereqRow(
    title: String,
    body: String,
    done: Boolean?,
    action: String?,
    optional: Boolean = false,
    confirm: String? = null,
    onConfirm: () -> Unit = {},
    onUndo: (() -> Unit)? = null,
    tag: String? = null,
    onAction: () -> Unit,
) {
    val (icon: ImageVector, tint) =
        when (done) {
            true -> Icons.Filled.CheckCircle to StatusColors.Armed
            false -> Icons.Filled.ErrorOutline to if (optional) StatusColors.Paused else MaterialTheme.colorScheme.error
            null -> Icons.Outlined.Info to MaterialTheme.colorScheme.primary
        }
    Card(Modifier.fillMaxWidth().then(if (tag != null) Modifier.testTag(tag) else Modifier)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                Text(
                    title + if (optional) " · " + stringResource(R.string.prereq_optional) else "",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 12.dp).weight(1f).semantics { heading() },
                )
                if (done ==
                    true
                ) {
                    Text(
                        stringResource(R.string.prereq_granted),
                        color = StatusColors.Armed,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (done != true && (action != null || confirm != null)) {
                // Buttons move to the next line as a whole when they don't fit; a label never wraps.
                FlowRow(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (action != null) {
                        if (done == false && !optional && confirm == null) {
                            FilledTonalButton(onClick = onAction) { Text(action, maxLines = 1) }
                        } else {
                            OutlinedButton(onClick = onAction) { Text(action, maxLines = 1) }
                        }
                    }
                    if (confirm != null) FilledTonalButton(onClick = onConfirm) { Text(confirm, maxLines = 1) }
                }
            }
            if (done == true && onUndo != null) {
                TextButton(onClick = onUndo, modifier = Modifier.padding(top = 4.dp)) { Text(stringResource(R.string.prereq_undo)) }
            }
        }
    }
}
