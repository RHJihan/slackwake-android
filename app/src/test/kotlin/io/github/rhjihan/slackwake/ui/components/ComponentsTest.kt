// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rhjihan.slackwake.core.status.MonitorState
import io.github.rhjihan.slackwake.core.status.MonitorStatus
import io.github.rhjihan.slackwake.core.status.Problem
import io.github.rhjihan.slackwake.core.status.StatusDetail
import io.github.rhjihan.slackwake.prereq.PrereqState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `numeric field clamps to the upper bound on Done`() {
        var stored by mutableIntStateOf(300)
        compose.setContent {
            MaterialTheme { SliderField("Away timeout", stored, { stored = it }, clamp = 10..7200, sliderRange = 10..1800) }
        }
        val field =
            compose.onNode(hasSetTextAction())
        field.performTextReplacement("99999")
        field.performImeAction()
        compose.runOnIdle { assertEquals(7200, stored) }
        field.assertTextEquals("7200")
    }

    @Test fun `numeric field clamps to the lower bound and rejects non-digits`() {
        var stored by mutableIntStateOf(500)
        compose.setContent { MaterialTheme { SliderField("Flash speed", stored, { stored = it }, clamp = 200..2000) } }
        val field =
            compose.onNode(hasSetTextAction())
        field.performTextReplacement("5x")
        field.assertTextEquals("500") // non-digit input ignored
        field.performTextReplacement("50")
        field.performImeAction()
        compose.runOnIdle { assertEquals(200, stored) }
    }

    @Test fun `empty numeric field restores the stored value`() {
        var stored by mutableIntStateOf(5)
        compose.setContent { MaterialTheme { SliderField("Alert delay", stored, { stored = it }, clamp = 0..120) } }
        val field =
            compose.onNode(hasSetTextAction())
        field.performTextReplacement("")
        field.performImeAction()
        field.assertTextEquals("5")
        compose.runOnIdle { assertEquals(5, stored) }
    }

    @Test fun `switch row toggles from anywhere on the row`() {
        var on by mutableStateOf(false)
        compose.setContent { MaterialTheme { SwitchRow("Sound alert", on, { on = it }, caption = "Play a sound") } }
        compose.onNodeWithText("Sound alert").assertIsOff()
        compose.onNodeWithText("Play a sound").performClick()
        compose.onNodeWithText("Sound alert").assertIsOn()
    }

    @Test fun `status pill announces state, sentence and problem`() {
        compose.setContent {
            MaterialTheme {
                StatusPill(MonitorStatus(MonitorState.ARMED, StatusDetail.ARMED_KEYWORDS_ONLY, Problem.FULL_SCREEN_DENIED))
            }
        }
        compose
            .onNodeWithContentDescription("armed only for pings matching your keywords", substring = true)
            .assertExists()
        compose.onNodeWithContentDescription("Status: Armed.", substring = true).assertExists()
        compose.onNodeWithContentDescription("heads-up notification", substring = true).assertExists()
    }

    @Test fun `supporting text and slider follow typing before it is saved`() {
        var stored by mutableIntStateOf(600)
        compose.setContent {
            MaterialTheme {
                SliderField("Max", stored, { stored = it }, clamp = 5..3600, supporting = { androidx.compose.material3.Text("live=$it") })
            }
        }
        compose.onNodeWithText("live=600").assertExists()
        compose.onNode(hasSetTextAction()).performTextReplacement("375")
        compose.onNodeWithText("live=375").assertExists()
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(375f, 5f..3600f))).assertExists()
        compose.runOnIdle { assertEquals("not persisted until Done", 600, stored) }
        compose.onNode(hasSetTextAction()).performTextReplacement("99999")
        compose.onNodeWithText("live=3600").assertExists() // live value is clamped too
    }

    @Test fun `slack row is ticked off by the user and can be undone`() {
        var confirmed by mutableStateOf(false)
        compose.setContent {
            // Scrollable like the real screens: the Slack card is last and below the fold.
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PrerequisiteList(PrereqState.ALL_GRANTED, confirmed, { confirmed = it })
                }
            }
        }
        compose.onNodeWithText("I've turned this on").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(true, confirmed) }
        compose.onNodeWithText("Undo").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(false, confirmed) }
        compose.onNodeWithText("Open Slack settings").performScrollTo().assertExists()
    }

    @Test fun `photosensitivity dialog reports the dont-show-again choice only on confirm`() {
        var result: Boolean? = null
        var dismissed = false
        compose.setContent {
            MaterialTheme { PhotosensitivityDialog(onConfirm = { result = it }, onDismiss = { dismissed = true }) }
        }
        compose.onNodeWithTag("photosensitivity_dont_show").performClick().assertIsOn()
        compose.onNodeWithText("Turn on flash").performClick()
        compose.runOnIdle { assertEquals(true, result) }
        assertEquals(false, dismissed)
    }

    @Test fun `photosensitivity dialog defaults to showing again`() {
        var result: Boolean? = null
        compose.setContent { MaterialTheme { PhotosensitivityDialog(onConfirm = { result = it }, onDismiss = {}) } }
        compose.onNodeWithTag("photosensitivity_dont_show").assertIsOff()
        compose.onNodeWithText("Turn on flash").performClick()
        compose.runOnIdle { assertEquals(false, result) }
    }

    @Test fun `home shows only the setup steps that are still open`() {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PrerequisiteList(PrereqState.ALL_GRANTED.copy(listenerAccess = false), true, {}, onlyMissing = true)
                }
            }
        }
        compose.onNodeWithText("Notification access").assertExists()
        compose.onNodeWithText("Show notifications").assertDoesNotExist()
        compose.onNodeWithText("Slack notifications").assertDoesNotExist()
        compose.onNodeWithText("Battery optimization", substring = true).assertDoesNotExist()
    }

    @Test fun `nothing is open means setup is complete`() {
        assertEquals(false, setupIncomplete(PrereqState.ALL_GRANTED, slackConfirmed = true))
        assertEquals(true, setupIncomplete(PrereqState.ALL_GRANTED, slackConfirmed = false))
        assertEquals(true, setupIncomplete(PrereqState.ALL_GRANTED.copy(fullScreenAllowed = false), slackConfirmed = true))
    }
}
