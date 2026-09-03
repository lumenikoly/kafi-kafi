package com.lightkafka.ui.broker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lightkafka.ui.infra.AccentCyan
import com.lightkafka.ui.infra.BorderSubtle
import com.lightkafka.ui.infra.SurfaceCard
import com.lightkafka.ui.infra.SurfaceElevated
import com.lightkafka.ui.infra.TextMuted
import com.lightkafka.ui.infra.TextPrimary
import com.lightkafka.ui.infra.TextSecondary

// ── Broker Detail Card ─────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun BrokerDetailCard(
    brokerId: String,
    partitions: List<BrokerPartitionInfo>,
    topics: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(SurfaceElevated, MaterialTheme.shapes.medium)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PartitionLeadershipSection(brokerId, partitions)
        HorizontalDivider(color = BorderSubtle)
        RelatedTopicsSection(topics)
    }
}

// ── Partition Leadership Section ───────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun PartitionLeadershipSection(
    brokerId: String,
    partitions: List<BrokerPartitionInfo>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Partition Leadership", style = MaterialTheme.typography.labelLarge, color = TextSecondary)

        if (partitions.isEmpty()) {
            Text(
                "No partition leadership found for broker $brokerId",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        } else {
            Text(
                "${partitions.size} partition${if (partitions.size != 1) "s" else ""} led by this broker",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            PartitionList(partitions)
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun PartitionList(
    partitions: List<BrokerPartitionInfo>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (p in partitions.take(50)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    p.topicName,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text("p${p.partition}", style = MaterialTheme.typography.bodySmall, color = AccentCyan)
                Text("replicas: ${p.replicas.size}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
        if (partitions.size > 50) {
            Text(
                "…and ${partitions.size - 50} more",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

// ── Related Topics Section ─────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun RelatedTopicsSection(
    topics: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Related Topics", style = MaterialTheme.typography.labelLarge, color = TextSecondary)

        if (topics.isEmpty()) {
            Text("No related topics", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        } else {
            Text(
                "${topics.size} topic${if (topics.size != 1) "s" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            TopicChips(topics)
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun TopicChips(
    topics: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (topic in topics.take(20)) {
                Box(
                    modifier =
                        Modifier
                            .background(SurfaceCard, MaterialTheme.shapes.extraSmall)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(topic, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                }
            }
        }
        if (topics.size > 20) {
            Text(
                "…and ${topics.size - 20} more",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
    }
}
