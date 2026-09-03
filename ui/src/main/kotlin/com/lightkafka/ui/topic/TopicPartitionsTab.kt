package com.lightkafka.ui.topic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lightkafka.core.kafka.KafkaResult
import com.lightkafka.core.kafka.PartitionDetail
import com.lightkafka.ui.infra.AccentViolet
import com.lightkafka.ui.infra.StatusError
import com.lightkafka.ui.infra.SurfaceCard
import com.lightkafka.ui.infra.TextMuted
import com.lightkafka.ui.infra.TextPrimary
import com.lightkafka.ui.infra.TextSecondary
import com.lightkafka.ui.infra.formatKafkaError
import kotlinx.coroutines.CancellationException

// ── Main Entry ─────────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
fun TopicPartitionsTab(
    adminService: com.lightkafka.core.kafka.KafkaAdminService,
    topicName: String,
    modifier: Modifier = Modifier,
) {
    var partitions by remember { mutableStateOf<List<PartitionDetail>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(topicName) {
        isLoading = true
        errorMessage = null
        try {
            when (val result = adminService.getPartitionDetails(topicName)) {
                is KafkaResult.Success -> partitions = result.value
                is KafkaResult.Failure -> errorMessage = formatKafkaError(result.error)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (e: Exception) {
            errorMessage = e.message ?: "Unexpected error"
        } finally {
            isLoading = false
        }
    }

    when {
        isLoading -> PartitionsLoading(modifier = modifier)
        errorMessage != null -> PartitionsError(error = errorMessage!!, modifier = modifier)
        partitions != null -> {
            if (partitions!!.isEmpty()) {
                PartitionsEmpty(modifier = modifier)
            } else {
                PartitionsTable(partitions = partitions!!, modifier = modifier)
            }
        }
    }
}

// ── Partition Table ────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun PartitionsTable(
    partitions: List<PartitionDetail>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "${partitions.size} partition${if (partitions.size != 1) "s" else ""}",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )

        // Header row
        PartitionHeaderRow()

        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(partitions, key = { it.partition }) { partition ->
                PartitionRow(partition = partition)
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun PartitionHeaderRow(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HeaderCell("Partition", weight = 0.12f)
        HeaderCell("Leader", weight = 0.12f)
        HeaderCell("Replicas", weight = 0.2f)
        HeaderCell("ISR", weight = 0.2f)
        HeaderCell("Start Offset", weight = 0.18f)
        HeaderCell("End Offset", weight = 0.18f)
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun RowScope.HeaderCell(
    text: String,
    weight: Float,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = TextMuted,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.weight(weight),
    )
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun PartitionRow(
    partition: PartitionDetail,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(SurfaceCard, MaterialTheme.shapes.small)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DataCell("${partition.partition}", weight = 0.12f, primary = true)
        DataCell(partition.leader?.toString() ?: "none", weight = 0.12f)
        DataCell(partition.replicas.joinToString(", "), weight = 0.2f)
        DataCell(partition.inSyncReplicas.joinToString(", "), weight = 0.2f)
        DataCell(partition.beginningOffset?.toString() ?: "—", weight = 0.18f)
        DataCell(partition.endOffset?.toString() ?: "—", weight = 0.18f)
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun RowScope.DataCell(
    text: String,
    weight: Float,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (primary) TextPrimary else TextSecondary,
        fontWeight = if (primary) FontWeight.Medium else FontWeight.Normal,
        modifier = modifier.weight(weight),
    )
}

// ── Loading State ──────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun PartitionsLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = AccentViolet,
            )
            Text("Loading partitions…", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
    }
}

// ── Error State ────────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun PartitionsError(
    error: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Failed to load partitions",
                style = MaterialTheme.typography.bodyLarge,
                color = StatusError,
            )
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}

// ── Empty State ────────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun PartitionsEmpty(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "No partition information available",
            style = MaterialTheme.typography.bodyLarge,
            color = TextMuted,
        )
    }
}
