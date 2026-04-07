package com.lightkafka.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lightkafka.core.kafka.PartitionDetail
import com.lightkafka.core.kafka.TopicConfig
import com.lightkafka.core.kafka.TopicConfigEntry

sealed interface TopicConfigTab {
    data object Config : TopicConfigTab
    data object Partitions : TopicConfigTab
    data object Statistics : TopicConfigTab
}

data class TopicConfigDialogState(
    val topicName: String,
    val config: TopicConfig? = null,
    val partitions: List<PartitionDetail> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@Composable
fun topicConfigDialog(
    state: TopicConfigDialogState,
    onDismiss: () -> Unit,
    onUpdateConfig: (Map<String, String>) -> Unit,
    onAddPartitions: (Int) -> Unit,
) {
    var selectedTab by remember { mutableStateOf<TopicConfigTab>(TopicConfigTab.Config) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = state.topicName,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                if (state.isLoading) {
                    Text(
                        text = "(Loading...)",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                TabRow(
                    selectedTabIndex =
                        when (selectedTab) {
                            TopicConfigTab.Config -> 0
                            TopicConfigTab.Partitions -> 1
                            TopicConfigTab.Statistics -> 2
                        },
                    containerColor = SurfaceElevated,
                    contentColor = TextPrimary,
                    indicator = { tabPositions ->
                        Box(
                            modifier =
                                Modifier
                                    .tabIndicatorOffset(
                                        tabPositions[
                                            when (selectedTab) {
                                                TopicConfigTab.Config -> 0
                                                TopicConfigTab.Partitions -> 1
                                                TopicConfigTab.Statistics -> 2
                                            },
                                        ],
                                    )
                                    .height(3.dp)
                                    .fillMaxWidth()
                                    .background(AccentViolet),
                        )
                    },
                ) {
                    Tab(
                        selected = selectedTab == TopicConfigTab.Config,
                        onClick = { selectedTab = TopicConfigTab.Config },
                        text = {
                            Text(
                                "Configuration",
                                color = if (selectedTab == TopicConfigTab.Config) AccentViolet else TextSecondary,
                            )
                        },
                    )
                    Tab(
                        selected = selectedTab == TopicConfigTab.Partitions,
                        onClick = { selectedTab = TopicConfigTab.Partitions },
                        text = {
                            Text(
                                "Partitions",
                                color = if (selectedTab == TopicConfigTab.Partitions) AccentViolet else TextSecondary,
                            )
                        },
                    )
                    Tab(
                        selected = selectedTab == TopicConfigTab.Statistics,
                        onClick = { selectedTab = TopicConfigTab.Statistics },
                        text = {
                            Text(
                                "Statistics",
                                color = if (selectedTab == TopicConfigTab.Statistics) AccentViolet else TextSecondary,
                            )
                        },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedTab) {
                    TopicConfigTab.Config ->
                        configTab(
                            config = state.config,
                            isLoading = state.isLoading,
                            onUpdateConfig = onUpdateConfig,
                        )
                    TopicConfigTab.Partitions ->
                        partitionsTab(
                            partitions = state.partitions,
                            isLoading = state.isLoading,
                            currentPartitionCount = state.partitions.size,
                            onAddPartitions = onAddPartitions,
                        )
                    TopicConfigTab.Statistics ->
                        statisticsTab(
                            partitions = state.partitions,
                            isLoading = state.isLoading,
                        )
                }

                state.error?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = StatusError,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        },
    )
}

@Composable
private fun configTab(
    config: TopicConfig?,
    isLoading: Boolean,
    onUpdateConfig: (Map<String, String>) -> Unit,
) {
    var isEditing by remember { mutableStateOf(false) }
    val editedConfigs = remember { mutableStateListOf<Pair<String, String>>() }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading configuration...", color = TextMuted)
        }
    } else if (config == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No configuration available", color = TextMuted)
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${config.entries.size} configuration entries",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!isEditing) {
                    Surface(
                        color = AccentViolet.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { isEditing = true },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit",
                                tint = AccentViolet,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "Edit",
                                color = AccentViolet,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(config.entries) { entry ->
                    ConfigEntryRow(
                        entry = entry,
                        isEditing = isEditing,
                        onValueChange = { newValue: String ->
                            val index = editedConfigs.indexOfFirst { it.first == entry.name }
                            if (index >= 0) {
                                editedConfigs[index] = entry.name to newValue
                            } else {
                                editedConfigs.add(entry.name to newValue)
                            }
                        },
                    )
                }
            }

            if (isEditing) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            onUpdateConfig(editedConfigs.associate { it.first to it.second })
                            isEditing = false
                            editedConfigs.clear()
                        },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = AccentViolet,
                                contentColor = Color.White,
                            ),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Save Changes")
                    }
                    TextButton(onClick = { isEditing = false; editedConfigs.clear() }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigEntryRow(
    entry: TopicConfigEntry,
    isEditing: Boolean,
    onValueChange: (String) -> Unit,
) {
    Surface(
        color = SurfaceElevated,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.name,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                    )
                    if (entry.isDefault) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = AccentCyan.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                text = "default",
                                color = AccentCyan,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    if (entry.isReadOnly) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            color = StatusWarning.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                text = "read-only",
                                color = StatusWarning,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                if (isEditing && !entry.isReadOnly) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = entry.value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle =
                            MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary,
                            ),
                    )
                } else {
                    Text(
                        text = if (entry.isSensitive) "********" else entry.value,
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun partitionsTab(
    partitions: List<PartitionDetail>,
    isLoading: Boolean,
    currentPartitionCount: Int,
    onAddPartitions: (Int) -> Unit,
) {
    var showAddPartitionsDialog by remember { mutableStateOf(false) }
    var newPartitionCount by remember { mutableStateOf("") }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading partitions...", color = TextMuted)
        }
    } else if (partitions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No partition information available", color = TextMuted)
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$currentPartitionCount partition${if (currentPartitionCount != 1) "s" else ""}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Surface(
                    color = AccentViolet.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { showAddPartitionsDialog = true },
                ) {
                    Text(
                        text = "Add Partitions",
                        color = AccentViolet,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(partitions) { partition ->
                    PartitionRow(partition)
                }
            }
        }
    }

    if (showAddPartitionsDialog) {
        AlertDialog(
            onDismissRequest = { showAddPartitionsDialog = false },
            containerColor = SurfaceCard,
            title = {
                Text("Add Partitions", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column {
                    Text(
                        text = "Current partitions: $currentPartitionCount",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newPartitionCount,
                        onValueChange = { newPartitionCount = it.filter { c -> c.isDigit() } },
                        singleLine = true,
                        label = { Text("New total partition count") },
                        placeholder = { Text("Must be greater than $currentPartitionCount") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = outlinedTextFieldColorsDialog(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val count = newPartitionCount.toIntOrNull()
                        if (count != null && count > currentPartitionCount) {
                            onAddPartitions(count)
                            showAddPartitionsDialog = false
                        }
                    },
                    enabled = (newPartitionCount.toIntOrNull() ?: 0) > currentPartitionCount,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = AccentViolet,
                            contentColor = Color.White,
                        ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPartitionsDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
        )
    }
}

@Composable
private fun PartitionRow(partition: PartitionDetail) {
    Surface(
        color = SurfaceElevated,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Partition number badge
            Surface(
                color = partitionTint(partition.partition),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    text = "P${partition.partition}",
                    color = partitionTextColor(partition.partition),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            // Leader
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Leader",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = partition.leader?.toString() ?: "None",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // Replicas
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Replicas",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = partition.replicas.joinToString(", "),
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // ISR
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "In-Sync",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = partition.inSyncReplicas.joinToString(", "),
                    color = StatusSuccess,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // Offsets
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Offsets",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
                val begin = partition.beginningOffset?.toString() ?: "?"
                val end = partition.endOffset?.toString() ?: "?"
                val messages: String =
                    if (partition.beginningOffset != null && partition.endOffset != null) {
                        val count = partition.endOffset!! - partition.beginningOffset!!
                        " ($count msgs)"
                    } else {
                        ""
                    }
                Text(
                    text = "$begin - $end$messages",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun statisticsTab(
    partitions: List<PartitionDetail>,
    isLoading: Boolean,
) {
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading statistics...", color = TextMuted)
        }
    } else if (partitions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No statistics available", color = TextMuted)
        }
    } else {
        val totalMessages = partitions.sumOf {
            val begin = it.beginningOffset ?: 0L
            val end = it.endOffset ?: 0L
            end - begin
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Summary stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StatCard(
                    title = "Partitions",
                    value = partitions.size.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    title = "Total Messages",
                    value = formatNumber(totalMessages),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    title = "Avg Messages/Partition",
                    value = if (partitions.isNotEmpty()) formatNumber(totalMessages / partitions.size) else "0",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Per-partition breakdown
            Text(
                text = "Partition Details",
                color = TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(partitions) { partition ->
                    val beginOffset = partition.beginningOffset
                    val endOffset = partition.endOffset
                    val messages: Long =
                        if (beginOffset != null && endOffset != null) {
                            endOffset - beginOffset
                        } else {
                            0L
                        }

                    Surface(
                        color = SurfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Surface(
                                color = partitionTint(partition.partition),
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(
                                    text = "${partition.partition}",
                                    color = partitionTextColor(partition.partition),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }

                            Text(
                                text = "${formatNumber(messages)} messages",
                                color = TextPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            val begin = partition.beginningOffset?.toString() ?: "?"
                            val end = partition.endOffset?.toString() ?: "?"
                            Text(
                                text = "[$begin - $end]",
                                color = TextMuted,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = SurfaceElevated,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                color = TextMuted,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun formatNumber(n: Long): String {
    if (n < 1000) return n.toString()
    if (n < 1_000_000) return "${n / 1000}K"
    if (n < 1_000_000_000) return "${n / 1_000_000}M"
    return "${n / 1_000_000_000}B"
}

@Composable
private fun outlinedTextFieldColorsDialog() =
    androidx.compose.material3.OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AccentViolet,
        unfocusedBorderColor = BorderDefault,
        focusedContainerColor = SurfaceElevated,
        unfocusedContainerColor = SurfaceElevated,
        focusedLabelColor = AccentViolet,
        unfocusedLabelColor = TextMuted,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        cursorColor = AccentViolet,
    )
