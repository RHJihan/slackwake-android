// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.core.filter

import io.github.rhjihan.slackwake.core.model.SlackEvent
import io.github.rhjihan.slackwake.core.settings.AppSettings

/**
 * Keyword parsing and matching, ported 1:1 from MainViewModel.ParseKeywords /
 * MatchKeyword / ShouldSuppressByKeyword.
 */
object KeywordFilter {
    private const val COMMENT_PREFIX = "//"

    /**
     * Split on commas and line breaks; anything inside double quotes is one verbatim
     * keyword (commas included). Entries are trimmed; blanks are dropped; an UNQUOTED
     * entry starting with `//` is a commented-out keyword and is skipped.
     */
    fun parse(input: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var wasQuoted = false

        for (c in input) {
            when {
                c == '"' -> {
                    inQuotes = !inQuotes
                    wasQuoted = true
                }

                (c == ',' || c == '\n' || c == '\r') && !inQuotes -> {
                    take(current, wasQuoted)?.let(result::add)
                    current.clear()
                    wasQuoted = false
                }

                else -> {
                    current.append(c)
                }
            }
        }
        take(current, wasQuoted)?.let(result::add)
        return result
    }

    private fun take(
        builder: StringBuilder,
        wasQuoted: Boolean,
    ): String? {
        val token = builder.toString().trim()
        if (token.isEmpty()) return null
        if (!wasQuoted && token.startsWith(COMMENT_PREFIX)) return null
        return token
    }

    /**
     * Remove Slack mrkdwn delimiters (`*` bold, `_` italic, `~` strike, `` ` `` code) so
     * "deploy failed" matches "*deploy* failed".
     */
    fun stripFormatting(text: String): String = text.filterNot { it == '*' || it == '_' || it == '~' || it == '`' }

    /**
     * First keyword found (case-insensitive substring) in sender + channel + text, or
     * null. Note: as on Windows, a keyword made only of formatting characters (e.g. `*`)
     * strips to "" and therefore matches every ping.
     */
    fun match(
        event: SlackEvent,
        keywords: String,
    ): String? {
        val haystack =
            stripFormatting(
                listOf(event.sender.orEmpty(), event.channel.orEmpty(), event.text.orEmpty()).joinToString(" "),
            )
        return parse(keywords).firstOrNull { haystack.contains(stripFormatting(it), ignoreCase = true) }
    }

    /**
     * Allow-list first (drop anything that matches nothing), then mute-list (drop any
     * match) — so a ping must be allowed AND not muted, and muting wins on overlap. A
     * filter that is off or has blank text is inert; in particular an empty allow-list
     * never mutes everything.
     */
    fun evaluate(
        event: SlackEvent,
        settings: AppSettings,
    ): FilterResult {
        if (settings.keywordAllowEnabled &&
            settings.keywordAllowText.isNotBlank() &&
            match(event, settings.keywordAllowText) == null
        ) {
            return FilterResult.Suppressed("allow-list: no keyword matched")
        }
        if (settings.keywordFilterEnabled && settings.keywordFilterText.isNotBlank()) {
            match(event, settings.keywordFilterText)?.let {
                return FilterResult.Suppressed("mute keyword matched '$it'")
            }
        }
        return FilterResult.Allowed
    }
}

sealed interface FilterResult {
    data object Allowed : FilterResult

    data class Suppressed(
        val reason: String,
    ) : FilterResult
}
