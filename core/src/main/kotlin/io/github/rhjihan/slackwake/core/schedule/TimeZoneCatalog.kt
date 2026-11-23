// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.core.schedule

/**
 * The platform's time-zone database, as the app sees it: which IDs name the same zone,
 * and which zones a person may pick. IDs are IANA tz database IDs throughout.
 *
 * Implemented on Android with ICU/CLDR (java.time exposes no alias information), so this
 * module stays pure JVM.
 */
interface TimeZoneCatalog {
    /**
     * The one ID this app uses for [id]'s zone: its current IANA name, with aliases
     * collapsed ("US/Eastern" → "America/New_York", "Asia/Calcutta" → "Asia/Kolkata").
     * Null when [id] is not a zone this device knows. Idempotent.
     */
    fun canonicalId(id: String): String?

    /**
     * Zones offered for selection: one per real location (IANA zone.tab semantics, as in
     * Android and iOS settings), each already a [canonicalId]. No aliases, no Etc/ zones.
     */
    fun selectableIds(): List<String>
}
