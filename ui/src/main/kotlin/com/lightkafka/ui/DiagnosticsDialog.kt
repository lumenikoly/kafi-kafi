package com.lightkafka.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.lightkafka.core.storage.SendStatus
import java.util.Locale

@Composable
internal fun diagnosticsDialog(
    state: MainUiState,
    logStore: AppLogStore,
    onAction: (MainUiAction) -> Unit,
) {
    var logEntries by remember(state.isDiagnosticsOpen) { mutableStateOf(logStore.readRecent(limit = 250)) }
    val lastError = state.history.firstOrNull { it.status == SendStatus.FAILURE }?.errorMessage ?: "None"
    val consumerStatus = if (state.isConsumerPaused) "Paused" else "Running"
    val messageRate = formatMessagesPerSecond(state)

    Dialog(onDismissRequest = { onAction(MainUiAction.SetDiagnosticsOpen(false)) }) {
        Surface(
            modifier = Modifier.width(900.dp).height(640.dp),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Diagnostics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    diagnosticsMetricCard(label = "Consumer", value = consumerStatus, modifier = Modifier.weight(1f))
                    diagnosticsMetricCard(label = "Messages/sec", value = messageRate, modifier = Modifier.weight(1f))
                    diagnosticsMetricCard(label = "Last error", value = lastError, modifier = Modifier.weight(1f))
                }

                Text(
                    text = "Log file: ${logStore.logFile}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { logEntries = logStore.readRecent(limit = 250) }) {
                        Text("Refresh")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { onAction(MainUiAction.SetDiagnosticsOpen(false)) }) {
                        Text("Close")
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(logEntries) { entry ->
                        Surface(
                            tonalElevation = 1.dp,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text =
                                    "${formatTimestamp(entry.timestampEpochMillis, includeDate = true)} " +
                                        "[${entry.level}] ${entry.message}",
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun diagnosticsMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun formatMessagesPerSecond(state: MainUiState): String {
    if (state.messages.size < 2) {
        return "0.0"
    }
    val minTimestamp = state.messages.minOfOrNull { it.timestamp } ?: return "0.0"
    val maxTimestamp = state.messages.maxOfOrNull { it.timestamp } ?: return "0.0"
    val durationMs = (maxTimestamp - minTimestamp).coerceAtLeast(1)
    val rate = state.messages.size * 1_000.0 / durationMs
    return String.format(Locale.US, "%.1f", rate)
}
