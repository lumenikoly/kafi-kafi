package com.lightkafka.ui

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Clock

enum class AppLogLevel {
    INFO,
    WARN,
    ERROR,
}

data class AppLogEntry(
    val timestampEpochMillis: Long,
    val level: AppLogLevel,
    val message: String,
)

class AppLogStore(
    val logFile: Path,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun append(
        level: AppLogLevel,
        message: String,
    ) {
        logFile.parent?.let(Files::createDirectories)
        val encodedMessage = encode(message)
        val line = "${clock.millis()}|${level.name}|$encodedMessage\n"
        Files.writeString(
            logFile,
            line,
            Charsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
            StandardOpenOption.WRITE,
        )
    }

    fun readRecent(limit: Int): List<AppLogEntry> {
        if (limit <= 0 || !Files.exists(logFile)) {
            return emptyList()
        }

        return Files
            .readAllLines(logFile, Charsets.UTF_8)
            .takeLast(limit)
            .mapNotNull(::parseLine)
    }

    private fun parseLine(line: String): AppLogEntry? {
        val parts = line.split('|', limit = 3)
        if (parts.size != 3) {
            return null
        }

        val timestamp = parts[0].toLongOrNull() ?: return null
        val level = runCatching { AppLogLevel.valueOf(parts[1]) }.getOrNull() ?: return null
        return AppLogEntry(
            timestampEpochMillis = timestamp,
            level = level,
            message = decode(parts[2]),
        )
    }

    private companion object {
        fun encode(message: String): String =
            message
                .replace("\\", "\\\\")
                .replace("|", "\\|")
                .replace("\n", "\\n")

        fun decode(message: String): String {
            val restoredPipes = message.replace("\\|", "|")
            val restoredNewlines = restoredPipes.replace("\\n", "\n")
            return restoredNewlines.replace("\\\\", "\\")
        }
    }
}
