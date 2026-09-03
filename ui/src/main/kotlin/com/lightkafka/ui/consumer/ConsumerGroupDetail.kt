package com.lightkafka.ui.consumer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lightkafka.core.kafka.ConsumerGroupDetail
import com.lightkafka.core.kafka.ConsumerGroupMemberInfo
import com.lightkafka.core.kafka.PartitionLagInfo
import com.lightkafka.ui.infra.BorderSubtle
import com.lightkafka.ui.infra.StatusError
import com.lightkafka.ui.infra.StatusSuccess
import com.lightkafka.ui.infra.StatusWarning
import com.lightkafka.ui.infra.SurfaceCard
import com.lightkafka.ui.infra.SurfaceElevated
import com.lightkafka.ui.infra.TextMuted
import com.lightkafka.ui.infra.TextPrimary
import com.lightkafka.ui.infra.TextSecondary

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun GroupDetail(
    detail: ConsumerGroupDetail?,
    selectedGroupId: String?,
    isLoading: Boolean,
    error: String?,
    actionMessage: String?,
    actionIsError: Boolean,
    actionInProgress: Boolean,
    onRetry: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        selectedGroupId == null -> EmptyDetail("Select a consumer group", modifier)
        isLoading -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        error != null -> Box(modifier, contentAlignment = Alignment.Center) { InlineError(error, onRetry) }
        detail != null ->
            GroupDetailContent(
                detail = detail,
                actionMessage = actionMessage,
                actionIsError = actionIsError,
                actionInProgress = actionInProgress,
                onReset = onReset,
                onDelete = onDelete,
                modifier = modifier,
            )
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun GroupDetailContent(
    detail: ConsumerGroupDetail,
    actionMessage: String?,
    actionIsError: Boolean,
    actionInProgress: Boolean,
    onReset: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalLag = detail.partitionLags.sumOf { it.lag ?: 0 }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        detail.groupId,
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StateLabel(detail.state)
                        Text(
                            "${detail.members.size} members",
                            color = TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text("$totalLag total lag", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onReset, enabled = !actionInProgress) { Text("Reset offsets") }
                    OutlinedButton(
                        onClick = onDelete,
                        enabled = !actionInProgress,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
                    ) { Text("Delete group") }
                }
            }
            actionMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (actionIsError) StatusError else StatusSuccess,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(top = 16.dp))
        }

        item { SectionTitle("Partition lag", "${detail.partitionLags.size} committed partitions") }
        if (detail.partitionLags.isEmpty()) {
            item { EmptySection("This group has no committed offsets") }
        } else {
            item { LagHeader() }
            items(detail.partitionLags, key = { "${it.topic}:${it.partition}" }) { lag -> LagRow(lag) }
        }

        item { SectionTitle("Members", "${detail.members.size} active") }
        if (detail.members.isEmpty()) {
            item { EmptySection("No active members") }
        } else {
            items(detail.members, key = ConsumerGroupMemberInfo::memberId) { member -> MemberRow(member) }
        }
        item { Spacer(Modifier.size(8.dp)) }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun SectionTitle(
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun LagHeader() {
    Row(Modifier.fillMaxWidth().background(SurfaceElevated).padding(horizontal = 24.dp, vertical = 8.dp)) {
        TableText("Topic", Modifier.weight(1f), header = true)
        TableText("Partition", Modifier.width(80.dp), header = true)
        TableText("Current", Modifier.width(100.dp), header = true)
        TableText("End", Modifier.width(100.dp), header = true)
        TableText("Lag", Modifier.width(100.dp), header = true)
        TableText("Member", Modifier.weight(1f), header = true)
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun LagRow(lag: PartitionLagInfo) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TableText(lag.topic, Modifier.weight(1f))
        TableNumber(lag.partition.toString(), Modifier.width(80.dp))
        TableNumber(lag.currentOffset?.toString() ?: "—", Modifier.width(100.dp))
        TableNumber(lag.endOffset?.toString() ?: "—", Modifier.width(100.dp))
        TableNumber(lag.lag?.toString() ?: "—", Modifier.width(100.dp), warning = (lag.lag ?: 0) > 0)
        TableText(lag.memberId ?: "unassigned", Modifier.weight(1f))
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun TableText(
    value: String,
    modifier: Modifier,
    header: Boolean = false,
) {
    Text(
        value,
        style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
        color = if (header) TextMuted else TextSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(end = 8.dp),
    )
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun TableNumber(
    value: String,
    modifier: Modifier,
    warning: Boolean = false,
) {
    Text(
        value,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = if (warning) StatusWarning else TextSecondary,
        modifier = modifier,
    )
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun MemberRow(member: ConsumerGroupMemberInfo) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        color = SurfaceCard,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                member.clientId ?: member.memberId,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(member.clientHost ?: "Unknown host", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            val assignments = member.assignments.joinToString { "${it.topic}[${it.partition}]" }
            Text(
                assignments.ifBlank { "No partition assignments" },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun EmptyDetail(
    message: String,
    modifier: Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = TextMuted)
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun EmptySection(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = TextMuted,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}
