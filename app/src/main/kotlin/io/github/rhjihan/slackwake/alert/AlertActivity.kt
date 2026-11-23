// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.alert

import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Display
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.getSystemService
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The fullscreen alert. Port of OverlayWindow: shows over the lock screen, turns the
 * screen on and keeps it on (DisplayWake), hides the system bars, and dismisses on a tap
 * anywhere, the back gesture, or any hardware key — volume keys and physical keyboards
 * included. The dismissing key is consumed (down AND up) so it never leaks into whatever
 * is underneath, mirroring the Windows keyboard hook. The power key cannot be
 * intercepted by apps; pressing it just turns the screen off and the alert keeps going.
 *
 * Additional presentation displays get a mirrored [Presentation]; sound plays once, from
 * the [AlertController], never per display.
 */
@AndroidEntryPoint
class AlertActivity : ComponentActivity() {
    @Inject lateinit var controller: AlertController

    private val presentations = mutableMapOf<Int, Presentation>()
    private val flashStart = MutableStateFlow<Long?>(null)
    private var dismissKeyDown = false

    private val displayListener =
        object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = syncPresentations()

            override fun onDisplayRemoved(displayId: Int) {
                presentations.remove(displayId)?.dismiss()
            }

            override fun onDisplayChanged(displayId: Int) = Unit
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        enterImmersive()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = dismiss("back")
            },
        )

        setContent { Content() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                launch {
                    controller.active.collect { if (it == null) finishAndRemoveTask() }
                }
                launch {
                    controller.flashing.collect { on -> flashStart.value = if (on) SystemClock.uptimeMillis() else null }
                }
                launch { controller.keepScreenOn.collect(::holdScreen) }
            }
        }

        getSystemService<DisplayManager>()?.registerDisplayListener(displayListener, null)
        syncPresentations()
    }

    @Composable
    private fun Content() {
        val alert by controller.active.collectAsStateWithLifecycle()
        val start by flashStart.collectAsStateWithLifecycle()
        val a = alert ?: return
        MaterialTheme {
            AlertScreen(
                event = a.event,
                colorA = a.plan.flashColorA,
                colorB = a.plan.flashColorB,
                halfCycleMs = a.plan.flashIntervalMs,
                flashStartMs = start,
                nowMs = SystemClock::uptimeMillis,
                onDismiss = { dismiss("tap") },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Launched by a stale notification after the alert already ended.
        if (controller.active.value == null) {
            finishAndRemoveTask()
        } else {
            controller.onAlertScreenShown()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    /**
     * Any key dismisses — volume, back, keyboard, D-pad. Consume the key-down and dismiss
     * on the matching key-up, so the pair is swallowed whole and nothing underneath sees
     * an orphan key-up. (The power key never reaches apps.)
     */
    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        dismissKeyDown = true
        return true
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (dismissKeyDown) dismiss("key ${KeyEvent.keyCodeToString(keyCode)}")
        return true
    }

    override fun onDestroy() {
        getSystemService<DisplayManager>()?.unregisterDisplayListener(displayListener)
        presentations.values.forEach { runCatching { it.dismiss() } }
        presentations.clear()
        super.onDestroy()
    }

    private fun dismiss(source: String) {
        controller.dismiss(source)
        finishAndRemoveTask()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
    }

    /**
     * Keep the screen on while the alert is active; once auto-stop has ended everything,
     * let it time out normally and stop forcing it on. The alert stays over the lock
     * screen, so waking the phone shows it again.
     */
    private fun holdScreen(hold: Boolean) {
        if (hold) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setTurnScreenOn(false)
            } else {
                @Suppress("DEPRECATION")
                window.clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            }
        }
    }

    private fun enterImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun syncPresentations() {
        val dm = getSystemService<DisplayManager>() ?: return
        for (display in dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)) {
            if (display.displayId == Display.DEFAULT_DISPLAY || display.displayId in presentations) continue
            val p = AlertPresentation(this, display, controller, flashStart) { dismiss("tap (external display)") }
            runCatching { p.show() }.onSuccess { presentations[display.displayId] = p }
        }
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, AlertActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}

/** Mirror of the alert on an additional presentation display (same text and flash, no sound). */
private class AlertPresentation(
    private val activity: AlertActivity,
    display: Display,
    private val controller: AlertController,
    private val flashStart: StateFlow<Long?>,
    private val onDismiss: () -> Unit,
) : Presentation(activity, display) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view =
            ComposeView(context).apply {
                setContent {
                    val alert by controller.active.collectAsStateWithLifecycle()
                    val start by flashStart.collectAsStateWithLifecycle()
                    val a = alert ?: return@setContent
                    MaterialTheme {
                        AlertScreen(
                            event = a.event,
                            colorA = a.plan.flashColorA,
                            colorB = a.plan.flashColorB,
                            halfCycleMs = a.plan.flashIntervalMs,
                            flashStartMs = start,
                            nowMs = SystemClock::uptimeMillis,
                            onDismiss = onDismiss,
                        )
                    }
                }
            }
        // A Presentation is a Dialog, so Compose needs the owners wired by hand.
        window?.decorView?.let {
            it.setViewTreeLifecycleOwner(activity)
            it.setViewTreeViewModelStoreOwner(activity)
            it.setViewTreeSavedStateRegistryOwner(activity)
        }
        setContentView(view)
        setOnKeyListener { _, _, event ->
            if (event.action == KeyEvent.ACTION_UP) onDismiss()
            true
        }
    }
}
