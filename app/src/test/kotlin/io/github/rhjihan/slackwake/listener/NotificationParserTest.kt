// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.listener

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rhjihan.slackwake.core.model.SlackEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationParserTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun builder() = NotificationCompat.Builder(context, "test").setSmallIcon(android.R.drawable.ic_dialog_info)

    private fun messaging(
        title: String?,
        vararg messages: Pair<String?, String>,
        group: Boolean = title != null,
    ): Notification {
        val me =
            Person
                .Builder()
                .setName("Me")
                .setKey("me")
                .build()
        val style = NotificationCompat.MessagingStyle(me).setConversationTitle(title).setGroupConversation(group)
        var t = 1_000L
        messages.forEach { (sender, text) ->
            val person =
                sender?.let {
                    Person
                        .Builder()
                        .setName(it)
                        .setKey(it)
                        .build()
                }
            style.addMessage(text, t++, person)
        }
        return builder().setStyle(style).build()
    }

    private fun event(n: Notification) = (NotificationParser.parse(SLACK_PACKAGE, n) as ParseResult.Event).event

    @Test fun `only the exact Slack package is accepted`() {
        val n = builder().setContentTitle("Alice").setContentText("hi").build()
        assertEquals(ParseResult.Skip(SkipReason.NOT_SLACK), NotificationParser.parse("com.slack", n))
        assertEquals(ParseResult.Skip(SkipReason.NOT_SLACK), NotificationParser.parse("com.Slack.fake", n))
        assertEquals(ParseResult.Skip(SkipReason.NOT_SLACK), NotificationParser.parse("evil.app", n))
        assertTrue(NotificationParser.parse("com.Slack", n) is ParseResult.Event)
    }

    @Test fun `messaging style channel uses conversation title and latest message`() {
        val n = messaging("#incidents", "Bob" to "first", "Alice" to "db is down")
        assertEquals(SlackEvent(sender = "Alice", channel = "#incidents", text = "db is down"), event(n))
    }

    @Test fun `messaging style direct message has no channel`() {
        val n = messaging(null, "Alice" to "you there?")
        assertEquals(SlackEvent(sender = "Alice", channel = null, text = "you there?"), event(n))
    }

    @Test fun `latest message from the user is skipped`() {
        val n = messaging("#ops", "Alice" to "ping", null to "my reply")
        assertEquals(ParseResult.Skip(SkipReason.OWN_MESSAGE), NotificationParser.parse(SLACK_PACKAGE, n))
    }

    @Test fun `plain notification with hash title is a channel`() {
        val n = builder().setContentTitle("#random").setContentText("lunch?").build()
        assertEquals(SlackEvent(sender = null, channel = "#random", text = "lunch?"), event(n))
    }

    @Test fun `plain notification with other title is a sender`() {
        val n = builder().setContentTitle("Alice").setContentText("hello").build()
        assertEquals(SlackEvent(sender = "Alice", channel = null, text = "hello"), event(n))
    }

    @Test fun `big text is preferred over the truncated text`() {
        val n =
            builder()
                .setContentTitle("Alice")
                .setContentText("short…")
                .setStyle(NotificationCompat.BigTextStyle().bigText("the whole long message"))
                .build()
        assertEquals("the whole long message", event(n).text)
    }

    @Test fun `empty notification still yields an event (overlay shows no preview)`() {
        assertEquals(SlackEvent(null, null, null), event(builder().build()))
    }

    @Test fun `group summaries are skipped`() {
        val n =
            builder()
                .setContentTitle("2 new")
                .setGroup("g")
                .setGroupSummary(true)
                .build()
        assertEquals(ParseResult.Skip(SkipReason.GROUP_SUMMARY), NotificationParser.parse(SLACK_PACKAGE, n))
    }

    @Test fun `ongoing notifications are skipped`() {
        val n = builder().setContentTitle("Syncing").setOngoing(true).build()
        assertEquals(ParseResult.Skip(SkipReason.ONGOING), NotificationParser.parse(SLACK_PACKAGE, n))
    }

    // ---- dedupe ----

    @Test fun `unchanged repost of the same key is a duplicate`() {
        val d = NotificationDeduper()
        val e = SlackEvent("Alice", null, "hi")
        assertTrue(d.isNew("k", e))
        assertFalse(d.isNew("k", e))
        assertFalse(d.isNew("k", e.copy()))
    }

    @Test fun `changed content on the same key is new`() {
        val d = NotificationDeduper()
        assertTrue(d.isNew("k", SlackEvent("Alice", null, "hi")))
        assertTrue(d.isNew("k", SlackEvent("Alice", null, "hi again")))
    }

    @Test fun `same content on a different key is new`() {
        val d = NotificationDeduper()
        val e = SlackEvent("Alice", null, "hi")
        assertTrue(d.isNew("a", e))
        assertTrue(d.isNew("b", e))
    }

    @Test fun `removal forgets the key and seeding baselines it`() {
        val d = NotificationDeduper()
        val e = SlackEvent("Alice", null, "hi")
        d.seed("k", e)
        assertFalse(d.isNew("k", e))
        d.onRemoved("k")
        assertTrue(d.isNew("k", e))
    }

    @Test fun `capacity is bounded`() {
        val d = NotificationDeduper(capacity = 3)
        repeat(10) { d.isNew("k$it", SlackEvent(null, null, "$it")) }
        assertEquals(3, d.size)
    }

    @Test fun `messaging style repost with an appended message is new`() {
        val d = NotificationDeduper()
        assertTrue(d.isNew("k", event(messaging("#ops", "Alice" to "one"))))
        assertFalse(d.isNew("k", event(messaging("#ops", "Alice" to "one"))))
        assertTrue(d.isNew("k", event(messaging("#ops", "Alice" to "one", "Bob" to "two"))))
    }
}
