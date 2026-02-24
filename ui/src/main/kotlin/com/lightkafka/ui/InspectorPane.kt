package com.lightkafka.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lightkafka.core.kafka.ConsumedMessage

@Composable
internal fun inspectorPane(
    selectedMessage: ConsumedMessage?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Inspector", style = MaterialTheme.typography.titleSmall)
        if (selectedMessage == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Select a message to inspect")
            }
            return
        }

        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                inspectorField("Topic", selectedMessage.topic)
                inspectorField("Partition", selectedMessage.partition.toString())
                inspectorField("Offset", selectedMessage.offset.toString())
                inspectorField("Timestamp", formatTimestamp(selectedMessage.timestamp, includeDate = true))
                inspectorField("Key", previewBytes(selectedMessage.key, limit = 8_192))
                inspectorField("Value", previewBytes(selectedMessage.value, limit = 8_192))
                val headersText =
                    selectedMessage
                        .headers
                        .entries
                        .joinToString("\n") { entry -> "${entry.key}=${previewBytes(entry.value)}" }
                        .ifBlank { "No headers" }
                inspectorField("Headers", headersText)
            }
        }
    }
}

@Composable
private fun inspectorField(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 1.dp,
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                value,
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
