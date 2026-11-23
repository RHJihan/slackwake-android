// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.alert

import android.view.KeyEvent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.rhjihan.slackwake.core.model.SlackEvent
import io.github.rhjihan.slackwake.core.settings.AppSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/** Every dismissal path closes the alert and ends the alert state (sound, notification). */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AlertActivityDismissalTest {
    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var controller: AlertController

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    // Silent so the test run does not blare the alarm; flash on to exercise the animation.
    private val settings = AppSettings(soundEnabled = false, flashEnabled = true, soundDelaySeconds = 0)

    @Before fun setUp() {
        hilt.inject()
        instrumentation.runOnMainSync { controller.fire(SlackEvent.TEST, settings, isTest = true) }
    }

    @After fun tearDown() {
        instrumentation.runOnMainSync { controller.dismiss("test teardown") }
    }

    private fun launch(): ActivityScenario<AlertActivity> =
        ActivityScenario.launch<AlertActivity>(AlertActivity.intent(instrumentation.targetContext)).also {
            assertEquals(Lifecycle.State.RESUMED, it.state)
        }

    private fun assertDismissed(scenario: ActivityScenario<AlertActivity>) {
        instrumentation.waitForIdleSync()
        assertNull(controller.active.value)
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
    }

    @Test fun tapAnywhereDismisses() {
        val s = launch()
        onView(isRoot()).perform(click())
        assertDismissed(s)
    }

    @Test fun backDismisses() {
        val s = launch()
        pressBackUnconditionally()
        assertDismissed(s)
    }

    @Test fun volumeKeyDismisses() {
        val s = launch()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_VOLUME_DOWN)
        assertDismissed(s)
    }

    @Test fun physicalKeyboardKeyDismisses() {
        val s = launch()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_A)
        assertDismissed(s)
    }

    @Test fun escapeDismisses() {
        val s = launch()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ESCAPE)
        assertDismissed(s)
    }

    @Test fun notificationDismissClosesTheScreen() {
        val s = launch()
        instrumentation.runOnMainSync { controller.dismiss("notification") }
        assertDismissed(s)
    }
}
