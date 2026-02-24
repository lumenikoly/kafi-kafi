package com.lightkafka.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AppLogStoreTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `append persists logs and readRecent returns ordered entries`() {
        val logFile = tempDir.resolve("app.log")
        val store = AppLogStore(logFile = logFile, clock = fixedClock("2026-02-24T12:00:00Z"))

        store.append(level = AppLogLevel.INFO, message = "Started")
        store.append(level = AppLogLevel.ERROR, message = "Failed to connect")

        val entries = store.readRecent(limit = 10)

        assertEquals(2, entries.size)
        assertEquals(AppLogLevel.INFO, entries[0].level)
        assertEquals(AppLogLevel.ERROR, entries[1].level)
        assertEquals("Failed to connect", entries[1].message)
        assertEquals(true, Files.exists(logFile))
    }

    @Test
    fun `readRecent ignores malformed lines`() {
        val logFile = tempDir.resolve("app.log")
        Files.writeString(logFile, "bad-line\n1700000000000|INFO|ok\n")
        val store = AppLogStore(logFile = logFile, clock = fixedClock("2026-02-24T12:00:00Z"))

        val entries = store.readRecent(limit = 20)

        assertEquals(1, entries.size)
        assertEquals("ok", entries.single().message)
    }

    private fun fixedClock(instantIso: String): Clock = Clock.fixed(Instant.parse(instantIso), ZoneOffset.UTC)
}
