// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.data.settings

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import app.cash.turbine.test
import io.github.rhjihan.slackwake.core.schedule.TimeZoneCatalog
import io.github.rhjihan.slackwake.core.settings.AppSettings
import io.github.rhjihan.slackwake.data.diagnostics.DiagnosticLog
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.repo(file: File): SettingsRepository {
        val log = DiagnosticLog(tmp.newFolder())
        val store =
            DataStoreFactory.create(
                serializer = SettingsSerializer,
                corruptionHandler = ReplaceFileCorruptionHandler { AppSettings() },
                scope = backgroundScope,
                produceFile = { file },
            )
        return SettingsRepository(store, log, zones)
    }

    /** Plain JVM test (no ICU): one alias, everything else canonical. */
    private val zones =
        object : TimeZoneCatalog {
            override fun canonicalId(id: String) = if (id == "America/Indianapolis") "America/Indiana/Indianapolis" else id

            override fun selectableIds() = emptyList<String>()
        }

    @Test fun `an alias saved by an older version is migrated to the canonical zone ID`() =
        runTest {
            val f = tmp.newFile("settings.json").apply { writeText("""{"ScheduleTimeZone":"America/Indianapolis"}""") }
            val r = repo(f)
            assertEquals("America/Indiana/Indianapolis", r.current().scheduleTimeZone)
            r.update { it.copy(enabled = false) }
            assertTrue(f.readText(), f.readText().contains("\"ScheduleTimeZone\": \"America/Indiana/Indianapolis\""))
        }

    @Test fun `picking an alias stores the canonical zone ID`() =
        runTest {
            val r = repo(File(tmp.root, "settings.json"))
            r.update { it.copy(scheduleTimeZone = "America/Indianapolis") }
            assertEquals("America/Indiana/Indianapolis", r.current().scheduleTimeZone)
        }

    @Test fun `missing file yields defaults`() =
        runTest {
            assertEquals(AppSettings(), repo(File(tmp.root, "settings.json")).current())
        }

    @Test fun `corrupt file resets to defaults without crashing`() =
        runTest {
            val f = tmp.newFile("settings.json").apply { writeText("{ this is not json") }
            assertEquals(AppSettings(), repo(f).current())
        }

    @Test fun `wrong types reset to defaults`() =
        runTest {
            val f = tmp.newFile("settings.json").apply { writeText("""{"IdleTimeoutSeconds":"soon"}""") }
            assertEquals(AppSettings(), repo(f).current())
        }

    @Test fun `out-of-range stored values are clamped on read`() =
        runTest {
            val f =
                tmp
                    .newFile(
                        "settings.json",
                    ).apply { writeText("""{"IdleTimeoutSeconds":1,"FlashIntervalMs":100,"FlashColorA":"zz"}""") }
            val s = repo(f).current()
            assertEquals(10, s.idleTimeoutSeconds)
            assertEquals(200, s.flashIntervalMs)
            assertEquals("#000000", s.flashColorA)
        }

    @Test fun `writes are clamped and persisted`() =
        runTest {
            val f = File(tmp.root, "settings.json")
            val r = repo(f)
            r.update { it.copy(alertMaxDurationSeconds = 100_000, keywordAllowText = "x") }
            assertEquals(3600, r.current().alertMaxDurationSeconds)
            val onDisk = f.readText()
            assertTrue(onDisk, onDisk.contains("\"AlertMaxDurationSeconds\": 3600"))
            assertTrue(onDisk, onDisk.contains("\"KeywordAllowText\": \"x\""))
        }

    @Test fun `flow emits each distinct change`() =
        runTest {
            val r = repo(File(tmp.root, "settings.json"))
            r.settings.test {
                assertEquals(true, awaitItem().enabled)
                r.update { it.copy(enabled = false) }
                assertEquals(false, awaitItem().enabled)
                r.update { it.copy(enabled = false) } // no-op
                r.update { it.copy(soundLoop = false) }
                assertEquals(false, awaitItem().soundLoop)
            }
        }
}
