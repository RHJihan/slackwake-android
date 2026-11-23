// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.tile

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dagger.hilt.android.AndroidEntryPoint
import io.github.rhjihan.slackwake.R
import io.github.rhjihan.slackwake.core.status.MonitorState
import io.github.rhjihan.slackwake.core.status.MonitorStatus
import io.github.rhjihan.slackwake.data.settings.SettingsRepository
import io.github.rhjihan.slackwake.di.ApplicationScope
import io.github.rhjihan.slackwake.status.StatusRepository
import io.github.rhjihan.slackwake.ui.components.icon
import io.github.rhjihan.slackwake.ui.components.label
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The tray icon's Android counterpart: tap toggles monitoring; the subtitle shows
 * Armed / Paused / Disabled with the matching bell. Only listens while visible.
 * Note: the shade is usually pulled down by a present user, so it will typically read
 * "Paused" — Armed is the state while the phone sits locked and unattended.
 */
@AndroidEntryPoint
class MonitorTileService : TileService() {
    @Inject lateinit var status: StatusRepository

    @Inject lateinit var settings: SettingsRepository

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    private var job: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        status.refreshPrerequisites()
        job?.cancel()
        job = scope.launch { status.status.collect(::render) }
    }

    override fun onStopListening() {
        job?.cancel()
        job = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        scope.launch { settings.update { it.copy(enabled = !it.enabled) } }
    }

    private fun render(s: MonitorStatus) {
        val tile = qsTile ?: return
        val stateLabel = getString(s.state.label())
        tile.state = if (s.state == MonitorState.DISABLED) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.label = getString(R.string.tile_label)
        tile.icon = Icon.createWithResource(this, s.state.icon())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = stateLabel
        tile.contentDescription = "${getString(R.string.tile_label)}, $stateLabel"
        tile.updateTile()
    }
}
