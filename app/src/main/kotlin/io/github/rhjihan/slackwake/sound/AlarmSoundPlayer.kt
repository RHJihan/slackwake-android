// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.rhjihan.slackwake.core.alert.SoundSink
import io.github.rhjihan.slackwake.data.diagnostics.DiagnosticLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Builds the [SoundSink] for one alert, plus transient audio focus around it. */
interface AlertSoundSource {
    fun create(storedUri: String): SoundSink

    fun acquireFocus()

    fun releaseFocus()
}

@Singleton
class AlarmSoundFactory
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val resolver: SoundResolver,
        private val log: DiagnosticLog,
    ) : AlertSoundSource {
        private val audioManager = context.getSystemService<AudioManager>()
        private var focusRequest: AudioFocusRequest? = null

        override fun create(storedUri: String): SoundSink = AlarmSoundSink(context, resolver.resolve(storedUri), resolver.defaultAlarmUri(), log)

        /** Transient focus so music/podcasts duck or pause while the alert sounds. */
        override fun acquireFocus() {
            if (focusRequest != null) return
            val request =
                AudioFocusRequest
                    .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(ALARM_ATTRIBUTES)
                    .setOnAudioFocusChangeListener { }
                    .build()
            audioManager?.requestAudioFocus(request)
            focusRequest = request
        }

        override fun releaseFocus() {
            focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            focusRequest = null
        }

        companion object {
            val ALARM_ATTRIBUTES: AudioAttributes =
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
        }
    }

/**
 * One MediaPlayer per play (as Windows did), so "stop now" really means now and late
 * callbacks from a torn-down player are harmless. Plays on the ALARM stream, so it is
 * governed by the alarm volume rather than media/ringer volume.
 */
private class AlarmSoundSink(
    private val context: Context,
    private var uri: Uri,
    private val fallback: Uri,
    private val log: DiagnosticLog,
) : SoundSink {
    override suspend fun playOnce() {
        val ok = playUri(uri)
        if (ok) return
        if (uri != fallback) {
            log.write("Alert sound failed to play; switching to the default alarm sound")
            uri = fallback
            return
        }
        // Even the default failed (no audio device?) — back off so a loop does not spin.
        delay(FAILURE_BACKOFF_MS)
    }

    private suspend fun playUri(target: Uri): Boolean =
        suspendCancellableCoroutine { cont ->
            val player = MediaPlayer()

            fun finish(result: Boolean) {
                runCatching { player.release() }
                if (cont.isActive) cont.resume(result)
            }
            cont.invokeOnCancellation { runCatching { player.release() } }
            try {
                player.setAudioAttributes(AlarmSoundFactory.ALARM_ATTRIBUTES)
                player.setDataSource(context, target)
                player.setOnPreparedListener { it.start() }
                player.setOnCompletionListener { finish(true) }
                player.setOnErrorListener { _, what, extra ->
                    log.write("MediaPlayer error what=$what extra=$extra")
                    finish(false)
                    true
                }
                player.prepareAsync()
            } catch (e: Exception) {
                log.write("MediaPlayer setup failed: ${e.javaClass.simpleName}")
                finish(false)
            }
        }

    companion object {
        const val FAILURE_BACKOFF_MS = 2_000L
    }
}
