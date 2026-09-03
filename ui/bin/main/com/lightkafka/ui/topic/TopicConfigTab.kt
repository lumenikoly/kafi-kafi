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
import com.lightkafka.core.kafka.TopicConfig
import com.lightkafka.core.kafka.TopicConfigEntry
import com.lightkafka.ui.infra.AccentViolet
import com.lightkafka.ui.infra.StatusError
import com.lightkafka.ui.infra.SurfaceCard
import com.lightkafka.ui.infra.SurfaceElevated
import com.lightkafka.ui.infra.TextMuted
import com.lightkafka.ui.infra.TextPrimary
import com.lightkafka.ui.infra.TextSecondary
import com.lightkafka.ui.infra.formatKafkaError
import kotlinx.coroutines.CancellationException

// ── Main Entry ─────────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
fun TopicConfigTab(
    adminService: com.lightkafka.core.kafka.KafkaAdminService,
    topicName: String,
    modifier: Modifier = Modifier,
) {
    var config by remember { mutableStateOf<TopicConfig?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(topicName) {
        isLoading = true
        errorMessage = null
        try {
            when (val result = adminService.getTopicConfig(topicName)) {
                is KafkaResult.Success -> config = result.value
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
        isLoading -> ConfigLoading(modifier = modifier)
        errorMessage != null -> ConfigError(error = errorMessage!!, modifier = modifier)
        config != null -> {
            if (config!!.entries.isEmpty()) {
                ConfigEmpty(modifier = modifier)
            } else {
                ConfigTable(entries = config!!.entries, modifier = modifier)
            }
        }
    }
}

// ── Config Table ───────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun ConfigTable(
    entries: List<TopicConfigEntry>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "${entries.size} config ${if (entries.size != 1) "entries" else "entry"}",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )

        // Header row
        ConfigHeaderRow()

        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(entries, key = { it.name }) { entry ->
                ConfigRow(entry = entry)
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun ConfigHeaderRow(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ConfigHeaderCell("Name", weight = 0.35f)
        ConfigHeaderCell("Value", weight = 0.4f)
        ConfigHeaderCell("Flags", weight = 0.25f)
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun RowScope.ConfigHeaderCell(
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
private fun ConfigRow(
    entry: TopicConfigEntry,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(SurfaceCard, MaterialTheme.shapes.small)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            entry.name,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.35f),
        )
        Text(
            if (entry.isSensitive) "••••••••" else entry.value,
            style = MaterialTheme.typography.bodySmall,
            color = if (entry.isSensitive) TextMuted else TextSecondary,
            modifier = Modifier.weight(0.4f),
        )
        // Flags column — badges
        Row(
            modifier = Modifier.weight(0.25f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (entry.isDefault) ConfigBadge("default")
            if (entry.isReadOnly) ConfigBadge("read-only")
            if (entry.isSensitive) ConfigBadge("sensitive")
        }
    }
}

// ── Config Badge ───────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun ConfigBadge(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .background(SurfaceElevated, MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
        )
    }
}

// ── Loading State ──────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun ConfigLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = AccentViolet,
            )
            Text("Loading config…", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
    }
}

// ── Error State ────────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun ConfigError(
    error: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Failed to load config",
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
private fun ConfigEmpty(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "No configuration entries available",
            style = MaterialTheme.typography.bodyLarge,
            color = TextMuted,
        )
    }
}
