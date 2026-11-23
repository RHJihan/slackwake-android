// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.data.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import io.github.rhjihan.slackwake.core.settings.AppSettings
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * JSON on disk, keyed with the Windows property names. Clamps on the way in and the way
 * out. Undecodable bytes raise [CorruptionException], which the DataStore's
 * ReplaceFileCorruptionHandler turns into defaults (no crash).
 */
object SettingsSerializer : Serializer<AppSettings> {
    val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }

    override val defaultValue: AppSettings = AppSettings()

    override suspend fun readFrom(input: InputStream): AppSettings {
        val text = input.readBytes().decodeToString()
        if (text.isBlank()) return defaultValue
        return try {
            json.decodeFromString(AppSettings.serializer(), text).normalized()
        } catch (e: SerializationException) {
            throw CorruptionException("Unreadable settings", e)
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("Unreadable settings", e)
        }
    }

    override suspend fun writeTo(
        t: AppSettings,
        output: OutputStream,
    ) {
        output.write(json.encodeToString(AppSettings.serializer(), t.normalized()).encodeToByteArray())
    }
}
