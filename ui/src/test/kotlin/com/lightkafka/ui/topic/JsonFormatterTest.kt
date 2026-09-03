package com.lightkafka.ui.topic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class JsonFormatterTest {
    @Nested
    inner class DetectAndFormat {
        @Test
        fun `valid JSON object is pretty-printed`() {
            val input = """{"name":"Alice","age":30}""".encodeToByteArray()
            val result = JsonFormatter.detectAndFormat(input)

            assertTrue(result is FormattedContent.Json)
            val json = result as FormattedContent.Json
            assertEquals("""{"name":"Alice","age":30}""", json.original)
            assertTrue(json.formatted.contains("  "))
            assertTrue(json.formatted.contains("\"name\""))
            assertTrue(json.formatted.contains("\"Alice\""))
        }

        @Test
        fun `valid JSON array is pretty-printed`() {
            val input = """[1,2,3]""".encodeToByteArray()
            val result = JsonFormatter.detectAndFormat(input)

            assertTrue(result is FormattedContent.Json)
            val json = result as FormattedContent.Json
            assertTrue(json.formatted.contains("  1"))
            assertTrue(json.formatted.contains("  2"))
        }

        @Test
        fun `nested JSON is preserved and formatted`() {
            val input = """{"user":{"name":"Bob","address":{"city":"NYC"}}}""".encodeToByteArray()
            val result = JsonFormatter.detectAndFormat(input)

            assertTrue(result is FormattedContent.Json)
            val json = result as FormattedContent.Json
            // Verify 4+ indent levels (2-space nesting)
            assertTrue(json.formatted.contains("    "))
            assertTrue(json.formatted.contains("\"city\""))
            assertTrue(json.formatted.contains("\"NYC\""))
        }

        @Test
        fun `non-JSON text returns PlainText`() {
            val input = "Hello, World!".encodeToByteArray()
            val result = JsonFormatter.detectAndFormat(input)

            assertTrue(result is FormattedContent.PlainText)
            assertEquals("Hello, World!", (result as FormattedContent.PlainText).text)
        }

        @Test
        fun `null input returns Binary with size 0`() {
            val result = JsonFormatter.detectAndFormat(null)

            assertTrue(result is FormattedContent.Binary)
            assertEquals(0, (result as FormattedContent.Binary).size)
        }

        @Test
        fun `empty input returns PlainText with empty string`() {
            val input = ByteArray(0)
            val result = JsonFormatter.detectAndFormat(input)

            assertTrue(result is FormattedContent.PlainText)
            assertEquals("", (result as FormattedContent.PlainText).text)
        }

        @Test
        fun `malformed JSON starting with brace returns PlainText`() {
            val input = """{invalid json content""".encodeToByteArray()
            val result = JsonFormatter.detectAndFormat(input)

            assertTrue(result is FormattedContent.PlainText)
            assertEquals("{invalid json content", (result as FormattedContent.PlainText).text)
        }

        @Test
        fun `ByteArray with non-UTF8 content returns Binary`() {
            // 0xFF is not valid UTF-8
            val input = byteArrayOf(0x80.toByte(), 0xFF.toByte(), 0xFE.toByte())
            val result = JsonFormatter.detectAndFormat(input)

            assertTrue(result is FormattedContent.Binary)
            assertEquals(3, (result as FormattedContent.Binary).size)
        }

        @Test
        fun `JSON with whitespace padding is detected and formatted`() {
            val input = "  \n  {\"key\":\"value\"}  \n ".encodeToByteArray()
            val result = JsonFormatter.detectAndFormat(input)

            assertTrue(result is FormattedContent.Json)
        }

        @Test
        fun `pretty-print uses 2-space indent`() {
            val input = """{"a":1,"b":2}""".encodeToByteArray()
            val result = JsonFormatter.detectAndFormat(input) as FormattedContent.Json

            // Expected:
            // {
            //   "a": 1,
            //   "b": 2
            // }
            val lines = result.formatted.lines()
            assertTrue(lines.size >= 3) { "Expected multi-line output, got: $result" }
            assertEquals("{", lines[0])
            // Keys should be indented with exactly 2 spaces
            assertTrue(lines[1].startsWith("  \"")) {
                "Expected 2-space indent, line was: '${lines[1]}'"
            }
        }
    }

    @Nested
    inner class TryFormatJson {
        @Test
        fun `returns null for non-JSON string`() {
            assertEquals(null, JsonFormatter.tryFormatJson("just text"))
        }

        @Test
        fun `returns null for malformed JSON`() {
            assertEquals(null, JsonFormatter.tryFormatJson("""{"broken"""))
        }

        @Test
        fun `returns formatted JSON for valid input`() {
            val result = JsonFormatter.tryFormatJson("""{"x":1}""")
            assertTrue(result != null && result.contains("\"x\""))
        }
    }

    @Nested
    inner class TruncateWithIndicator {
        @Test
        fun `short text is returned unchanged`() {
            val text = "Hello"
            assertEquals(text, JsonFormatter.truncateWithIndicator(text))
        }

        @Test
        fun `long text is truncated with suffix`() {
            val text = "A".repeat(15_000)
            val result = JsonFormatter.truncateWithIndicator(text, maxLength = 10_000)

            assertTrue(result.endsWith("... (truncated)"))
            assertTrue(result.length < text.length)
            // The truncated portion should be exactly maxLength + suffix
            assertEquals(10_000 + "... (truncated)".length, result.length)
        }

        @Test
        fun `exactly maxLength text is not truncated`() {
            val text = "A".repeat(100)
            val result = JsonFormatter.truncateWithIndicator(text, maxLength = 100)
            assertEquals(text, result)
        }

        @Test
        fun `default maxLength is 10000`() {
            val text = "A".repeat(10_001)
            val result = JsonFormatter.truncateWithIndicator(text)
            assertTrue(result.endsWith("... (truncated)"))
        }
    }
}
