package com.lightkafka.ui

import com.lightkafka.core.kafka.ConsumedMessage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Clock

enum class MessageExportFormat {
    JSONL,
    CSV,
}

data class MessageExportResult(
    val file: Path,
    val exportedCount: Int,
)

class MessageExporter(
    private val exportDirectory: Path,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun export(
        messages: List<ConsumedMessage>,
        format: MessageExportFormat,
    ): MessageExportResult {
        Files.createDirectories(exportDirectory)
        val file = exportDirectory.resolve(buildFileName(format))
        val lines =
            when (format) {
                MessageExportFormat.JSONL -> messages.map(::toJsonLine)
                MessageExportFormat.CSV -> listOf(CSV_HEADER) + messages.map(::toCsvLine)
            }

        Files.write(
            file,
            lines,
            Charsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )

        return MessageExportResult(file = file, exportedCount = messages.size)
    }

    private fun buildFileName(format: MessageExportFormat): String {
        val suffix = if (format == MessageExportFormat.JSONL) "jsonl" else "csv"
        return "messages-${clock.millis()}.$suffix"
    }

    private fun toJsonLine(message: ConsumedMessage): String {
        val keyText = decodeText(message.key)
        val valueText = decodeText(message.value)
        val headersText =
            message.headers.entries.joinToString(
                prefix = "{",
                postfix = "}",
                separator = ",",
            ) { entry ->
                val headerValue = decodeText(entry.value)
                "\"${escapeJson(entry.key)}\":\"${escapeJson(headerValue)}\""
            }
        val sizeBytes = (message.key?.size ?: 0) + (message.value?.size ?: 0)
        return buildString {
            append('{')
            append("\"timestamp\":${message.timestamp},")
            append("\"topic\":\"${escapeJson(message.topic)}\",")
            append("\"partition\":${message.partition},")
            append("\"offset\":${message.offset},")
            append("\"key\":\"${escapeJson(keyText)}\",")
            append("\"value\":\"${escapeJson(valueText)}\",")
            append("\"headers\":$headersText,")
            append("\"sizeBytes\":$sizeBytes")
            append('}')
        }
    }

    private fun toCsvLine(message: ConsumedMessage): String {
        val keyText = decodeText(message.key)
        val valueText = decodeText(message.value)
        val headersText = message.headers.entries.joinToString(";") { "${it.key}=${decodeText(it.value)}" }
        val sizeBytes = (message.key?.size ?: 0) + (message.value?.size ?: 0)
        return listOf(
            message.timestamp.toString(),
            message.topic,
            message.partition.toString(),
            message.offset.toString(),
            keyText,
            valueText,
            headersText,
            sizeBytes.toString(),
        ).joinToString(",") { value -> escapeCsv(value) }
    }

    private companion object {
        const val CSV_HEADER = "timestamp,topic,partition,offset,key,value,headers,sizeBytes"

        fun decodeText(value: ByteArray?): String = value?.toString(Charsets.UTF_8).orEmpty()

        fun escapeJson(value: String): String =
            buildString {
                value.forEach { ch ->
                    when (ch) {
                        '\\' -> append("\\\\")
                        '"' -> append("\\\"")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        else -> append(ch)
                    }
                }
            }

        fun escapeCsv(value: String): String {
            val escaped = value.replace("\"", "\"\"")
            return if (escaped.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
                "\"$escaped\""
            } else {
                escaped
            }
        }
    }
}
