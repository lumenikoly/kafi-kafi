package com.lightkafka.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.lightkafka.core.kafka.ConsumerGroupSummary
import com.lightkafka.core.kafka.ConsumerGroupDetail
import com.lightkafka.core.kafka.PartitionLagInfo

data class ConsumerGroupsState(
    val groups: List<ConsumerGroupSummary> = emptyList(),
    val selectedGroupId: String? = null,
    val selectedGroupDetail: ConsumerGroupDetail? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

@Composable
fun consumerGroupsPane(
    state: ConsumerGroupsState,
    onRefresh: () -> Unit,
    onSelectGroup: (String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var groupToDelete by remember { mutableStateOf<String?>(null) }
    var showDetailDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.background(SidebarBackgroundColor),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Consumer Groups",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BorderSubtle),
        )

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Loading consumer groups...", color = TextMuted)
            }
        } else if (state.error != null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Error: ${state.error}",
                    color = StatusError,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else if (state.groups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No consumer groups found",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.groups) { group ->
                    consumerGroupItem(
                        group = group,
                        selected = group.groupId == state.selectedGroupId,
                        onClick = {
                            onSelectGroup(group.groupId)
                            showDetailDialog = true
                        },
                        onDelete = { groupToDelete = group.groupId },
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    groupToDelete?.let { groupId ->
        AlertDialog(
            onDismissRequest = { groupToDelete = null },
            containerColor = SurfaceCard,
            title = {
                Text(
                    text = "Delete Consumer Group?",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Column {
                    Text(
                        text = "Are you sure you want to delete consumer group \"$groupId\"?",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This action cannot be undone.",
                        color = StatusError,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteGroup(groupId)
                        groupToDelete = null
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = StatusError,
                            contentColor = Color.White,
                        ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { groupToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
        )
    }

    // Group detail dialog
    if (showDetailDialog && state.selectedGroupDetail != null) {
        consumerGroupDetailDialog(
            detail = state.selectedGroupDetail!!,
            onDismiss = { showDetailDialog = false },
        )
    }
}

@Composable
private fun consumerGroupItem(
    group: ConsumerGroupSummary,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = if (selected) AccentViolet.copy(alpha = 0.15f) else SurfaceElevated,
        shape = RoundedCornerShape(10.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.groupId,
                    color = if (selected) AccentViolet else TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
                group.state?.let { state ->
                    Text(
                        text = state,
                        color = getStateColor(state),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun consumerGroupDetailDialog(
    detail: ConsumerGroupDetail,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Text(
                text = detail.groupId,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                // State and members info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Surface(
                        color = SurfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "State",
                                color = TextMuted,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(
                                text = detail.state,
                                color = getStateColor(detail.state),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    Surface(
                        color = SurfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Members",
                                color = TextMuted,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(
                                text = detail.members.size.toString(),
                                color = TextPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    Surface(
                        color = SurfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Partitions",
                                color = TextMuted,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(
                                text = detail.partitionLags.size.toString(),
                                color = TextPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Partition lags
                Text(
                    text = "Partition Lag",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(detail.partitionLags) { lag ->
                        partitionLagRow(lag)
                    }
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
private fun partitionLagRow(lag: PartitionLagInfo) {
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = lag.topic,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
                Text(
                    text = "Partition ${lag.partition}",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Lag",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
                val lagValue = lag.lag
                Text(
                    text = lagValue?.toString() ?: "?",
                    color = when {
                        lagValue == null -> TextMuted
                        lagValue > 1000 -> StatusError
                        lagValue > 100 -> StatusWarning
                        else -> StatusSuccess
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Current",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = lag.currentOffset?.toString() ?: "?",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "End",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = lag.endOffset?.toString() ?: "?",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun getStateColor(state: String): Color =
    when (state.uppercase()) {
        "STABLE" -> StatusSuccess
        "EMPTY" -> TextMuted
        "DEAD" -> StatusError
        "PREPARING_REBALANCE", "COMPLETING_REBALANCE" -> StatusWarning
        else -> TextSecondary
    }
