package com.lightkafka.ui.broker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lightkafka.core.kafka.BrokerInfo
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

// ── Enrichment data built client-side ──────────────────────────────────────

/**
 * Pre-computed enrichment: maps a broker id to the partitions it leads
 * and the unique topic names among those partitions.
 */
private data class BrokerEnrichment(
    val partitions: List<BrokerPartitionInfo>,
    val topics: List<String>,
)

/**
 * Partition assignment derived from [TopicDescription.partitions].
 * The leader is a broker id (string).
 */
internal data class BrokerPartitionInfo(
    val topicName: String,
    val partition: Int,
    val replicas: List<String>,
)

// ── Data Fetching ──────────────────────────────────────────────────────────

private data class BrokerFetchResult(
    val cluster: ClusterDescription?,
    val enrichment: Map<String, BrokerEnrichment>,
)

/**
 * Fetches cluster description and builds a brokerId → partition/topic enrichment map
 * in a single batch. Topic describe failures are skipped gracefully.
 */
private suspend fun fetchBrokerData(adminService: com.lightkafka.core.kafka.KafkaAdminService): BrokerFetchResult {
    // 1. Describe cluster → broker list
    val clusterResult = adminService.describeCluster()
    if (clusterResult is KafkaResult.Failure) {
        error(clusterResult.error.toString())
    }
    val cluster =
        (clusterResult as KafkaResult.Success).value
            ?: error("Cluster description returned null")

    // 2. List all topics
    val topicsResult = adminService.listTopics(includeInternal = true)
    if (topicsResult is KafkaResult.Failure) {
        // Non-fatal: show brokers without enrichment
        return BrokerFetchResult(cluster, emptyMap())
    }
    val topics = (topicsResult as KafkaResult.Success).value

    // 3. Batch describeTopic for every topic → build broker→partitions map
    val brokerPartitions = mutableMapOf<String, MutableList<BrokerPartitionInfo>>()
    for (topic in topics) {
        val descResult = adminService.describeTopic(topic.name)
        if (descResult !is KafkaResult.Success) continue
        val desc = descResult.value
        for (p in desc.partitions) {
            val leader = p.leader ?: continue
            brokerPartitions
                .getOrPut(leader) { mutableListOf() }
                .add(BrokerPartitionInfo(topicName = desc.name, partition = p.partition, replicas = p.replicas))
        }
    }

    // 4. Build enrichment map: brokerId → (partitions, unique topic names)
    val enrichment =
        brokerPartitions.mapValues { (_, parts) ->
            BrokerEnrichment(
                partitions = parts.sortedWith(compareBy({ it.topicName }, { it.partition })),
                topics = parts.map { it.topicName }.distinct().sorted(),
            )
        }

    return BrokerFetchResult(cluster, enrichment)
}

// ── Main Entry ─────────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
fun BrokersPage(
    connectionStateFlow: MutableStateFlow<AppConnectionState>,
    shellStore: Store<*, ShellAction>,
    modifier: Modifier = Modifier,
) {
    val connectionState by connectionStateFlow.collectAsState()
    val adminService = connectionState.activeAdminService

    if (adminService == null || connectionState.connectionStatus?.state != ConnectionState.CONNECTED) {
        BrokersDisconnected(
            onOpenConnections = { shellStore.dispatch(ShellAction.OpenTab(TabType.CONNECTIONS)) },
            modifier = modifier,
        )
        return
    }

    var clusterDescription by remember { mutableStateOf<ClusterDescription?>(null) }
    var enrichment by remember { mutableStateOf<Map<String, BrokerEnrichment>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableStateOf(0) }

    LaunchedEffect(connectionState.activeProfile?.id, retryKey) {
        isLoading = true
        errorMessage = null
        try {
            val (cluster, enrich) = fetchBrokerData(adminService)
            clusterDescription = cluster
            enrichment = enrich
        } catch (e: Exception) {
            errorMessage = e.message ?: "Unexpected error"
        } finally {
            isLoading = false
        }
    }

    when {
        isLoading -> BrokersLoading(modifier = modifier)
        errorMessage != null ->
            BrokersError(
                error = errorMessage!!,
                onRetry = { retryKey++ },
                modifier = modifier,
            )
        clusterDescription != null ->
            BrokersConnected(
                brokers = clusterDescription!!.brokers,
                controllerId = clusterDescription!!.controllerId,
                enrichment = enrichment,
                modifier = modifier,
            )
    }
}

// ── Connected State ────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun BrokersConnected(
    brokers: List<BrokerInfo>,
    controllerId: String?,
    enrichment: Map<String, BrokerEnrichment>,
    modifier: Modifier = Modifier,
) {
    var selectedBrokerId by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // Header
        Text(
            "Brokers",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "${brokers.size} broker${if (brokers.size != 1) "s" else ""}",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )

        HorizontalDivider(
            color = BorderSubtle,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        if (brokers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No brokers found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextMuted,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(brokers, key = { it.id }) { broker ->
                    val isSelected = selectedBrokerId == broker.id
                    BrokerRow(
                        broker = broker,
                        isController = broker.id == controllerId,
                        isSelected = isSelected,
                        onClick = {
                            selectedBrokerId = if (isSelected) null else broker.id
                        },
                    )
                    // Inline detail card when selected
                    if (isSelected) {
                        val data = enrichment[broker.id]
                        BrokerDetailCard(
                            brokerId = broker.id,
                            partitions = data?.partitions ?: emptyList(),
                            topics = data?.topics ?: emptyList(),
                        )
                    }
                }
            }
        }
    }
}

// ── Broker Row ─────────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun BrokerRow(
    broker: BrokerInfo,
    isController: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor =
        when {
            isSelected -> SurfaceElevated
            else -> SurfaceCard
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(bgColor, MaterialTheme.shapes.small)
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            // Broker ID
            Text(
                "Broker ${broker.id}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
            )

            // Host:port
            val hostPort =
                buildString {
                    append(broker.host ?: "unknown")
                    if (broker.port != null) append(":${broker.port}")
                }
            Text(
                hostPort,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }

        // Controller badge
        if (isController) {
            ControllerBadge()
        }
    }
}

// ── Controller Badge ───────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun ControllerBadge(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .background(StatusSuccessBg, MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            "controller",
            style = MaterialTheme.typography.labelSmall,
            color = StatusSuccess,
        )
    }
}

// ── Disconnected State ─────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun BrokersDisconnected(
    onOpenConnections: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Connect to a cluster to view brokers",
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
private fun BrokersLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = AccentViolet,
            )
            Text("Loading brokers…", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
    }
}

// ── Error State ────────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun BrokersError(
    error: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Failed to load brokers",
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
