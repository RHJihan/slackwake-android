// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.core.schedule

import io.github.rhjihan.slackwake.core.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleZonesTest {
    /** Knows two aliases; everything else is its own canonical ID. */
    private val zones =
        object : TimeZoneCatalog {
            private val aliases = mapOf("America/Indianapolis" to "America/Indiana/Indianapolis", "US/Eastern" to "America/New_York")

            override fun canonicalId(id: String) = aliases[id] ?: id

            override fun selectableIds() = listOf("America/Indiana/Indianapolis", "America/New_York")
        }

    /** A catalog that knows nothing (e.g. tzdata newer than ICU on some device). */
    private val unknowing =
        object : TimeZoneCatalog {
            override fun canonicalId(id: String): String? = null

            override fun selectableIds() = emptyList<String>()
        }

    @Test fun `aliases are stored under the canonical ID`() {
        assertEquals("America/Indiana/Indianapolis", ScheduleZones.canonicalize("America/Indianapolis", zones))
        assertEquals("America/New_York", ScheduleZones.canonicalize("US/Eastern", zones))
        assertEquals("America/New_York", ScheduleZones.canonicalize("America/New_York", zones))
    }

    @Test fun `blank and invalid IDs mean the phone zone`() {
        assertEquals("", ScheduleZones.canonicalize("", zones))
        assertEquals("", ScheduleZones.canonicalize("Mars/Olympus_Mons", zones))
    }

    @Test fun `a valid ID the catalog doesn't know is kept, not dropped`() {
        assertEquals("Asia/Dhaka", ScheduleZones.canonicalize("Asia/Dhaka", unknowing))
    }

    @Test fun `settings normalization with a catalog canonicalizes the schedule zone and is idempotent`() {
        val s = AppSettings(scheduleTimeZone = "America/Indianapolis", idleTimeoutSeconds = 1).normalized(zones)
        assertEquals("America/Indiana/Indianapolis", s.scheduleTimeZone)
        assertEquals(10, s.idleTimeoutSeconds)
        assertEquals(s, s.normalized(zones))
    }
}
