package com.lightkafka.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lightkafka.core.kafka.ConsumedMessage
import com.lightkafka.core.storage.ClusterProfile

@Composable
internal fun topBar(
    state: MainUiState,
    onAction: (MainUiAction) -> Unit,
) {
    Surface(shadowElevation = 4.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Light Kafka Viewer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            profileSwitcher(
                profiles = state.profiles,
                activeProfileId = state.activeProfileId,
                onSelect = { onAction(MainUiAction.SetActiveProfile(it)) },
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(onClick = { onAction(MainUiAction.TogglePause) }) {
                Text(if (state.isConsumerPaused) "Resume" else "Pause")
            }

            TextButton(onClick = { onAction(MainUiAction.SetProducerPanelOpen(true)) }) {
                Text("Producer")
            }

            TextButton(onClick = { onAction(MainUiAction.SetConnectionManagerOpen(true)) }) {
                Text("Connections")
            }

            TextButton(onClick = { onAction(MainUiAction.SetDiagnosticsOpen(true)) }) {
                Text("Diagnostics")
            }
        }
    }
}

@Composable
private fun profileSwitcher(
    profiles: List<ClusterProfile>,
    activeProfileId: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val activeName = profiles.firstOrNull { it.id == activeProfileId }?.name ?: "No profile"

    Box {
        TextButton(onClick = { expanded = true }) {
            Text("Profile: $activeName")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = { Text(profile.name) },
                    onClick = {
                        expanded = false
                        onSelect(profile.id)
                    },
                )
            }
        }
    }
}

@Composable
internal fun topicsPane(
    state: MainUiState,
    onAction: (MainUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Topics", style = MaterialTheme.typography.titleSmall)

        OutlinedTextField(
            value = state.topicSearchQuery,
            onValueChange = { onAction(MainUiAction.SetTopicSearch(it)) },
            label = { Text("Search topics") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(state.visibleTopics()) { topic ->
                val selected = topic == state.selectedTopic
                val background =
                    if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                onClick = { onAction(MainUiAction.SelectTopic(topic)) },
                            ),
                    color = background,
                    tonalElevation = if (selected) 2.dp else 0.dp,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = topic,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun messagesPane(
    state: MainUiState,
    messages: List<ConsumedMessage>,
    onAction: (MainUiAction) -> Unit,
    onExport: (MessageExportFormat, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        filterBar(state = state, onAction = onAction)
        exportBar(state = state, onExport = onExport)
        messageHeaderRow()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(items = messages, key = { messageId(it) }) { message ->
                messageRow(
                    message = message,
                    selected = state.selectedMessageId == messageId(message),
                    onClick = { onAction(MainUiAction.SelectMessage(messageId(message))) },
                )
            }
        }
    }
}

@Composable
private fun exportBar(
    state: MainUiState,
    onExport: (MessageExportFormat, Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { onExport(MessageExportFormat.JSONL, false) }) {
            Text("Export JSONL")
        }
        TextButton(onClick = { onExport(MessageExportFormat.CSV, false) }) {
            Text("Export CSV")
        }
        TextButton(
            onClick = { onExport(MessageExportFormat.JSONL, true) },
            enabled = state.selectedMessageId != null,
        ) {
            Text("Export Selected")
        }

        Spacer(modifier = Modifier.weight(1f))

        if (state.exportStatus != null) {
            Text(
                text = state.exportStatus,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun filterBar(
    state: MainUiState,
    onAction: (MainUiAction) -> Unit,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = state.filters.globalSearch,
            onValueChange = { onAction(MainUiAction.SetGlobalSearch(it)) },
            label = { Text("Search") },
            singleLine = true,
            modifier = Modifier.width(180.dp),
        )
        OutlinedTextField(
            value = state.filters.keySearch,
            onValueChange = { onAction(MainUiAction.SetKeySearch(it)) },
            label = { Text("Key contains") },
            singleLine = true,
            modifier = Modifier.width(170.dp),
        )
        OutlinedTextField(
            value = state.filters.valueSearch,
            onValueChange = { onAction(MainUiAction.SetValueSearch(it)) },
            label = { Text("Value contains") },
            singleLine = true,
            modifier = Modifier.width(170.dp),
        )
        OutlinedTextField(
            value = state.filters.partitionFilter,
            onValueChange = { onAction(MainUiAction.SetPartitionFilter(it)) },
            label = { Text("Partition") },
            singleLine = true,
            modifier = Modifier.width(120.dp),
        )
        TextButton(
            onClick = {
                onAction(MainUiAction.SetGlobalSearch(""))
                onAction(MainUiAction.SetKeySearch(""))
                onAction(MainUiAction.SetValueSearch(""))
                onAction(MainUiAction.SetPartitionFilter(""))
            },
        ) {
            Text("Clear")
        }
    }
}

@Composable
private fun messageHeaderRow() {
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            headerCell("Time", 110.dp)
            headerCell("Part", 40.dp)
            headerCell("Offset", 70.dp)
            headerCell("Key", 160.dp)
            headerCell("Value", 260.dp)
            headerCell("Headers", 60.dp)
            headerCell("Size", 60.dp)
        }
    }
}

@Composable
private fun headerCell(
    text: String,
    width: Dp,
) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun messageRow(
    message: ConsumedMessage,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = background,
        tonalElevation = if (selected) 2.dp else 0.dp,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            cellText(formatTimestamp(message.timestamp), 110.dp)
            cellText(message.partition.toString(), 40.dp)
            cellText(message.offset.toString(), 70.dp)
            cellText(previewBytes(message.key), 160.dp)
            cellText(previewBytes(message.value), 260.dp)
            cellText(message.headers.size.toString(), 60.dp)
            val size = (message.key?.size ?: 0) + (message.value?.size ?: 0)
            cellText("${size}b", 60.dp)
        }
    }
}

@Composable
private fun cellText(
    text: String,
    width: Dp,
) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
    )
}
