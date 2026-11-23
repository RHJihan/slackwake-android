// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.alert

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rhjihan.slackwake.core.alert.SoundSink
import io.github.rhjihan.slackwake.core.alert.VibrationSink
import io.github.rhjihan.slackwake.core.model.SlackEvent
import io.github.rhjihan.slackwake.core.settings.AppSettings
import io.github.rhjihan.slackwake.core.vibration.VibrationMode
import io.github.rhjihan.slackwake.core.vibration.VibrationPattern
import io.github.rhjihan.slackwake.core.vibration.Waveform
import io.github.rhjihan.slackwake.data.diagnostics.DiagnosticLog
import io.github.rhjihan.slackwake.sound.AlertSoundSource
import io.github.rhjihan.slackwake.vibration.AlertVibrationSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Records plays; each play lasts until cancelled (a long clip). */
private class FakeSoundSource : AlertSoundSource {
    var plays = 0
    var cancelled = 0
    var focusHeld = false

    override fun create(storedUri: String) =
        object : SoundSink {
            override suspend fun playOnce() {
                plays++
                try {
                    awaitCancellation()
                } finally {
                    cancelled++
                }
            }
        }

    override fun acquireFocus() {
        focusHeld = true
    }

    override fun releaseFocus() {
        focusHeld = false
    }
}

/** Records vibration pulses; each lasts until cancelled. */
private class FakeVibrationSource : AlertVibrationSource {
    var plays = 0
    var cancels = 0
    override val hasVibrator = true
    override val hasAmplitudeControl = true

    override fun create() =
        object : VibrationSink {
            override suspend fun play(waveform: Waveform) {
                plays++
                awaitCancellation()
            }
        }

    override fun preview(
        pattern: VibrationPattern,
        intensityPercent: Int,
        mode: VibrationMode,
    ) = Unit

    override fun cancel() {
        cancels++
    }
}

