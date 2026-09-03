package com.lightkafka.ui.topic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.lightkafka.core.kafka.KafkaResult
import com.lightkafka.core.kafka.TopicDescription
import com.lightkafka.ui.connection.AppConnectionState
import com.lightkafka.ui.connection.ConnectionState
import com.lightkafka.ui.infra.AccentViolet
import com.lightkafka.ui.infra.BorderSubtle
import com.lightkafka.ui.infra.StatusError
import com.lightkafka.ui.infra.TextMuted
import com.lightkafka.ui.infra.TextPrimary
import com.lightkafka.ui.infra.TextSecondary
import com.lightkafka.ui.infra.formatKafkaError
import com.lightkafka.ui.shell.ShellAction
import com.lightkafka.ui.shell.Store
import com.lightkafka.ui.shell.TabType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

// ── Sub-tab definition ─────────────────────────────────────────────────────

private enum class TopicSubTab(
    val label: String,
) {
    MESSAGES("Messages"),
    PARTITIONS("Partitions"),
    CONFIG("Config"),
}

// ── Main Entry ─────────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
fun TopicDetailPage(
    connectionStateFlow: MutableStateFlow<AppConnectionState>,
    shellStore: Store<*, ShellAction>,
    topicName: String,
    modifier: Modifier = Modifier,
) {
    val connectionState by connectionStateFlow.collectAsState()
    val adminService = connectionState.activeAdminService

    if (adminService == null || connectionState.connectionStatus?.state != ConnectionState.CONNECTED) {
        TopicDetailDisconnected(
            onOpenConnections = { shellStore.dispatch(ShellAction.OpenTab(TabType.CONNECTIONS)) },
            modifier = modifier,
        )
        return
    }

    var topicDescription by remember { mutableStateOf<TopicDescription?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableStateOf(0) }
    var selectedSubTab by remember { mutableStateOf(TopicSubTab.MESSAGES) }

    LaunchedEffect(connectionState.activeProfile?.id, topicName, retryKey) {
        isLoading = true
        errorMessage = null
        try {
            when (val result = adminService.describeTopic(topicName)) {
                is KafkaResult.Success -> topicDescription = result.value
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
        isLoading -> TopicDetailLoading(modifier = modifier)
        errorMessage != null ->
            TopicDetailError(
                error = errorMessage!!,
                onRetry = { retryKey++ },
                modifier = modifier,
            )
        topicDescription != null ->
            TopicDetailContent(
                topicDescription = topicDescription!!,
                adminService = adminService,
                connectionStateFlow = connectionStateFlow,
                selectedSubTab = selectedSubTab,
                onSubTabSelected = { selectedSubTab = it },
                modifier = modifier,
            )
    }
}

// ── Content (header + sub-tabs) ────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun TopicDetailContent(
    topicDescription: TopicDescription,
    adminService: com.lightkafka.core.kafka.KafkaAdminService,
    connectionStateFlow: MutableStateFlow<AppConnectionState>,
    selectedSubTab: TopicSubTab,
    onSubTabSelected: (TopicSubTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Header
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                topicDescription.name,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
            )
            val partitionCount = topicDescription.partitions.size
            val suffix = if (partitionCount != 1) "s" else ""
            val internalTag = if (topicDescription.internal) " · internal" else ""
            Text(
                "$partitionCount partition$suffix$internalTag",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }

        HorizontalDivider(color = BorderSubtle)

        // Sub-tab bar
        SubTabBar(
            selectedSubTab = selectedSubTab,
            onSubTabSelected = onSubTabSelected,
        )

        HorizontalDivider(color = BorderSubtle)

        // Sub-tab content
        when (selectedSubTab) {
            TopicSubTab.MESSAGES ->
                TopicMessagesTab(
                    connectionStateFlow = connectionStateFlow,
                    topicName = topicDescription.name,
                    partitionCount = topicDescription.partitions.size,
                )
            TopicSubTab.PARTITIONS ->
                TopicPartitionsTab(
                    adminService = adminService,
                    topicName = topicDescription.name,
                )
            TopicSubTab.CONFIG ->
                TopicConfigTab(
                    adminService = adminService,
                    topicName = topicDescription.name,
                )
        }
    }
}

// ── Sub-tab bar ────────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun SubTabBar(
    selectedSubTab: TopicSubTab,
    onSubTabSelected: (TopicSubTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        for (tab in TopicSubTab.entries) {
            val isSelected = tab == selectedSubTab
            Column(
                modifier =
                    Modifier
                        .clickable { onSubTabSelected(tab) }
                        .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    tab.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) TextPrimary else TextSecondary,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
                // Underline indicator
                Box(
                    modifier =
                        Modifier
                            .padding(top = 4.dp)
                            .background(
                                if (isSelected) AccentViolet else androidx.compose.ui.graphics.Color.Transparent,
                                MaterialTheme.shapes.extraSmall,
                            ).fillMaxWidth()
                            .height(2.dp),
                )
            }
        }
    }
}

// ── Disconnected State ─────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun TopicDetailDisconnected(
    onOpenConnections: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Connect to a cluster to view topic details",
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
private fun TopicDetailLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = AccentViolet,
            )
            Text("Loading topic…", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
    }
}

// ── Error State ────────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun TopicDetailError(
    error: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Failed to load topic",
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
