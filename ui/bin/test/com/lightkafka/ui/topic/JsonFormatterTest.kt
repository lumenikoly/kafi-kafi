package com.lightkafka.ui.topic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JsonFormatterTest {
    @Test
    fun `valid JSON object is pretty-printed`() {
        val result = JsonFormatter.detectAndFormat("""{"name":"Alice","age":30}""".encodeToByteArray())

        assertTrue(result is FormattedContent.Json)
        assertTrue((result as FormattedContent.Json).formatted.contains("  \"name\""))
    }

    @Test
    fun `non-JSON text returns PlainText`() {
        val result = JsonFormatter.detectAndFormat("Hello, World!".encodeToByteArray())

        assertEquals("Hello, World!", (result as FormattedContent.PlainText).text)
    }

    @Test
    fun `malformed JSON returns PlainText`() {
        val result = JsonFormatter.detectAndFormat("{invalid json content".encodeToByteArray())

        assertEquals("{invalid json content", (result as FormattedContent.PlainText).text)
    }

    @Test
    fun `null and non-UTF8 input return Binary`() {
        assertEquals(0, (JsonFormatter.detectAndFormat(null) as FormattedContent.Binary).size)
        assertEquals(
            3,
            (
                JsonFormatter.detectAndFormat(byteArrayOf(0x80.toByte(), 0xFF.toByte(), 0xFE.toByte())) as
                    FormattedContent.Binary
            ).size,
        )
    }

    @Test
    fun `tryFormatJson rejects invalid input`() {
        assertEquals(null, JsonFormatter.tryFormatJson("just text"))
        assertEquals(null, JsonFormatter.tryFormatJson("""{"broken"""))
    }

    @Test
    fun `long text is truncated at the configured limit`() {
        val result = JsonFormatter.truncateWithIndicator("A".repeat(15_000), maxLength = 10_000)

        assertEquals(10_000 + "... (truncated)".length, result.length)
        assertTrue(result.endsWith("... (truncated)"))
    }
}
