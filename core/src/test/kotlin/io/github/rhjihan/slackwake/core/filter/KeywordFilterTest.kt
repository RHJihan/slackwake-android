// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.core.filter

import io.github.rhjihan.slackwake.core.model.SlackEvent
import io.github.rhjihan.slackwake.core.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordFilterTest {
    // ---- parse ----

    @Test fun `splits on commas and every kind of line break`() {
        assertEquals(listOf("a", "b", "c", "d"), KeywordFilter.parse("a,b\nc\r\nd"))
    }

    @Test fun `trims entries and drops blanks`() {
        assertEquals(listOf("standup", "#random"), KeywordFilter.parse("  standup , ,\n\n  #random  ,"))
    }

    @Test fun `empty and whitespace-only input yield nothing`() {
        assertEquals(emptyList<String>(), KeywordFilter.parse(""))
        assertEquals(emptyList<String>(), KeywordFilter.parse("  ,\n , "))
    }

    @Test fun `quoted phrase keeps commas and newlines verbatim`() {
        assertEquals(listOf("is now, on-call", "x"), KeywordFilter.parse("\"is now, on-call\", x"))
        assertEquals(listOf("line1\nline2"), KeywordFilter.parse("\"line1\nline2\""))
    }

    @Test fun `quotes are removed but surrounding text in the same entry is kept`() {
        assertEquals(listOf("say hi there"), KeywordFilter.parse("say \"hi\" there"))
    }

    @Test fun `unterminated quote swallows the rest as one keyword`() {
        assertEquals(listOf("a", "b, c"), KeywordFilter.parse("a, \"b, c"))
    }

    @Test fun `unquoted double-slash entries are comments`() {
        assertEquals(listOf("keep"), KeywordFilter.parse("// deploy, keep,   //x"))
    }

    @Test fun `quoted double-slash entry is a literal keyword`() {
        assertEquals(listOf("// literal"), KeywordFilter.parse("\"// literal\""))
    }

    @Test fun `hash channel names are not comments`() {
        assertEquals(listOf("#random"), KeywordFilter.parse("#random"))
    }

    @Test fun `empty quotes produce no keyword`() {
        assertEquals(emptyList<String>(), KeywordFilter.parse("\"\""))
    }

    // ---- match ----

    private val ping = SlackEvent(sender = "Alice", channel = "#Incidents", text = "*deploy* failed on _prod_")

    @Test fun `matches case-insensitively across sender, channel and text`() {
        assertEquals("alice", KeywordFilter.match(ping, "alice"))
        assertEquals("#incidents", KeywordFilter.match(ping, "#incidents"))
        assertEquals("PROD", KeywordFilter.match(ping, "PROD"))
    }

    @Test fun `slack formatting markers are ignored on both sides`() {
        assertEquals("deploy failed", KeywordFilter.match(ping, "deploy failed"))
        assertEquals("`deploy` failed", KeywordFilter.match(ping, "`deploy` failed"))
    }

    @Test fun `phrase spanning sender and channel matches through the joining space`() {
        assertEquals("alice #incidents", KeywordFilter.match(ping, "alice #incidents"))
    }

    @Test fun `returns the first configured keyword that matches`() {
        assertEquals("prod", KeywordFilter.match(ping, "nomatch, prod, alice"))
    }

    @Test fun `null fields are treated as empty`() {
        assertNull(KeywordFilter.match(SlackEvent(null, null, null), "x"))
        assertEquals("x", KeywordFilter.match(SlackEvent(null, null, "x"), "x"))
    }

    @Test fun `formatting-only keyword matches everything (Windows parity)`() {
        assertEquals("*", KeywordFilter.match(SlackEvent(null, null, "anything"), "*"))
    }

    // ---- evaluate ----

    private fun settings(
        allowOn: Boolean = false,
        allow: String = "",
        muteOn: Boolean = false,
        mute: String = "",
    ) = AppSettings(keywordAllowEnabled = allowOn, keywordAllowText = allow, keywordFilterEnabled = muteOn, keywordFilterText = mute)

    @Test fun `no filters allows everything`() {
        assertEquals(FilterResult.Allowed, KeywordFilter.evaluate(ping, settings()))
    }

    @Test fun `empty allow-list is inert`() {
        assertEquals(FilterResult.Allowed, KeywordFilter.evaluate(ping, settings(allowOn = true, allow = "  \n ")))
    }

    @Test fun `allow-list with only commented-out keywords mutes everything (text is not blank)`() {
        assertTrue(KeywordFilter.evaluate(ping, settings(allowOn = true, allow = "// off")) is FilterResult.Suppressed)
    }

    @Test fun `allow-list drops non-matching pings`() {
        val r = KeywordFilter.evaluate(ping, settings(allowOn = true, allow = "oncall"))
        assertEquals(FilterResult.Suppressed("allow-list: no keyword matched"), r)
    }

    @Test fun `allow-list lets matching pings through`() {
        assertEquals(FilterResult.Allowed, KeywordFilter.evaluate(ping, settings(allowOn = true, allow = "prod")))
    }

    @Test fun `mute-list drops matches`() {
        val r = KeywordFilter.evaluate(ping, settings(muteOn = true, mute = "alice"))
        assertEquals(FilterResult.Suppressed("mute keyword matched 'alice'"), r)
    }

    @Test fun `mute wins over allow on overlap`() {
        val r = KeywordFilter.evaluate(ping, settings(allowOn = true, allow = "prod", muteOn = true, mute = "prod"))
        assertTrue(r is FilterResult.Suppressed)
    }

    @Test fun `disabled filters are ignored even with text`() {
        assertEquals(FilterResult.Allowed, KeywordFilter.evaluate(ping, settings(allow = "zzz", mute = "alice")))
    }
}
