// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.ui.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class TimeZonePickerTest {
    private val winter = Instant.parse("2026-01-15T12:00:00Z")
    private val summer = Instant.parse("2026-07-15T12:00:00Z")

    @Test fun `gmt offsets read like system settings`() {
        assertEquals("GMT", formatGmtOffset(0))
        assertEquals("GMT+6", formatGmtOffset(6 * 3600))
        assertEquals("GMT−5", formatGmtOffset(-5 * 3600))
        assertEquals("GMT+5:30", formatGmtOffset(5 * 3600 + 30 * 60))
        assertEquals("GMT+5:45", formatGmtOffset(5 * 3600 + 45 * 60))
    }

    @Test fun `zone description uses the city and follows DST`() {
        val ny = describeZone("America/New_York", Locale.US, winter)
        assertEquals("New York", ny.city)
        assertEquals("GMT−5", ny.offsetText)
        assertEquals("GMT−4", describeZone("America/New_York", Locale.US, summer).offsetText)
        assertEquals("GMT+6", describeZone("Asia/Dhaka", Locale.US, winter).offsetText)
    }

    @Test fun `list offers real city zones only, sorted by offset`() {
        val all = zoneOptions(Locale.US, winter)
        val ids = all.map { it.id }.toSet()
        assertTrue("America/New_York" in ids)
        assertTrue("Asia/Dhaka" in ids)
        assertFalse("legacy alias hidden", "US/Eastern" in ids)
        assertFalse("Etc zones hidden", ids.any { it.startsWith("Etc/") })
        assertEquals(all.sortedWith(compareBy({ it.offsetSeconds }, { it.city })), all)
    }
}