/**
 * The user requirement behind this test: even when the fullscreen alert cannot be shown,
 * a Slack ping must still produce a notification AND the alert sound.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AlertControllerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val nm = context.getSystemService(NotificationManager::class.java)
    private lateinit var sound: FakeSoundSource
    private lateinit var vibration: FakeVibrationSource
    private val settings = AppSettings(soundDelaySeconds = 5, alertAutoStopEnabled = false)
    private val ping = SlackEvent("Alice", "#ops", "db down")

    @Before fun setUp() {
        sound = FakeSoundSource()
        vibration = FakeVibrationSource()
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun TestScope.controller(fullScreenAllowed: Boolean = true): AlertController {
        val log = DiagnosticLog(tmp.newFolder())
        val notifier =
            object : AlertNotifier(context, log) {
                override fun canUseFullScreenIntent() = fullScreenAllowed
            }
        return AlertController(notifier, sound, vibration, log, backgroundScope)
    }

    @Test fun `without full-screen permission it posts a heads-up with Dismiss and still plays the sound`() =
        runTest(StandardTestDispatcher()) {
            val c = controller(fullScreenAllowed = false)
            assertTrue(c.fire(ping, settings, isTest = false))

            val posted = shadowOf(nm).allNotifications.single()
            assertNull("no full-screen intent without permission", posted.fullScreenIntent)
            assertEquals(
                "Dismiss",
                posted.actions
                    .single()
                    .title
                    .toString(),
            )
            assertNotNull(posted.deleteIntent)

            advanceTimeBy(4_999)
            runCurrent()
            assertEquals("sound waits out the alert delay", 0, sound.plays)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(1, sound.plays)
            assertEquals("vibration starts with the sound", 1, vibration.plays)
            assertTrue(sound.focusHeld)

            // The Dismiss action and swipe-away both route to AlertActionReceiver...
            listOf(posted.actions.single().actionIntent, posted.deleteIntent).forEach {
                val target = shadowOf(it).savedIntent
                assertEquals(AlertActionReceiver::class.java.name, target.component?.className)
                assertEquals(AlertActionReceiver.ACTION_DISMISS, target.action)
            }
            // ...which calls dismiss: sound stops, notification cancelled, state cleared.
            c.dismiss("notification")
            runCurrent()
            assertEquals(1, sound.cancelled)
            assertTrue("motor stopped", vibration.cancels >= 1)
            assertFalse(sound.focusHeld)
            assertTrue(shadowOf(nm).allNotifications.isEmpty())
            assertNull(c.active.value)
        }

    @Test fun `with full-screen permission the notification carries a full-screen intent`() =
        runTest(StandardTestDispatcher()) {
            controller(fullScreenAllowed = true).fire(ping, settings, isTest = false)
            assertNotNull(shadowOf(nm).allNotifications.single().fullScreenIntent)
        }

    @Test fun `with notifications blocked the sound still plays`() =
        runTest(StandardTestDispatcher()) {
            shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
            val c = controller()
            assertTrue(c.fire(ping, settings.copy(soundDelaySeconds = 0), isTest = false))
            runCurrent()
            assertTrue(shadowOf(nm).allNotifications.isEmpty())
            assertEquals(1, sound.plays)
            assertEquals(1, vibration.plays)
            c.dismiss("test")
            runCurrent()
            assertEquals(1, sound.cancelled)
        }

    @Test fun `a second alert while one is showing is dropped`() =
        runTest(StandardTestDispatcher()) {
            val c = controller()
            assertTrue(c.fire(ping, settings, isTest = false))
            assertFalse(c.fire(ping.copy(text = "another"), settings, isTest = false))
            assertEquals(
                "db down",
                c.active.value
                    ?.event
                    ?.text,
            )
        }

    @Test fun `dismiss with nothing active is a no-op`() =
        runTest(StandardTestDispatcher()) {
            controller().dismiss("nothing")
            assertTrue(shadowOf(nm).allNotifications.isEmpty())
        }

    @Test fun `after auto-stop ends everything the screen is released but the alert and notification stay`() =
        runTest(StandardTestDispatcher()) {
            val c = controller(fullScreenAllowed = false)
            c.fire(ping, AppSettings(soundDelaySeconds = 5, alertAutoStopEnabled = true, alertMaxDurationSeconds = 60), isTest = false)
            runCurrent()
            assertTrue(c.keepScreenOn.value)
            advanceTimeBy(64_999)
            runCurrent()
            assertTrue("held until delay + cap", c.keepScreenOn.value)
            advanceTimeBy(1)
            runCurrent()
            assertFalse(c.keepScreenOn.value)
            assertNotNull("alert stays until dismissed", c.active.value)
            assertEquals("notification stays as a record", 1, shadowOf(nm).allNotifications.size)
        }

    @Test fun `screen stays held while the flash runs until dismissed`() =
        runTest(StandardTestDispatcher()) {
            val c = controller()
            val s = AppSettings(soundDelaySeconds = 0, alertAutoStopEnabled = true, alertMaxDurationSeconds = 10, alertAutoStopIncludesVisual = false)
            c.fire(ping, s, isTest = false)
            advanceTimeBy(600_000)
            runCurrent()
            assertTrue(c.keepScreenOn.value)
        }

    @Test fun `dismiss releases the screen`() =
        runTest(StandardTestDispatcher()) {
            val c = controller()
            c.fire(ping, AppSettings(alertAutoStopEnabled = false), isTest = false)
            runCurrent()
            assertTrue(c.keepScreenOn.value)
            c.dismiss("tap")
            assertFalse(c.keepScreenOn.value)
        }

    @Test fun `sound and vibration left running do not keep the screen on`() =
        runTest(StandardTestDispatcher()) {
            val c = controller()
            val s =
                AppSettings(
                    soundDelaySeconds = 0,
                    alertAutoStopEnabled = true,
                    alertMaxDurationSeconds = 10,
                    alertAutoStopIncludesSound = false,
                    alertAutoStopIncludesVibration = false,
                    alertAutoStopIncludesVisual = true,
                )
            c.fire(ping, s, isTest = false)
            advanceTimeBy(10_001)
            runCurrent()
            assertFalse("released once the flash stopped", c.keepScreenOn.value)
            assertTrue("sound still playing", sound.plays >= 1 && sound.cancelled == 0)
            assertNotNull(c.active.value)
        }

    @Test fun `test alert notification is quiet from the start`() =
        runTest(StandardTestDispatcher()) {
            controller(fullScreenAllowed = true).fire(ping, settings, isTest = true)
            val n = shadowOf(nm).allNotifications.single()
            assertEquals(AlertNotifier.ACTIVE_CHANNEL_ID, n.channelId)
            assertNull("no full-screen intent: the screen is opened directly", n.fullScreenIntent)
        }

    @Test fun `real alert moves to the quiet channel once the alert screen shows`() =
        runTest(StandardTestDispatcher()) {
            val c = controller(fullScreenAllowed = true)
            c.fire(ping, settings, isTest = false)
            assertEquals(AlertNotifier.CHANNEL_ID, shadowOf(nm).allNotifications.single().channelId)
            c.onAlertScreenShown()
            val n = shadowOf(nm).allNotifications.single()
            assertEquals("same notification, now quiet", AlertNotifier.ACTIVE_CHANNEL_ID, n.channelId)
            assertEquals(
                "Dismiss",
                n.actions
                    .single()
                    .title
                    .toString(),
            )
        }

    @Test fun `heads-up fallback stays on the high channel until the screen is opened`() =
        runTest(StandardTestDispatcher()) {
            controller(fullScreenAllowed = false).fire(ping, settings, isTest = false)
            assertEquals(AlertNotifier.CHANNEL_ID, shadowOf(nm).allNotifications.single().channelId)
        }

    @Test fun `quiet channel exists and never pops up`() =
        runTest(StandardTestDispatcher()) {
            controller().fire(ping, settings, isTest = true)
            val ch = nm.getNotificationChannel(AlertNotifier.ACTIVE_CHANNEL_ID)
            assertTrue(ch.importance < NotificationManager.IMPORTANCE_HIGH)
            assertNull(ch.sound)
        }
}
