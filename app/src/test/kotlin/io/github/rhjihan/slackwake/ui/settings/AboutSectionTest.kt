// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.ui.settings

import android.app.Application
import android.content.Intent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class AboutSectionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `shows version and developer`() {
        compose.setContent { MaterialTheme { AboutSection(versionName = "9.9.9") } }
        compose.onNodeWithText("Version 9.9.9").assertExists()
        compose.onNodeWithText("Md. Rifat Hasan Jihan").assertExists()
        compose.onNodeWithText("rhjihan.github.io").assertDoesNotExist() // the name is the link
    }

    @Test fun `tapping the developer name opens their website`() {
        compose.setContent { MaterialTheme { AboutSection(versionName = "1") } }
        compose.onNodeWithTag("about_developer").performClick()
        val started = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://rhjihan.github.io/", started.dataString)
        assertTrue(started.hasCategory(Intent.CATEGORY_BROWSABLE))
    }

    @Test fun `license row opens the GPL v3 text`() {
        compose.setContent { MaterialTheme { AboutSection(versionName = "1") } }
        compose.onNodeWithText("GNU General Public License v3.0").assertExists()
        compose.onNodeWithTag("about_license").performClick()
        val started = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertEquals("https://www.gnu.org/licenses/gpl-3.0.txt", started.dataString)
    }
}
