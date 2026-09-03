package com.lightkafka.ui.consumer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lightkafka.core.kafka.ConsumerGroupDetail
import com.lightkafka.core.kafka.OffsetResetSpec
import com.lightkafka.core.kafka.PartitionLagInfo
import com.lightkafka.ui.infra.StatusError
import com.lightkafka.ui.infra.TextPrimary
import com.lightkafka.ui.infra.TextSecondary

private enum class ResetMode(val label: String) {
    EARLIEST("Earliest"),
    LATEST("Latest"),
    OFFSET("Offset"),
    TIMESTAMP("Timestamp"),
}

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun ResetOffsetsDialog(
    group: ConsumerGroupDetail,
    inProgress: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, OffsetResetSpec) -> Unit,
) {
    val knownTopics = remember(group) { group.partitionLags.map(PartitionLagInfo::topic).distinct().sorted() }
    var topic by remember(group.groupId) { mutableStateOf(knownTopics.firstOrNull().orEmpty()) }
    var mode by remember(group.groupId) { mutableStateOf(ResetMode.EARLIEST) }
    var value by remember(group.groupId) { mutableStateOf("") }
    val spec = resetSpec(mode, value)
    val error =
        when {
            topic.isBlank() -> "Topic is required"
            mode in setOf(ResetMode.OFFSET, ResetMode.TIMESTAMP) && spec == null -> "Enter a non-negative number"
            else -> null
        }

    AlertDialog(
        onDismissRequest = { if (!inProgress) onDismiss() },
        title = { Text("Reset offsets for ${group.groupId}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "The new position applies to every partition of the topic. Stop active consumers before resetting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Topic") },
                    supportingText = {
                        if (knownTopics.isNotEmpty()) Text("Known: ${knownTopics.joinToString()}")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResetModeButton(ResetMode.EARLIEST, mode) { mode = it }
                    ResetModeButton(ResetMode.LATEST, mode) { mode = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResetModeButton(ResetMode.OFFSET, mode) { mode = it }
                    ResetModeButton(ResetMode.TIMESTAMP, mode) { mode = it }
                }
                if (mode == ResetMode.OFFSET || mode == ResetMode.TIMESTAMP) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { input -> if (input.all(Char::isDigit)) value = input },
                        label = { Text(if (mode == ResetMode.OFFSET) "Offset" else "Unix timestamp (ms)") },
                        isError = error != null,
                        supportingText = { error?.let { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(topic.trim(), checkNotNull(spec)) },
                enabled = error == null && !inProgress,
            ) { Text(if (inProgress) "Resetting…" else "Reset offsets") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !inProgress) { Text("Cancel") } },
    )
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun ResetModeButton(
    mode: ResetMode,
    selected: ResetMode,
    onSelect: (ResetMode) -> Unit,
) {
    if (mode == selected) {
        Button(onClick = { onSelect(mode) }) { Text(mode.label) }
    } else {
        OutlinedButton(onClick = { onSelect(mode) }) { Text(mode.label) }
    }
}

private fun resetSpec(
    mode: ResetMode,
    value: String,
): OffsetResetSpec? =
    when (mode) {
        ResetMode.EARLIEST -> OffsetResetSpec.Earliest
        ResetMode.LATEST -> OffsetResetSpec.Latest
        ResetMode.OFFSET -> value.toLongOrNull()?.takeIf { it >= 0 }?.let(OffsetResetSpec::Offset)
        ResetMode.TIMESTAMP -> value.toLongOrNull()?.takeIf { it >= 0 }?.let(OffsetResetSpec::Timestamp)
    }

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun DeleteGroupDialog(
    groupId: String,
    inProgress: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!inProgress) onDismiss() },
        title = { Text("Delete consumer group?") },
        text = {
            Text(
                "Delete $groupId and its committed offsets. Kafka rejects deletion while the group has active members.",
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !inProgress,
                colors = ButtonDefaults.buttonColors(containerColor = StatusError, contentColor = TextPrimary),
            ) { Text(if (inProgress) "Deleting…" else "Delete group") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !inProgress) { Text("Cancel") } },
    )
}
