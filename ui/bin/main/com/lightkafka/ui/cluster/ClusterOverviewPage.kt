package com.lightkafka.ui.cluster

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lightkafka.core.kafka.ClusterDescription
import com.lightkafka.core.kafka.KafkaResult
import com.lightkafka.ui.connection.AppConnectionState
import com.lightkafka.ui.connection.ConnectionState
import com.lightkafka.ui.infra.AccentViolet
import com.lightkafka.ui.infra.BorderSubtle
import com.lightkafka.ui.infra.StatusError
import com.lightkafka.ui.infra.StatusSuccess
import com.lightkafka.ui.infra.StatusSuccessBg
import com.lightkafka.ui.infra.SurfaceCard
import com.lightkafka.ui.infra.SurfaceElevated
import com.lightkafka.ui.infra.TextMuted
import com.lightkafka.ui.infra.TextPrimary
import com.lightkafka.ui.infra.TextSecondary
import com.lightkafka.ui.shell.ShellAction
import com.lightkafka.ui.shell.Store
import com.lightkafka.ui.shell.TabType
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Bundles the data needed by [ConnectedState] to stay under
 * detekt's LongParameterList threshold (8 parameters).
 */
private data class ClusterData(
    val connectionName: String,
    val latencyMs: Long,
    val clusterId: String?,
    val brokerCount: Int,
    val topicCount: Int,
)

@Suppress("ktlint:standard:function-naming")
@Composable
fun ClusterOverviewPage(
    connectionStateFlow: MutableStateFlow<AppConnectionState>,
    shellStore: Store<*, ShellAction>,
    modifier: Modifier = Modifier,
) {
    val connectionState by connectionStateFlow.collectAsState()
    val activeProfile = connectionState.activeProfile
    val adminService = connectionState.activeAdminService

    if (adminService == null || connectionState.connectionStatus?.state != ConnectionState.CONNECTED) {
        DisconnectedState(
            onOpenConnections = {
                shellStore.dispatch(ShellAction.OpenTab(TabType.CONNECTIONS))
            },
            modifier = modifier,
        )
        return
    }

    // Connected — fetch cluster data
    var clusterDescription by remember { mutableStateOf<ClusterDescription?>(null) }
    var topicCount by remember { mutableStateOf(-1) } // -1 = not loaded yet
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableStateOf(0) }

    LaunchedEffect(activeProfile?.id, retryKey) {
        isLoading = true
        errorMessage = null
        try {
            val clusterResult = adminService.describeCluster()
            val topicsResult = adminService.listTopics()

            when {
                clusterResult is KafkaResult.Failure -> {
                    errorMessage = clusterResult.error.toString()
                }
                topicsResult is KafkaResult.Failure -> {
                    errorMessage = topicsResult.error.toString()
                }
                else -> {
                    clusterDescription = (clusterResult as KafkaResult.Success).value
                    topicCount = (topicsResult as KafkaResult.Success).value.size
                }
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: "Unexpected error"
        } finally {
            isLoading = false
        }
    }

    if (isLoading) {
        LoadingState(modifier = modifier)
        return
    }

    if (errorMessage != null) {
        ErrorState(
            error = errorMessage!!,
            onRetry = { retryKey++ },
            modifier = modifier,
        )
        return
    }

    val data =
        ClusterData(
            connectionName = activeProfile?.name ?: "Unknown",
            latencyMs = connectionState.connectionStatus?.latencyMs ?: 0,
            clusterId = clusterDescription?.clusterId,
            brokerCount = clusterDescription?.brokers?.size ?: 0,
            topicCount = topicCount.coerceAtLeast(0),
        )

    ConnectedState(
        data = data,
        onViewBrokers = { shellStore.dispatch(ShellAction.OpenTab(TabType.BROKERS)) },
        onViewTopics = { shellStore.dispatch(ShellAction.OpenTab(TabType.TOPICS)) },
        modifier = modifier,
    )
}

// ── Connected State ────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun ConnectedState(
    data: ClusterData,
    onViewBrokers: () -> Unit,
    onViewTopics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Header row: connection name + status badge + latency
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    data.connectionName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                StatusBadge("Connected")
            }
            if (data.latencyMs > 0) {
                Text(
                    "${data.latencyMs}ms",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                )
            }
        }

        // Cluster ID
        if (data.clusterId != null) {
            Text(
                "Cluster ID: ${data.clusterId}",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }

        HorizontalDivider(color = BorderSubtle)

        // Summary cards row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SummaryCard("Brokers", data.brokerCount.toString(), modifier = Modifier.weight(1f))
            SummaryCard("Topics", data.topicCount.toString(), modifier = Modifier.weight(1f))
            SummaryCard("Consumer Groups", "—", modifier = Modifier.weight(1f))
        }

        HorizontalDivider(color = BorderSubtle)

        // Quick actions
        Text(
            "Quick Actions",
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onViewTopics,
                colors = ButtonDefaults.buttonColors(containerColor = AccentViolet, contentColor = TextPrimary),
            ) { Text("View Topics") }
            Button(
                onClick = onViewBrokers,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated, contentColor = TextPrimary),
            ) { Text("View Brokers") }
            OutlinedButton(
                onClick = { /* Topic creation deferred to M002 */ },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
            ) { Text("Create Topic") }
        }
    }
}

// ── Summary Card ───────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun SummaryCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(SurfaceCard, shape = MaterialTheme.shapes.medium)
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ── Status Badge ───────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun StatusBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .background(StatusSuccessBg, shape = MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(StatusSuccess),
        )
        Text(text, style = MaterialTheme.typography.labelMedium, color = StatusSuccess)
    }
}

// ── Disconnected State ─────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun DisconnectedState(
    onOpenConnections: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Connect to a cluster to get started",
                style = MaterialTheme.typography.headlineMedium,
                color = TextSecondary,
            )
            Button(
                onClick = onOpenConnections,
                colors = ButtonDefaults.buttonColors(containerColor = AccentViolet, contentColor = TextPrimary),
            ) { Text("Open Connections") }
        }
    }
}

// ── Loading State ──────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = AccentViolet,
            )
            Text("Loading cluster data…", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
    }
}

// ── Error State ────────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun ErrorState(
    error: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Failed to load cluster data",
                style = MaterialTheme.typography.headlineSmall,
                color = StatusError,
            )
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = StatusError, contentColor = TextPrimary),
            ) { Text("Retry") }
        }
    }
}
