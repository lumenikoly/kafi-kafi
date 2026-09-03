package com.lightkafka.ui.topic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Result of content formatting — distinguishes JSON, plain text, and binary payloads.
 */
sealed interface FormattedContent {
    data class Json(
        val formatted: String,
        val original: String,
    ) : FormattedContent

    data class PlainText(
        val text: String,
    ) : FormattedContent

    data class Binary(
        val size: Int,
    ) : FormattedContent
}

/**
 * Detects JSON content and formats it with 2-space pretty-printing.
 * Pure Kotlin utility — no Compose dependency, fully testable in isolation.
 */
object JsonFormatter {
    private const val DEFAULT_MAX_LENGTH = 10_000
    private const val TRUNCATION_SUFFIX = "... (truncated)"

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val jsonParser =
        Json { ignoreUnknownKeys = true }

    private val utf8Decoder =
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)

    /**
     * Detects the content type of [input] bytes and returns the appropriate
     * [FormattedContent] variant.
     *
     * - `null` → [FormattedContent.Binary] with size 0
     * - Non-UTF-8 bytes → [FormattedContent.Binary] with actual size
     * - Valid JSON (object or array) → [FormattedContent.Json] with pretty-printed output
     * - Everything else → [FormattedContent.PlainText]
     */
    fun detectAndFormat(input: ByteArray?): FormattedContent {
        if (input == null) {
            return FormattedContent.Binary(size = 0)
        }

        val text =
            try {
                utf8Decoder.decode(java.nio.ByteBuffer.wrap(input)).toString()
            } catch (_: CharacterCodingException) {
                return FormattedContent.Binary(size = input.size)
            }

        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return FormattedContent.PlainText(text = "")
        }

        val pretty = tryFormatJson(trimmed)
        return if (pretty != null) {
            FormattedContent.Json(formatted = pretty, original = trimmed)
        } else {
            FormattedContent.PlainText(text = text)
        }
    }

    /**
     * Attempts to parse [text] as JSON and pretty-print it.
     * Returns the formatted string on success, or `null` if the text
     * is not valid JSON.
     */
    fun tryFormatJson(text: String): String? {
        val trimmed = text.trim()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return null
        }
        return try {
            val element: JsonElement = jsonParser.parseToJsonElement(trimmed)

            @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
            val prettyPrinter =
                Json {
                    prettyPrint = true
                    prettyPrintIndent = "  "
                }
            prettyPrinter.encodeToString(JsonElement.serializer(), element)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Truncates [text] to [maxLength] characters, appending an indicator
     * if truncation was necessary.
     */
    fun truncateWithIndicator(
        text: String,
        maxLength: Int = DEFAULT_MAX_LENGTH,
    ): String {
        if (text.length <= maxLength) {
            return text
        }
        return text.take(maxLength) + TRUNCATION_SUFFIX
    }
}
