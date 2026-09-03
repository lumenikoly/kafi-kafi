package com.lightkafka.ui.consumer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lightkafka.core.kafka.ConsumerGroupSummary
import com.lightkafka.core.kafka.DefaultKafkaConsumerGroupService
import com.lightkafka.core.storage.ClusterProfile
import com.lightkafka.ui.connection.AppConnectionState
import com.lightkafka.ui.connection.ConnectionState
import com.lightkafka.ui.connection.toKafkaConnectionConfig
import com.lightkafka.ui.infra.AccentViolet
import com.lightkafka.ui.infra.BorderSubtle
import com.lightkafka.ui.infra.StatusError
import com.lightkafka.ui.infra.StatusSuccess
import com.lightkafka.ui.infra.StatusWarning
import com.lightkafka.ui.infra.SurfaceCard
import com.lightkafka.ui.infra.SurfaceHover
import com.lightkafka.ui.infra.TextMuted
import com.lightkafka.ui.infra.TextPrimary
import com.lightkafka.ui.infra.TextSecondary
import com.lightkafka.ui.shell.ShellAction
import com.lightkafka.ui.shell.Store
import com.lightkafka.ui.shell.TabType
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("ktlint:standard:function-naming")
@Composable
fun ConsumerGroupsPage(
    connectionStateFlow: MutableStateFlow<AppConnectionState>,
    shellStore: Store<*, ShellAction>,
    modifier: Modifier = Modifier,
) {
    val connectionState by connectionStateFlow.collectAsState()
    val profile = connectionState.activeProfile
    if (profile == null || connectionState.connectionStatus?.state != ConnectionState.CONNECTED) {
        DisconnectedState(
            onOpenConnections = { shellStore.dispatch(ShellAction.OpenTab(TabType.CONNECTIONS)) },
            modifier = modifier,
        )
        return
    }

    ConnectedConsumerGroups(profile = profile, modifier = modifier)
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun ConnectedConsumerGroups(
    profile: ClusterProfile,
    modifier: Modifier = Modifier,
) {
    val state =
        remember(profile.id) {
            ConsumerGroupsScreenState(DefaultKafkaConsumerGroupService(profile.toKafkaConnectionConfig()))
        }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.service) {
        try {
            awaitCancellation()
        } finally {
            withContext(NonCancellable) { state.service.close() }
        }
    }
    LaunchedEffect(state, state.groupsRefreshKey) { state.loadGroups() }
    LaunchedEffect(state, state.selectedGroupId, state.detailRefreshKey) { state.loadDetail() }

    ConsumerGroupsContent(state = state, modifier = modifier)

    state.detail?.takeIf { state.resetDialogOpen }?.let { group ->
        ResetOffsetsDialog(
            group = group,
            inProgress = state.actionInProgress,
            onDismiss = { state.resetDialogOpen = false },
            onConfirm = { topic, spec -> scope.launch { state.resetOffsets(group.groupId, topic, spec) } },
        )
    }
    state.selectedGroupId?.takeIf { state.deleteDialogOpen }?.let { groupId ->
        DeleteGroupDialog(
            groupId = groupId,
            inProgress = state.actionInProgress,
            onDismiss = { state.deleteDialogOpen = false },
            onConfirm = { scope.launch { state.deleteGroup(groupId) } },
        )
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun ConsumerGroupsContent(
    state: ConsumerGroupsScreenState,
    modifier: Modifier,
) {
    val filteredGroups =
        remember(state.groups, state.search) {
            state.groups.filter { it.groupId.contains(state.search, ignoreCase = true) }
        }
    Row(modifier = modifier.fillMaxSize()) {
        GroupList(
            groups = filteredGroups,
            totalCount = state.groups.size,
            selectedGroupId = state.selectedGroupId,
            search = state.search,
            isLoading = state.isLoading,
            error = state.listError,
            onSearchChange = { state.search = it },
            onSelect = state::selectGroup,
            onRefresh = { state.groupsRefreshKey++ },
            modifier = Modifier.width(320.dp).fillMaxHeight(),
        )
        Box(Modifier.width(1.dp).fillMaxHeight().background(BorderSubtle))
        GroupDetail(
            detail = state.detail,
            selectedGroupId = state.selectedGroupId,
            isLoading = state.isDetailLoading,
            error = state.detailError,
            actionMessage = state.actionMessage,
            actionIsError = state.actionIsError,
            actionInProgress = state.actionInProgress,
            onRetry = { state.detailRefreshKey++ },
            onReset = { state.resetDialogOpen = true },
            onDelete = { state.deleteDialogOpen = true },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun GroupList(
    groups: List<ConsumerGroupSummary>,
    totalCount: Int,
    selectedGroupId: String?,
    search: String,
    isLoading: Boolean,
    error: String?,
    onSearchChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.background(SurfaceCard)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Consumer Groups", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text("$totalCount total", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            TextButton(onClick = onRefresh, enabled = !isLoading) { Text("Refresh") }
        }
        OutlinedTextField(
            value = search,
            onValueChange = onSearchChange,
            label = { Text("Find group") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        )
        HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(top = 8.dp))

        when {
            isLoading -> CenteredProgress()
            error != null -> InlineError(error = error, onRetry = onRefresh)
            groups.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (search.isBlank()) "No consumer groups found" else "No groups match your search",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                    )
                }
            else ->
                LazyColumn {
                    items(groups, key = ConsumerGroupSummary::groupId) { group ->
                        GroupRow(
                            group = group,
                            selected = group.groupId == selectedGroupId,
                            onClick = { onSelect(group.groupId) },
                        )
                    }
                }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun GroupRow(
    group: ConsumerGroupSummary,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .background(if (selected) SurfaceHover else SurfaceCard)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                group.groupId,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${group.memberCount} members · ${group.topicCount} topics",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
        StateLabel(group.state ?: "UNKNOWN")
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun StateLabel(state: String) {
    val color =
        when (state.uppercase()) {
            "STABLE" -> StatusSuccess
            "EMPTY", "DEAD" -> TextMuted
            "PREPARING_REBALANCE", "COMPLETING_REBALANCE", "ASSIGNING", "RECONCILING" -> StatusWarning
            else -> TextSecondary
        }
    Text(state.replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = color)
}

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun InlineError(
    error: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(error, style = MaterialTheme.typography.bodyMedium, color = StatusError)
        OutlinedButton(onClick = onRetry) { Text("Try again") }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun CenteredProgress() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun DisconnectedState(
    onOpenConnections: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Connect to a cluster to manage consumer groups",
                style = MaterialTheme.typography.headlineSmall,
                color = TextSecondary,
            )
            Button(
                onClick = onOpenConnections,
                colors = ButtonDefaults.buttonColors(containerColor = AccentViolet, contentColor = TextPrimary),
            ) { Text("Open Connections") }
        }
    }
}
