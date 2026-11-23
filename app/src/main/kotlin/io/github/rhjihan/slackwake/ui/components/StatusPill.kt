// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import io.github.rhjihan.slackwake.R
import io.github.rhjihan.slackwake.core.status.MonitorState
import io.github.rhjihan.slackwake.core.status.MonitorStatus
import io.github.rhjihan.slackwake.core.status.Problem
import io.github.rhjihan.slackwake.core.status.StatusDetail
import io.github.rhjihan.slackwake.ui.theme.StatusColors

fun MonitorState.label(): Int =
    when (this) {
        MonitorState.ARMED -> R.string.state_armed
        MonitorState.PAUSED -> R.string.state_paused
        MonitorState.DISABLED -> R.string.state_disabled
    }

fun MonitorState.icon(): Int =
    when (this) {
        MonitorState.ARMED -> R.drawable.ic_bell_armed
        MonitorState.PAUSED -> R.drawable.ic_bell_paused
        MonitorState.DISABLED -> R.drawable.ic_bell_disabled
    }

fun MonitorState.color(): Color =
    when (this) {
        MonitorState.ARMED -> StatusColors.Armed
        MonitorState.PAUSED -> StatusColors.Paused
        MonitorState.DISABLED -> StatusColors.Disabled
    }

fun StatusDetail.text(): Int =
    when (this) {
        StatusDetail.DISABLED -> R.string.status_disabled
        StatusDetail.PAUSED_PRESENT -> R.string.status_paused
        StatusDetail.PAUSED_OFF_SCHEDULE -> R.string.status_off_schedule
        StatusDetail.ARMED_ALL -> R.string.status_armed_all
        StatusDetail.ARMED_KEYWORDS_ONLY -> R.string.status_armed_keywords
    }

fun Problem.text(): Int =
    when (this) {
        Problem.LISTENER_ACCESS_MISSING -> R.string.problem_listener_missing
        Problem.LISTENER_DISCONNECTED -> R.string.problem_listener_disconnected
        Problem.NOTIFICATIONS_BLOCKED -> R.string.problem_notifications_blocked
        Problem.ALERT_CHANNEL_BLOCKED -> R.string.problem_channel_blocked
        Problem.FULL_SCREEN_DENIED -> R.string.problem_full_screen_denied
    }

/** The Windows status pill: colored bell + state word + explanatory sentence (+ problem). */
@Composable
fun StatusPill(
    status: MonitorStatus,
    modifier: Modifier = Modifier,
) {
    val state = stringResource(status.state.label())
    val detail = stringResource(status.detail.text())
    val problem = status.problem?.let { stringResource(it.text()) }
    val a11y = stringResource(R.string.status_a11y, state, listOfNotNull(detail, problem).joinToString(" "))
    Card(
        modifier =
            modifier.fillMaxWidth().clearAndSetSemantics {
                contentDescription = a11y
                liveRegion = LiveRegionMode.Polite
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(
                painterResource(status.state.icon()),
                contentDescription = null,
                tint = status.state.color(),
                modifier = Modifier.size(32.dp),
            )
            Column {
                Text(state, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (problem != null) {
                    Text(problem, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
