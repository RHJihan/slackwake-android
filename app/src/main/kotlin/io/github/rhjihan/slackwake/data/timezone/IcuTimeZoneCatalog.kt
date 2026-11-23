// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.data.timezone

import android.os.Build
import io.github.rhjihan.slackwake.core.schedule.TimeZoneCatalog
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import android.icu.util.TimeZone as IcuTimeZone

/**
 * [TimeZoneCatalog] backed by the device's ICU, which carries CLDR's zone metadata
 * (aliases and each zone's country), the same data Android Settings is built on.
 *
 * - Identity: CLDR canonical IDs collapse every alias of a zone to one ID.
 * - Naming: CLDR keeps a zone's first spelling as its stable ID even after IANA renames
 *   it ("Asia/Calcutta"), so IDs are converted to IANA's current name ("Asia/Kolkata").
 * - Selection: canonical zones with a country, which is IANA's zone.tab set.
 *
 * Every ID returned is accepted by this device's java.time, which does the scheduling: if
 * its tzdata predates a rename (e.g. "Europe/Kyiv", 2022b), the older name is used.
 */
@Singleton
class IcuTimeZoneCatalog
    @Inject
    constructor() : TimeZoneCatalog {
        private val javaZoneIds: Set<String> = ZoneId.getAvailableZoneIds()

        private val selectable: List<String> by lazy {
            locationZones().mapNotNull(::canonicalId).distinct()
        }

        override fun canonicalId(id: String): String? {
            val cldr = cldrId(id) ?: return id.takeIf { it in javaZoneIds }
            return listOf(ianaName(cldr), cldr, id).firstOrNull { it in javaZoneIds }
        }

        override fun selectableIds(): List<String> = selectable

        companion object {
            /** CLDR's ID for [id] (alias → canonical), or null if ICU doesn't know it. */
            fun cldrId(id: String): String? = runCatching { IcuTimeZone.getCanonicalID(id) }.getOrNull()

            /**
             * CLDR canonical zones tied to a country or territory ("001" is CLDR's region for
             * non-location zones such as Etc/UTC). Equivalent to ICU's
             * SystemTimeZoneType.CANONICAL_LOCATION, which needs API 28 (minSdk is 26).
             */
            fun locationZones(): List<String> = IcuTimeZone.getAvailableIDs().filter { cldrId(it) == it && IcuTimeZone.getRegion(it) != "001" }

            /** IANA's current name for a CLDR canonical ID. */
            fun ianaName(cldr: String): String =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                    IcuTimeZone.getIanaID(cldr) ?: cldr
                } else {
                    CLDR_TO_IANA[cldr] ?: cldr
                }

            /**
             * CLDR canonical IDs whose IANA name has since changed: the `iana` attribute of
             * CLDR's common/bcp47/timezone.xml. Only consulted below API 36, which lacks
             * ICU's getIanaID; a unit test checks it against ICU so it can't drift.
             */
            val CLDR_TO_IANA: Map<String, String> =
                mapOf(
                    "Africa/Asmera" to "Africa/Asmara",
                    "America/Buenos_Aires" to "America/Argentina/Buenos_Aires",
                    "America/Catamarca" to "America/Argentina/Catamarca",
                    "America/Coral_Harbour" to "America/Atikokan",
                    "America/Cordoba" to "America/Argentina/Cordoba",
                    "America/Godthab" to "America/Nuuk",
                    "America/Indianapolis" to "America/Indiana/Indianapolis",
                    "America/Jujuy" to "America/Argentina/Jujuy",
                    "America/Louisville" to "America/Kentucky/Louisville",
                    "America/Mendoza" to "America/Argentina/Mendoza",
                    "Asia/Calcutta" to "Asia/Kolkata",
                    "Asia/Katmandu" to "Asia/Kathmandu",
                    "Asia/Rangoon" to "Asia/Yangon",
                    "Asia/Saigon" to "Asia/Ho_Chi_Minh",
                    "Atlantic/Faeroe" to "Atlantic/Faroe",
                    "Europe/Kiev" to "Europe/Kyiv",
                    "Pacific/Enderbury" to "Pacific/Kanton",
                    "Pacific/Ponape" to "Pacific/Pohnpei",
                    "Pacific/Truk" to "Pacific/Chuuk",
                )
        }
    }
