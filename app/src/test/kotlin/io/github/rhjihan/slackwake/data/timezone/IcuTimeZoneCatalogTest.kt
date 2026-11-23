// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.data.timezone

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId
import android.icu.util.TimeZone as IcuTimeZone

@RunWith(AndroidJUnit4::class)
class IcuTimeZoneCatalogTest {
    private val catalog = IcuTimeZoneCatalog()

    @Test fun `aliases resolve to the current IANA name`() {
        assertEquals("America/Indiana/Indianapolis", catalog.canonicalId("America/Indianapolis"))
        assertEquals("America/Indiana/Indianapolis", catalog.canonicalId("US/East-Indiana"))
        assertEquals("America/New_York", catalog.canonicalId("US/Eastern"))
        assertEquals("Asia/Kolkata", catalog.canonicalId("Asia/Calcutta"))
        assertEquals("Europe/Kyiv", catalog.canonicalId("Europe/Kiev"))
    }

    @Test fun `canonical IDs are fixed points`() {
        for (id in catalog.selectableIds()) assertEquals(id, catalog.canonicalId(id))
        assertEquals("Asia/Dhaka", catalog.canonicalId("Asia/Dhaka"))
    }

    @Test fun `unknown IDs are rejected`() {
        assertNull(catalog.canonicalId("Mars/Olympus_Mons"))
    }

    @Test fun `selectable zones are one per real location, under current IANA names`() {
        val ids = catalog.selectableIds()
        assertEquals("no duplicates", ids.size, ids.toSet().size)
        assertEquals("no two IDs alias the same zone", ids.size, ids.map { IcuTimeZone.getCanonicalID(it) }.toSet().size)
        assertTrue("java.time accepts every ID", ZoneId.getAvailableZoneIds().containsAll(ids))
        for (alias in ALIASES) assertFalse("$alias hidden", alias in ids)
        for (current in CURRENT) assertTrue("$current listed", current in ids)
        assertFalse("no non-location zones", ids.any { it == "UTC" || it.startsWith("Etc/") })
    }

    @Test fun `location filter matches ICU's CANONICAL_LOCATION set`() {
        val icu = IcuTimeZone.getAvailableIDs(IcuTimeZone.SystemTimeZoneType.CANONICAL_LOCATION, null, null).toSet()
        assertEquals(icu, IcuTimeZoneCatalog.locationZones().toSet())
    }

    @Test fun `pre-API-36 rename table matches ICU exactly`() {
        for ((cldr, iana) in IcuTimeZoneCatalog.CLDR_TO_IANA) {
            assertEquals(cldr, IcuTimeZone.getCanonicalID(iana))
            assertEquals(iana, IcuTimeZone.getIanaID(cldr))
        }
        val renamed = IcuTimeZoneCatalog.locationZones().filter { IcuTimeZone.getIanaID(it) != it }.toSet()
        assertEquals(renamed, IcuTimeZoneCatalog.CLDR_TO_IANA.keys)
    }

    private companion object {
        val ALIASES =
            listOf(
                "America/Indianapolis",
                "America/Louisville",
                "America/Buenos_Aires",
                "Asia/Calcutta",
                "Asia/Saigon",
                "Europe/Kiev",
                "US/Eastern",
            )
        val CURRENT =
            listOf(
                "America/Indiana/Indianapolis",
                "America/Kentucky/Louisville",
                "America/Argentina/Buenos_Aires",
                "Asia/Kolkata",
                "Europe/Kyiv",
                "America/New_York",
                "Asia/Dhaka",
            )
    }
}
