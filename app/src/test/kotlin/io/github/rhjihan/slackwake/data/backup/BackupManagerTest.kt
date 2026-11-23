// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.data.backup

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rhjihan.slackwake.core.backup.SettingsBackup
import io.github.rhjihan.slackwake.core.settings.AppSettings
import io.github.rhjihan.slackwake.data.diagnostics.DiagnosticLog
import io.github.rhjihan.slackwake.data.settings.SettingsRepository
import io.github.rhjihan.slackwake.data.settings.SettingsSerializer
import io.github.rhjihan.slackwake.sound.SoundResolver
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupManagerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun TestScope.setup(): Pair<BackupManager, SettingsRepository> {
        val log = DiagnosticLog(tmp.newFolder())
        val store =
            DataStoreFactory.create(
                serializer = SettingsSerializer,
                corruptionHandler = ReplaceFileCorruptionHandler { AppSettings() },
                scope = backgroundScope,
                produceFile = { File(tmp.root, "settings-${System.nanoTime()}.json") },
            )
        val repo = SettingsRepository(store, log)
        return BackupManager(context, repo, SoundResolver(context, log), log) to repo
    }

    private fun fileUri(name: String) = Uri.fromFile(File(tmp.root, name))

    @Test fun `back up then restore round-trips preferences`() =
        runTest {
            val (manager, repo) = setup()
            repo.update { it.copy(idleTimeoutSeconds = 900, keywordFilterEnabled = true, keywordFilterText = "standup") }
            val uri = fileUri("backup.json")
            assertTrue(manager.export(uri))
            assertTrue(File(uri.path!!).readText().contains(SettingsBackup.FORMAT))

            repo.update { AppSettings() } // user changes everything afterwards
            val preview = (manager.read(uri) as ReadResult.Ok).preview
            assertEquals(SettingsBackup.Source.BACKUP, preview.meta.source)
            manager.apply(preview)
            assertEquals(900, repo.current().idleTimeoutSeconds)
            assertEquals("standup", repo.current().keywordFilterText)
        }

    @Test fun `a sound this phone cannot play is not restored`() =
        runTest {
            val (manager, repo) = setup()
            val uri = fileUri("other-phone.json")
            File(uri.path!!).writeText(
                """{"format":"${SettingsBackup.FORMAT}","schemaVersion":1,"appVersion":"1","exportedAt":"2026-11-23T10:00:00Z",
                   "settings":{"SoundUri":"content://media/internal/audio/media/999999"}}""",
            )
            repo.update { it.copy(soundUri = "") }
            val preview = (manager.read(uri) as ReadResult.Ok).preview
            assertTrue(preview.soundKept)
            assertEquals("", preview.merged.soundUri)
        }

    @Test fun `a rejected file changes nothing`() =
        runTest {
            val (manager, repo) = setup()
            repo.update { it.copy(idleTimeoutSeconds = 123) }
            val uri = fileUri("photo.json")
            File(uri.path!!).writeText("""{"name":"not settings"}""")
            assertEquals(ReadResult.Failed(SettingsBackup.Failure.NOT_SLACKWAKE), manager.read(uri))
            assertEquals(123, repo.current().idleTimeoutSeconds)
        }

    @Test fun `missing file reports a read failure`() =
        runTest {
            val (manager, _) = setup()
            assertEquals(ReadResult.Failed(null), manager.read(fileUri("does-not-exist.json")))
        }

    @Test fun `undo puts the previous settings back`() =
        runTest {
            val (manager, repo) = setup()
            repo.update { it.copy(soundLoop = false) }
            val uri = fileUri("b.json")
            File(uri.path!!).writeText(
                """{"format":"${SettingsBackup.FORMAT}","schemaVersion":1,"appVersion":"1","exportedAt":"x","settings":{"SoundLoop":true}}""",
            )
            val previous = manager.apply((manager.read(uri) as ReadResult.Ok).preview)
            assertTrue(repo.current().soundLoop)
            manager.undo(previous)
            assertFalse(repo.current().soundLoop)
        }
}
