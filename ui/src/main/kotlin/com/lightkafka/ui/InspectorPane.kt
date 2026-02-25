package com.lightkafka.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
        Row(
            modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "MESSAGE INSPECTOR",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F2A44),
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Offset: ${selectedMessage?.offset ?: "-"}",
                style = MaterialTheme.typography.labelSmall,
                color = AccentMuted,
                fontFamily = FontFamily.Monospace,
            )
        }

        if (selectedMessage == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Select a message to inspect", color = Color(0xFF6B7280))
            }
            return
        }

        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SelectionContainer {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        inspectorField("TOPIC", selectedMessage.topic, Modifier.weight(1f))
                        inspectorField("PARTITION", selectedMessage.partition.toString(), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        inspectorField(
                            "TIMESTAMP",
                            formatTimestamp(selectedMessage.timestamp, includeDate = true),
                            Modifier.weight(1f),
                        )
                        inspectorField("KEY", previewBytes(selectedMessage.key, limit = 8_192), Modifier.weight(1f))
                    }
                    val headersText =
                        selectedMessage
                            .headers
                            .entries
                            .joinToString("\n") { entry -> "${entry.key}: ${previewBytes(entry.value)}" }
                            .ifBlank { "No headers" }
                    inspectorField("HEADERS", headersText, Modifier.fillMaxWidth())
                }
            }

            valuePane(selectedMessage = selectedMessage, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun inspectorField(
    label: String,
    value: String,
    modifier: Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = AccentMuted,
            fontWeight = FontWeight.Bold,
        )
        Text(
            value,
            modifier = Modifier.fillMaxWidth().background(Color.White).border(1.dp, Color(0xFFE5E7EB)).padding(10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF1F2937),
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun valuePane(
    selectedMessage: ConsumedMessage,
    modifier: Modifier,
) {
    Column(modifier = modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "VALUE (JSON)",
            style = MaterialTheme.typography.labelSmall,
            color = AccentMuted,
            fontWeight = FontWeight.Bold,
        )
        val scrollState = rememberScrollState()
        SelectionContainer {
            Text(
                previewBytes(selectedMessage.value, limit = 16_384),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE5E7EB))
                        .padding(10.dp)
                        .verticalScroll(scrollState),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF374151),
            )
        }
    }
}
