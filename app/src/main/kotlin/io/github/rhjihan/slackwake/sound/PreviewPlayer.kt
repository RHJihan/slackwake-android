// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.sound

import android.content.Context
import android.media.MediaPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Port of the Windows PreviewPlayer: play / stop / which-one-is-playing, one fresh
 * MediaPlayer per play so stop is immediate and stale completion callbacks are ignored.
 * Uses the alarm stream so the preview is as loud as the real alert will be.
 */
@Singleton
class PreviewPlayer
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val resolver: SoundResolver,
    ) {
        private var player: MediaPlayer? = null
        private val _playing = MutableStateFlow<String?>(null)

        /** Stored URI currently previewing ("" = default), or null when silent. */
        val playing: StateFlow<String?> = _playing.asStateFlow()

        fun play(stored: String) {
            stop()
            val p = MediaPlayer()
            player = p
            try {
                p.setAudioAttributes(AlarmSoundFactory.ALARM_ATTRIBUTES)
                p.setDataSource(context, resolver.resolve(stored))
                p.setOnPreparedListener { if (player === it) it.start() }
                p.setOnCompletionListener { if (player === it) stop() }
                p.setOnErrorListener { mp, _, _ ->
                    if (player === mp) stop()
                    true
                }
                p.prepareAsync()
                _playing.value = stored
            } catch (_: Exception) {
                stop()
            }
        }

        fun toggle(stored: String) = if (_playing.value == stored) stop() else play(stored)

        fun stop() {
            player?.let { runCatching { it.release() } }
            player = null
            _playing.value = null
        }
    }
