// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.rhjihan.slackwake.data.settings.SettingsRepository
import io.github.rhjihan.slackwake.vibration.AlertVibrationSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var vibrator: AlertVibrationSource

    @Before fun setUp() {
        hilt.inject()
        runBlocking {
            settings.update {
                it.copy(
                    onboardingCompleted = true,
                    enabled = true,
                    soundEnabled = true,
                    flashEnabled = false,
                    photosensitivityWarningDismissed = false,
                    keywordFilterEnabled = false,
                    keywordAllowEnabled = false,
                )
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("toggle_enabled")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun quickTogglePersistsImmediately() {
        compose.onNodeWithTag("toggle_sound").assertIsOn().performClick()
        compose.onNodeWithTag("toggle_sound").assertIsOff()
        compose.waitUntil(5_000) { runBlocking { !settings.current().soundEnabled } }
        assertFalse(runBlocking { settings.current().soundEnabled })
    }

    @Test fun enablingFlashAsksForConfirmation() {
        compose.onNodeWithTag("toggle_visual").performClick()
        compose.onNodeWithText("Photosensitivity warning").assertExists()
        compose.onNodeWithText("Turn on flash").performClick()
        compose.waitUntil(5_000) { runBlocking { settings.current().flashEnabled } }
        compose.onNodeWithTag("toggle_visual").assertIsOn()
    }

    @Test fun cancellingTheFlashWarningKeepsFlashOff() {
        compose.onNodeWithTag("toggle_visual").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("toggle_visual").assertIsOff()
        assertFalse(runBlocking { settings.current().flashEnabled })
    }

    @Test fun bothFiltersOnShowsTheInteractionNote() {
        compose.onNodeWithTag("tab_filters").performClick()
        runBlocking { settings.update { it.copy(keywordFilterEnabled = true, keywordAllowEnabled = true) } }
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("filter_note"))
        compose.onNodeWithText("Both filters are on").assertExists()
    }

    @Test fun statusIsPausedWhileTheUserIsLookingAndDisabledWhenOff() {
        // Viewing settings means the device is unlocked, so monitoring is Paused, not Armed.
        compose.onNodeWithContentDescription("Status: Paused.", substring = true).assertExists()
        compose.onNodeWithTag("toggle_enabled").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("Status: Disabled.", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(runBlocking { !settings.current().enabled })
    }

    @Test fun vibrationQuickTogglePersists() {
        runBlocking { settings.update { it.copy(vibrationEnabled = true) } }
        compose.onNodeWithTag("toggle_vibration").assertIsOn().performClick()
        compose.waitUntil(5_000) { runBlocking { !settings.current().vibrationEnabled } }
        compose.onNodeWithTag("toggle_vibration").assertIsOff()
    }

    @Test fun vibrationOffHidesItsOptions() {
        compose.onNodeWithTag("tab_alerts").performClick()
        runBlocking { settings.update { it.copy(vibrationEnabled = true) } }
        assumeTrue("needs a vibration motor", vibrator.hasVibrator)
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("vibration_mode_Fixed"))
        compose.onNodeWithTag("vibration_enabled").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("vibration_mode_Fixed").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Vibration pattern").assertDoesNotExist()
    }

    @Test fun gradualModeShowsTheBuildUpTime() {
        compose.onNodeWithTag("tab_alerts").performClick()
        assumeTrue("Gradual needs a motor with amplitude control", vibrator.hasAmplitudeControl)
        runBlocking { settings.update { it.copy(vibrationEnabled = true, vibrationMode = "Fixed") } }
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("vibration_mode_Gradual"))
        compose.onNodeWithTag("vibration_mode_Gradual").performClick()
        compose.waitUntil(5_000) { runBlocking { settings.current().vibrationMode == "Gradual" } }
        compose.onNodeWithText("Build-up time (seconds)").assertExists()
    }

    @Test fun turningFlashOffHidesItsOptions() {
        runBlocking { settings.update { it.copy(flashEnabled = true) } }
        compose.onNodeWithTag("tab_alerts").performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText("Flash colors"))
        compose.onNodeWithTag("tab_home").performClick()
        compose.onNodeWithTag("toggle_visual").performClick()
        compose.waitUntil(5_000) { runBlocking { !settings.current().flashEnabled } }
        compose.onNodeWithTag("tab_alerts").performClick()
        compose.onAllNodesWithText("Flash colors").assertCountEquals(0)
    }

    @Test fun dontShowAgainSkipsTheWarningNextTime() {
        compose.onNodeWithTag("toggle_visual").performClick()
        compose.onNodeWithTag("photosensitivity_dont_show").performClick()
        compose.onNodeWithText("Turn on flash").performClick()
        compose.waitUntil(5_000) { runBlocking { settings.current().photosensitivityWarningDismissed } }
        compose.onNodeWithTag("toggle_visual").performClick() // off
        compose.waitUntil(5_000) { runBlocking { !settings.current().flashEnabled } }
        compose.onNodeWithTag("toggle_visual").performClick() // on again: no dialog this time
        compose.waitUntil(5_000) { runBlocking { settings.current().flashEnabled } }
        compose.onNodeWithText("Photosensitivity warning").assertDoesNotExist()
    }

    @Test fun turningTheScheduleOnShowsTheWeek() {
        compose.onNodeWithTag("tab_timing").performClick()
        runBlocking { settings.update { it.copy(scheduleEnabled = false) } }
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("schedule_enabled"))
        compose.onNodeWithTag("schedule_enabled").performClick()
        compose.waitUntil(5_000) { runBlocking { settings.current().scheduleEnabled } }
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("schedule_summary"))
        compose.onNodeWithTag("schedule_day_MONDAY").assertExists()
    }

    @Test fun backFromAnotherTabReturnsHome() {
        compose.onNodeWithTag("tab_more").performClick()
        compose.onNodeWithText("Backup & restore").assertExists()
        pressBack()
        compose.onNodeWithTag("toggle_enabled").assertExists()
    }
}
