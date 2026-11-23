// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.listener

import android.app.Notification
import androidx.core.app.NotificationCompat
import io.github.rhjihan.slackwake.core.model.SlackEvent

/**
 * The one place Slack is identified. Exact package match — unlike Windows, which
 * matched any app whose display name contained "slack" and so could be impersonated.
 * Verified 2026-11 against the Play listing (note the capital S).
 */
const val SLACK_PACKAGE = "com.Slack"

/** Why a posted notification was not turned into an event. */
enum class SkipReason { NOT_SLACK, GROUP_SUMMARY, ONGOING, OWN_MESSAGE }

sealed interface ParseResult {
    data class Event(
        val event: SlackEvent,
    ) : ParseResult

    data class Skip(
        val reason: SkipReason,
    ) : ParseResult
}

object NotificationParser {
    fun parse(
        packageName: String,
        notification: Notification,
    ): ParseResult {
        if (packageName != SLACK_PACKAGE) return ParseResult.Skip(SkipReason.NOT_SLACK)
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return ParseResult.Skip(SkipReason.GROUP_SUMMARY)
        if (notification.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0) {
            return ParseResult.Skip(SkipReason.ONGOING)
        }

        fromMessagingStyle(notification)?.let { return it }
        return ParseResult.Event(fromPlainExtras(notification))
    }

    /**
     * Preferred path: conversation title → channel (null for a 1:1 DM), latest message's
     * sender → sender, latest message's text → text. A latest message with no sender is
     * the user's own (MessagingStyle convention — e.g. an inline reply echoed back), so
     * it is skipped.
     */
    private fun fromMessagingStyle(notification: Notification): ParseResult? {
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification) ?: return null
        val last = style.messages.lastOrNull() ?: return null
        val person = last.person ?: return ParseResult.Skip(SkipReason.OWN_MESSAGE)
        if (style.user.key != null && person.key == style.user.key) return ParseResult.Skip(SkipReason.OWN_MESSAGE)
        return ParseResult.Event(
            SlackEvent(
                sender = person.name?.toString()?.takeIf { it.isNotBlank() },
                channel = style.conversationTitle?.toString()?.takeIf { it.isNotBlank() },
                text = last.text?.toString()?.takeIf { it.isNotBlank() },
            ),
        )
    }

    /**
     * Fallback: EXTRA_TITLE / EXTRA_TEXT (big text preferred when present). The title is
     * classified like Windows did: starting with '#' → channel, otherwise → sender.
     */
    private fun fromPlainExtras(notification: Notification): SlackEvent {
        val extras = notification.extras
        val title =
            (extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) ?: extras.getCharSequence(Notification.EXTRA_TITLE))
                ?.toString()
                ?.takeIf { it.isNotBlank() }
        val text =
            (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
                ?.toString()
                ?.takeIf { it.isNotBlank() }
        return if (title?.startsWith("#") == true) {
            SlackEvent(sender = null, channel = title, text = text)
        } else {
            SlackEvent(sender = title, channel = null, text = text)
        }
    }
}

/**
 * Suppresses reposts of the same notification key whose content did not change (Slack
 * re-posts to update timestamps, badges, or after the user reads elsewhere). A changed
 * latest message on the same key is a new ping and passes. Bounded so it cannot grow
 * without limit if removals are missed.
 */
class NotificationDeduper(
    private val capacity: Int = 256,
) {
    private val seen =
        object : LinkedHashMap<String, Int>(capacity, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?) = size > capacity
        }

    /** Record without deciding — used to baseline notifications already present at connect. */
    fun seed(
        key: String,
        event: SlackEvent,
    ) {
        seen[key] = event.hashCode()
    }

    /** True if this is new content for [key] (and records it). */
    fun isNew(
        key: String,
        event: SlackEvent,
    ): Boolean {
        val fingerprint = event.hashCode()
        if (seen[key] == fingerprint) return false
        seen[key] = fingerprint
        return true
    }

    fun onRemoved(key: String) {
        seen.remove(key)
    }

    fun clear() = seen.clear()

    val size: Int get() = seen.size
}
