// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.core.backup

import io.github.rhjihan.slackwake.core.settings.AppSettings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

/**
 * Settings backup file, written via the Storage Access Framework.
 *
 * ```json
 * { "format": "io.github.rhjihan.slackwake.settings", "schemaVersion": 1,
 *   "appVersion": "1.0.0", "exportedAt": "2026-11-23T10:15:00Z", "settings": { … } }
 * ```
 * `settings` uses the same keys as the app's own settings file (and the Windows app's
 * settings.json), so a plain Windows settings.json can be restored too.
 *
 * Reading is lenient where it is safe (unknown keys ignored, values clamped like any
 * other settings) and strict where it matters (wrong format, newer schema, oversized
 * or malformed files are refused and nothing changes).
 */
object SettingsBackup {
    const val FORMAT = "io.github.rhjihan.slackwake.settings"
    const val SCHEMA_VERSION = 1
    const val MIME_TYPE = "application/json"

    /** Refuse anything bigger: a real backup is a few KB. */
    const val MAX_BYTES = 1_000_000

    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = true
            coerceInputValues = true // invalid enum-like/null values → defaults
        }

    @Serializable
    private data class Envelope(
        @SerialName("format") val format: String,
        @SerialName("schemaVersion") val schemaVersion: Int,
        @SerialName("appVersion") val appVersion: String,
        @SerialName("exportedAt") val exportedAt: String,
        @SerialName("settings") val settings: JsonElement,
    )

    /** Suggested file name, e.g. "SlackWake-settings-2026-11-23.json". */
    fun fileName(date: java.time.LocalDate): String = "SlackWake-settings-$date.json"

    /** Serialize [settings] (device-local fields reset, so the file is portable). */
    fun encode(
        settings: AppSettings,
        appVersion: String,
        now: Instant,
    ): String {
        val portable = withDeviceLocal(settings.normalized(), from = AppSettings())
        val envelope =
            Envelope(
                format = FORMAT,
                schemaVersion = SCHEMA_VERSION,
                appVersion = appVersion,
                exportedAt = now.toString(),
                settings = json.encodeToJsonElement(AppSettings.serializer(), portable),
            )
        return json.encodeToString(Envelope.serializer(), envelope)
    }

    /** Decode a backup. Never throws; on failure the caller must leave settings untouched. */
    fun decode(text: String): DecodeResult {
        if (text.length > MAX_BYTES) return DecodeResult.Failure(Failure.TOO_LARGE)
        val root =
            try {
                json.parseToJsonElement(text) as? JsonObject
            } catch (_: SerializationException) {
                null
            } ?: return DecodeResult.Failure(Failure.NOT_JSON)

        val format = (root["format"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
        return when {
            format == FORMAT -> decodeEnvelope(root)

            // Plain settings file (e.g. Windows %AppData%\SlackWake\settings.json).
            format == null && looksLikeSettings(root) -> decodeSettings(root, Meta(source = Source.PLAIN_SETTINGS))

            else -> DecodeResult.Failure(Failure.NOT_SLACKWAKE)
        }
    }

    private fun decodeEnvelope(root: JsonObject): DecodeResult {
        val version = root["schemaVersion"]?.jsonPrimitive?.intOrNull ?: return DecodeResult.Failure(Failure.INVALID)
        if (version > SCHEMA_VERSION) return DecodeResult.Failure(Failure.NEWER_VERSION)
        if (version < 1) return DecodeResult.Failure(Failure.INVALID)
        val envelope =
            try {
                json.decodeFromJsonElement(Envelope.serializer(), root)
            } catch (_: SerializationException) {
                return DecodeResult.Failure(Failure.INVALID)
            } catch (_: IllegalArgumentException) {
                return DecodeResult.Failure(Failure.INVALID)
            }
        val settings = envelope.settings as? JsonObject ?: return DecodeResult.Failure(Failure.INVALID)
        val exportedAt = runCatching { Instant.parse(envelope.exportedAt) }.getOrNull()
        return decodeSettings(settings, Meta(Source.BACKUP, envelope.appVersion, exportedAt))
    }

    private fun decodeSettings(
        obj: JsonObject,
        meta: Meta,
    ): DecodeResult =
        try {
            DecodeResult.Success(json.decodeFromJsonElement(AppSettings.serializer(), obj).normalized(), meta)
        } catch (_: SerializationException) {
            DecodeResult.Failure(Failure.INVALID)
        } catch (_: IllegalArgumentException) {
            DecodeResult.Failure(Failure.INVALID)
        }

    /** At least two known top-level settings keys, so random JSON isn't taken for settings. */
    private fun looksLikeSettings(obj: JsonObject): Boolean = obj.keys.count { it in KNOWN_KEYS } >= 2

    private val KNOWN_KEYS =
        AppSettings.serializer().descriptor.let { d -> (0 until d.elementsCount).map(d::getElementName).toSet() }

    /**
     * The restored settings, keeping this device's values for things that belong to the
     * device rather than to the user's preferences: onboarding, the Slack-notifications
     * tick, verbose diagnostics (privacy), and the photosensitivity acknowledgement
     * (safety warnings are acknowledged per device).
     */
    fun applyRestore(
        current: AppSettings,
        restored: AppSettings,
    ): AppSettings = withDeviceLocal(restored, from = current).normalized()

    private fun withDeviceLocal(
        target: AppSettings,
        from: AppSettings,
    ) = target.copy(
        onboardingCompleted = from.onboardingCompleted,
        slackNotificationsConfirmed = from.slackNotificationsConfirmed,
        verboseDiagnostics = from.verboseDiagnostics,
        photosensitivityWarningDismissed = from.photosensitivityWarningDismissed,
    )

    enum class Source { BACKUP, PLAIN_SETTINGS }

    data class Meta(
        val source: Source,
        val appVersion: String? = null,
        val exportedAt: Instant? = null,
    )

    enum class Failure { TOO_LARGE, NOT_JSON, NOT_SLACKWAKE, NEWER_VERSION, INVALID }

    sealed interface DecodeResult {
        data class Success(
            val settings: AppSettings,
            val meta: Meta,
        ) : DecodeResult

        data class Failure(
            val reason: SettingsBackup.Failure,
        ) : DecodeResult
    }
}
