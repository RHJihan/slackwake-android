// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Md. Rifat Hasan Jihan

package io.github.rhjihan.slackwake.data.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticLogTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `appends timestamped lines`() {
        val log = DiagnosticLog(tmp.root, now = { 0L })
        log.write("hello")
        log.write("world")
        val text = log.readAll()
        assertTrue(text.contains(" hello\n"))
        assertTrue(text.indexOf("hello") < text.indexOf("world"))
    }

    @Test fun `rotates into one previous file and keeps total under twice the cap`() {
        val log = DiagnosticLog(tmp.root, maxFileBytes = 1024)
        repeat(500) { log.write("line %04d ".format(it) + "x".repeat(20)) }
        log.flush()
        assertTrue(log.current.length() <= 1024)
        assertTrue(log.previous.exists())
        assertTrue(log.previous.length() <= 1024)
        val all = log.readAll()
        assertTrue("newest line survives", all.contains("line 0499"))
        assertFalse("oldest line rotated away", all.contains("line 0000"))
        assertEquals(2, tmp.root.listFiles()!!.size)
    }

    @Test fun `content is dropped unless verbose`() {
        val log = DiagnosticLog(tmp.root)
        log.content { "secret body" }
        assertFalse(log.readAll().contains("secret body"))
        log.verbose = true
        log.content { "secret body" }
        assertTrue(log.readAll().contains("[verbose] secret body"))
    }

    @Test fun `content lambda is not evaluated when not verbose`() {
        val log = DiagnosticLog(tmp.root)
        var evaluated = false
        log.content {
            evaluated = true
            ""
        }
        assertFalse(evaluated)
    }

    @Test fun `clear removes both files`() {
        val log = DiagnosticLog(tmp.root, maxFileBytes = 64)
        repeat(20) { log.write("0123456789") }
        log.clear()
        assertFalse(log.current.exists())
        assertFalse(log.previous.exists())
        assertEquals("", log.readAll())
    }
}
