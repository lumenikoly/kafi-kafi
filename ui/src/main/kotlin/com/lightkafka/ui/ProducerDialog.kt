package com.lightkafka.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.lightkafka.core.storage.SendHistoryEntry
import com.lightkafka.core.storage.SendStatus
import java.util.UUID

@Composable
internal fun producerDialog(
    state: MainUiState,
    onAction: (MainUiAction) -> Unit,
) {
    Dialog(onDismissRequest = { onAction(MainUiAction.SetProducerPanelOpen(false)) }) {
        Surface(
            modifier = Modifier.width(980.dp).height(700.dp),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp,
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                templateList(state = state, onAction = onAction)
                VerticalDivider()

                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    producerForm(state = state, onAction = onAction)

                    HorizontalDivider()

                    Text("Send History", style = MaterialTheme.typography.titleSmall)
                    historyList(state = state)
                }
            }
        }
    }
}

@Composable
private fun templateList(
    state: MainUiState,
    onAction: (MainUiAction) -> Unit,
) {
    Column(
        modifier = Modifier.width(280.dp).fillMaxHeight().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Templates", style = MaterialTheme.typography.titleSmall)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.templates) { template ->
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onAction(MainUiAction.ApplyTemplate(template.id)) },
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(template.name, fontWeight = FontWeight.Medium)
                        Text(template.topic, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun producerForm(
    state: MainUiState,
    onAction: (MainUiAction) -> Unit,
) {
    Text("Producer", style = MaterialTheme.typography.titleSmall)

    OutlinedTextField(
        value = state.producerDraft.topic,
        onValueChange = { onAction(MainUiAction.UpdateProducerTopic(it)) },
        label = { Text("Topic") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.producerDraft.partitionText,
        onValueChange = { onAction(MainUiAction.UpdateProducerPartition(it)) },
        label = { Text("Partition (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.producerDraft.key,
        onValueChange = { onAction(MainUiAction.UpdateProducerKey(it)) },
        label = { Text("Key") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.producerDraft.value,
        onValueChange = { onAction(MainUiAction.UpdateProducerValue(it)) },
        label = { Text("Value") },
        modifier = Modifier.fillMaxWidth().height(120.dp),
    )
    OutlinedTextField(
        value = state.producerDraft.headersText,
        onValueChange = { onAction(MainUiAction.UpdateProducerHeaders(it)) },
        label = { Text("Headers (key=value per line)") },
        modifier = Modifier.fillMaxWidth().height(100.dp),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                val entry = createHistoryEntry(state)
                onAction(MainUiAction.AddHistoryEntry(entry))
            },
        ) {
            Text("Send")
        }
        TextButton(onClick = { onAction(MainUiAction.SetProducerPanelOpen(false)) }) {
            Text("Close")
        }
    }
}

@Composable
private fun historyList(state: MainUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(state.history) { entry ->
            val tone = if (entry.status == SendStatus.SUCCESS) "OK" else "ERR"
            val text =
                "$tone ${entry.topic} ${formatTimestamp(entry.timestampEpochMillis)} ${entry.errorMessage.orEmpty()}"
            Surface(
                tonalElevation = 1.dp,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = text,
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun createHistoryEntry(state: MainUiState): SendHistoryEntry {
    val status =
        if (state.producerDraft.value.isBlank()) {
            SendStatus.FAILURE
        } else {
            SendStatus.SUCCESS
        }
    val topic = state.producerDraft.topic.ifBlank { state.selectedTopic ?: "unknown" }
    return SendHistoryEntry(
        id = UUID.randomUUID().toString(),
        profileId = state.activeProfileId ?: "unknown",
        topic = topic,
        partition = state.producerDraft.partitionText.toIntOrNull(),
        status = status,
        timestampEpochMillis = System.currentTimeMillis(),
        errorMessage = if (status == SendStatus.FAILURE) "Value cannot be empty" else null,
    )
}
