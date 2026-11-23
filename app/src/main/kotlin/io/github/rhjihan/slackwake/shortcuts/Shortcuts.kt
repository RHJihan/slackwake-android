// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.shortcuts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.rhjihan.slackwake.R
import io.github.rhjihan.slackwake.core.settings.AppSettings
import io.github.rhjihan.slackwake.data.settings.SettingsRepository
import io.github.rhjihan.slackwake.ui.components.PhotosensitivityDialog
import io.github.rhjihan.slackwake.ui.theme.SlackWakeTheme
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** The four tray-menu toggles, as long-press launcher shortcuts. */
enum class QuickToggle(
    val id: String,
    val nameRes: Int,
    val onRes: Int,
    val offRes: Int,
    val icon: Int,
) {
    MONITORING(
        "toggle_enabled",
        R.string.toggle_enabled,
        R.string.shortcut_monitoring_on,
        R.string.shortcut_monitoring_off,
        R.drawable.ic_bell_armed,
    ),
    SOUND("toggle_sound", R.string.toggle_sound, R.string.shortcut_sound_on, R.string.shortcut_sound_off, R.drawable.ic_bell_paused),
    VISUAL("toggle_visual", R.string.toggle_visual, R.string.shortcut_visual_on, R.string.shortcut_visual_off, R.drawable.ic_bell_paused),
    AUTO_STOP(
        "toggle_autostop",
        R.string.toggle_autostop,
        R.string.shortcut_autostop_on,
        R.string.shortcut_autostop_off,
        R.drawable.ic_bell_disabled,
    ),
    ;

    fun isOn(s: AppSettings): Boolean =
        when (this) {
            MONITORING -> s.enabled
            SOUND -> s.soundEnabled
            VISUAL -> s.flashEnabled
            AUTO_STOP -> s.alertAutoStopEnabled
        }

    fun set(
        s: AppSettings,
        on: Boolean,
    ): AppSettings =
        when (this) {
            MONITORING -> s.copy(enabled = on)
            SOUND -> s.copy(soundEnabled = on)
            VISUAL -> s.copy(flashEnabled = on)
            AUTO_STOP -> s.copy(alertAutoStopEnabled = on)
        }

    companion object {
        fun byId(id: String?) = entries.firstOrNull { it.id == id }
    }
}

/** Keeps the dynamic shortcuts' labels ("Turn sound alert off") in sync with settings. */
@Singleton
class ShortcutPublisher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun publish(s: AppSettings) {
            val shortcuts =
                QuickToggle.entries.mapIndexed { rank, t ->
                    val label = context.getString(if (t.isOn(s)) t.offRes else t.onRes)
                    ShortcutInfoCompat
                        .Builder(context, t.id)
                        .setShortLabel(label)
                        .setLongLabel(label)
                        .setRank(rank)
                        .setIcon(IconCompat.createWithResource(context, t.icon))
                        .setIntent(
                            Intent(context, ShortcutActionActivity::class.java)
                                .setAction(Intent.ACTION_VIEW)
                                .putExtra(ShortcutActionActivity.EXTRA_TOGGLE, t.id),
                        ).build()
                }
            runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
        }
    }

/**
 * Invisible trampoline: flips one setting and closes. Turning the flash ON still shows
 * the photosensitivity warning first, as everywhere else.
 */
@AndroidEntryPoint
class ShortcutActionActivity : ComponentActivity() {
    @Inject lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val toggle =
            QuickToggle.byId(intent.getStringExtra(EXTRA_TOGGLE)) ?: run {
                finish()
                return
            }
        // Lets the launcher rank the shortcuts by use.
        ShortcutManagerCompat.reportShortcutUsed(this, toggle.id)
        lifecycleScope.launch {
            val turnOn = !toggle.isOn(settings.current())
            if (toggle == QuickToggle.VISUAL && turnOn && !settings.current().photosensitivityWarningDismissed) {
                setContent {
                    SlackWakeTheme {
                        PhotosensitivityDialog(
                            onConfirm = { dontShowAgain -> apply(toggle, true, dontShowAgain) },
                            onDismiss = ::finish,
                        )
                    }
                }
            } else {
                apply(toggle, turnOn)
            }
        }
    }

    private fun apply(
        toggle: QuickToggle,
        on: Boolean,
        dismissWarning: Boolean = false,
    ) {
        lifecycleScope.launch {
            settings.update {
                toggle.set(it, on).copy(photosensitivityWarningDismissed = it.photosensitivityWarningDismissed || dismissWarning)
            }
            val msg = getString(R.string.shortcut_done, getString(toggle.nameRes), getString(if (on) R.string.on else R.string.off))
            Toast.makeText(this@ShortcutActionActivity, msg, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    companion object {
        const val EXTRA_TOGGLE = "toggle"
    }
}
