package com.lightkafka.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.lightkafka.core.kafka.ConsumedMessage

@Composable
internal fun inspectorPane(
    selectedMessage: ConsumedMessage?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.background(InspectorBackgroundColor),
    ) {
        // Header
        Surface(
            color = SurfaceCard,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Gradient accent
                Box(
                    modifier =
                        Modifier
                            .width(4.dp)
                            .height(24.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(AccentViolet, AccentPink),
                                ),
                                RoundedCornerShape(2.dp),
                            ),
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "Message Inspector",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (selectedMessage != null) {
                    Surface(
                        color = SurfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = "Offset: ${selectedMessage.offset}",
                            style = MaterialTheme.typography.labelMedium,
                            color = AccentViolet,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        if (selectedMessage == null) {
            emptyInspectorState()
            return
        }

        // Content
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Metadata panel
            SelectionContainer {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Topic & Partition row
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        inspectorField(
                            label = "TOPIC",
                            value = selectedMessage.topic,
                            modifier = Modifier.weight(1f),
                            isHighlighted = true,
                        )
                        inspectorField(
                            label = "PARTITION",
                            value = selectedMessage.partition.toString(),
                            modifier = Modifier.width(120.dp),
                        )
                    }

                    // Timestamp & Key row
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        inspectorField(
                            label = "TIMESTAMP",
                            value = formatTimestamp(selectedMessage.timestamp, includeDate = true),
                            modifier = Modifier.weight(1f),
                        )
                        inspectorField(
                            label = "KEY",
                            value = previewBytes(selectedMessage.key, limit = 8_192).ifEmpty { "null" },
                            modifier = Modifier.weight(1f),
                            isMonospace = true,
                        )
                    }

                    // Headers
                    val headersText =
                        selectedMessage
                            .headers
                            .entries
                            .joinToString("\n") { entry ->
                                "${entry.key}: ${previewBytes(entry.value)}"
                            }.ifBlank { "No headers" }
                    inspectorField(
                        label = "HEADERS",
                        value = headersText,
                        modifier = Modifier.fillMaxWidth(),
                        isMultiline = true,
                    )
                }
            }

            // Value panel
            valuePane(selectedMessage = selectedMessage, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun emptyInspectorState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(64.dp)
                        .background(SurfaceElevated, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "?",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextMuted,
                    fontWeight = FontWeight.Light,
                )
            }
            Text(
                text = "Select a message to inspect",
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun inspectorField(
    label: String,
    value: String,
    modifier: Modifier,
    isHighlighted: Boolean = false,
    isMonospace: Boolean = false,
    isMultiline: Boolean = false,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AccentViolet,
            fontWeight = FontWeight.Bold,
            letterSpacing =
                androidx.compose.ui.unit
                    .TextUnit(1f, androidx.compose.ui.unit.TextUnitType.Sp),
        )
        Surface(
            color = if (isHighlighted) AccentViolet.copy(alpha = 0.08f) else SurfaceElevated,
            shape = RoundedCornerShape(10.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp)),
        ) {
            val scrollState = rememberScrollState()
            Text(
                text = value,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (isMultiline) {
                                Modifier
                                    .height(120.dp)
                                    .verticalScroll(scrollState)
                            } else {
                                Modifier
                            },
                        ).padding(14.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (value == "null" || value == "No headers") TextMuted else TextPrimary,
                fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
                maxLines = if (isMultiline) Int.MAX_VALUE else 1,
            )
        }
    }
}

@Composable
private fun valuePane(
    selectedMessage: ConsumedMessage,
    modifier: Modifier,
) {
    val scrollState = rememberScrollState()
    val rawValue = previewBytes(selectedMessage.value, limit = 16_384)
    val displayValue = formatJsonValue(rawValue)

    Column(modifier = modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "VALUE",
                style = MaterialTheme.typography.labelSmall,
                color = AccentViolet,
                fontWeight = FontWeight.Bold,
                letterSpacing =
                    androidx.compose.ui.unit
                        .TextUnit(1f, androidx.compose.ui.unit.TextUnitType.Sp),
            )
            Surface(
                color = SurfaceElevated,
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(
                    text = "JSON",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        // Value display
        Surface(
            color = SurfaceDark,
            shape = RoundedCornerShape(10.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp)),
        ) {
            SelectionContainer {
                Text(
                    text = displayValue,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(scrollState),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = TextPrimary,
                )
            }
        }
    }
}

private fun formatJsonValue(json: String): AnnotatedString {
    if (json.isEmpty()) return AnnotatedString("")

    return buildAnnotatedString {
        var inString = false
        var inKey = false
        var i = 0

        while (i < json.length) {
            val char = json[i]

            when {
                char == '"' && (i == 0 || json[i - 1] != '\\') -> {
                    inString = !inString
                    if (!inString) inKey = false
                    withStyle(style = SpanStyle(color = AccentEmerald)) {
                        append(char)
                    }
                }
                inString && char == ':' && json.substring(i + 1).trimStart().firstOrNull() == '{' -> {
                    withStyle(style = SpanStyle(color = TextSecondary)) {
                        append(char)
                    }
                }
                inString -> {
                    withStyle(style = SpanStyle(color = AccentEmerald)) {
                        append(char)
                    }
                }
                char == ':' -> {
                    inKey = false
                    withStyle(style = SpanStyle(color = TextSecondary)) {
                        append(char)
                    }
                }
                char == '{' || char == '}' || char == '[' || char == ']' -> {
                    withStyle(style = SpanStyle(color = AccentViolet)) {
                        append(char)
                    }
                }
                char == ',' -> {
                    withStyle(style = SpanStyle(color = TextMuted)) {
                        append(char)
                    }
                }
                char.isDigit() || (char == '-' && i + 1 < json.length && json[i + 1].isDigit()) -> {
                    // Number
                    var numEnd = i
                    while (
                        numEnd < json.length &&
                        (
                            json[numEnd].isDigit() ||
                                json[numEnd] == '.' ||
                                json[numEnd] == '-' ||
                                json[numEnd] == 'e' ||
                                json[numEnd] == 'E'
                        )
                    ) {
                        numEnd++
                    }
                    withStyle(style = SpanStyle(color = AccentAmber)) {
                        append(json.substring(i, numEnd))
                    }
                    i = numEnd - 1
                }
                json.substring(i).startsWith("true") || json.substring(i).startsWith("false") -> {
                    val boolLen = if (json.substring(i).startsWith("true")) 4 else 5
                    withStyle(style = SpanStyle(color = AccentCyan)) {
                        append(json.substring(i, i + boolLen))
                    }
                    i += boolLen - 1
                }
                json.substring(i).startsWith("null") -> {
                    withStyle(style = SpanStyle(color = TextMuted, fontStyle = FontStyle.Italic)) {
                        append("null")
                    }
                    i += 3
                }
                char.isWhitespace() -> {
                    append(char)
                }
                else -> {
                    withStyle(style = SpanStyle(color = TextPrimary)) {
                        append(char)
                    }
                }
            }
            i++
        }
    }
}
