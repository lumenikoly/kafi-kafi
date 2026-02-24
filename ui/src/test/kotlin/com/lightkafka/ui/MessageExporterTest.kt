package com.lightkafka.ui

import com.lightkafka.core.kafka.ConsumedMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class MessageExporterTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `exports JSONL with one message per line`() {
        val exporter = MessageExporter(exportDirectory = tempDir)
        val messages =
            listOf(
                message(offset = 10, key = "order-\"1", value = "line\nitem"),
                message(offset = 11, key = "order-2", value = "{\"event\":\"created\"}"),
            )

        val result = exporter.export(messages = messages, format = MessageExportFormat.JSONL)
        val lines = Files.readAllLines(result.file)

        assertEquals(2, result.exportedCount)
        assertTrue(result.file.fileName.toString().endsWith(".jsonl"))
        assertEquals(2, lines.size)
        assertTrue(lines.first().contains("\"offset\":10"))
        assertTrue(lines.first().contains("line\\nitem"))
        assertTrue(lines.first().contains("order-\\\"1"))
    }

    @Test
    fun `exports CSV with a header and escaped values`() {
        val exporter = MessageExporter(exportDirectory = tempDir)
        val messages = listOf(message(offset = 100, key = "key,1", value = "value with \"quote\""))

        val result = exporter.export(messages = messages, format = MessageExportFormat.CSV)
        val lines = Files.readAllLines(result.file)

        assertEquals(1, result.exportedCount)
        assertTrue(result.file.fileName.toString().endsWith(".csv"))
        assertEquals("timestamp,topic,partition,offset,key,value,headers,sizeBytes", lines.first())
        assertEquals(2, lines.size)
        assertTrue(lines[1].contains("\"key,1\""))
        assertTrue(lines[1].contains("\"value with \"\"quote\"\"\""))
    }

    private fun message(
        offset: Long,
        key: String,
        value: String,
    ): ConsumedMessage =
        ConsumedMessage(
            topic = "orders",
            partition = 1,
            offset = offset,
            timestamp = 1_700_000_000_000,
            key = key.encodeToByteArray(),
            value = value.encodeToByteArray(),
            headers = mapOf("source" to "test".encodeToByteArray()),
        )
}
