package com.lightkafka.ui.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lightkafka.core.kafka.DefaultKafkaAdminService
import com.lightkafka.core.kafka.KafkaResult
import com.lightkafka.core.storage.ClusterProfile
import com.lightkafka.ui.infra.BorderSubtle
import com.lightkafka.ui.infra.StatusError
import com.lightkafka.ui.infra.StatusSuccess
import com.lightkafka.ui.infra.SurfaceCard
import com.lightkafka.ui.infra.SurfaceElevated
import com.lightkafka.ui.infra.SurfaceHover
import com.lightkafka.ui.infra.TextMuted
import com.lightkafka.ui.infra.TextPrimary
import com.lightkafka.ui.infra.TextSecondary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

@Suppress("ktlint:standard:function-naming")
@Composable
fun ConnectionPage(
    ctx: ConnectionPageContext,
    modifier: Modifier = Modifier,
) {
    val profiles = remember { mutableStateListOf<ClusterProfile>() }
    var selectedProfileId by remember { mutableStateOf<String?>(null) }
    var editorState by remember { mutableStateOf(ConnectionEditorState()) }

    LaunchedEffect(Unit) {
        profiles.clear()
        profiles.addAll(ctx.profileStore.loadProfiles())
        if (selectedProfileId == null && profiles.isNotEmpty()) {
            selectedProfileId = profiles.first().id
        }
    }

    LaunchedEffect(selectedProfileId) {
        val profile = profiles.find { it.id == selectedProfileId }
        editorState = profile?.toEditorState() ?: ConnectionEditorState()
    }

    val actions =
        buildEditorActions(
            editorState = editorState,
            onEditorStateChange = { editorState = it },
            onEditorStateUpdate = { update -> editorState = update(editorState) },
            profiles = profiles,
            ctx = ctx,
            onSelectedIdChange = { selectedProfileId = it },
        )

    Row(modifier = modifier.fillMaxSize()) {
        ProfileListPanel(
            profiles = profiles,
            selectedProfileId = selectedProfileId,
            onProfileSelect = { id -> selectedProfileId = id },
            onNewProfile = {
                editorState = ConnectionEditorState()
                selectedProfileId = null
            },
            modifier = Modifier.width(220.dp).fillMaxHeight(),
        )
        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(BorderSubtle))
        ConnectionEditorPanel(
            state = editorState,
            actions = actions,
            connectionState = ctx.connectionStateFlow,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

private fun buildEditorActions(
    editorState: ConnectionEditorState,
    onEditorStateChange: (ConnectionEditorState) -> Unit,
    onEditorStateUpdate: ((ConnectionEditorState) -> ConnectionEditorState) -> Unit,
    profiles: MutableList<ClusterProfile>,
    ctx: ConnectionPageContext,
    onSelectedIdChange: (String?) -> Unit,
): EditorActions =
    EditorActions(
        onStateChange = onEditorStateChange,
        onTest = {
            onEditorStateChange(
                editorState.copy(isTesting = true, testStatus = ConnectionTestStatus.InProgress()),
            )
            ctx.coroutineScope.launch {
                val svc =
                    DefaultKafkaAdminService(
                        editorState.toProfile(UUID.randomUUID().toString()).toKafkaConnectionConfig(),
                    )
                val t0 = System.currentTimeMillis()
                val result = svc.listTopics()
                val latency = System.currentTimeMillis() - t0
                svc.close()
                onEditorStateUpdate { currentState ->
                    currentState.copy(
                        isTesting = false,
                        testStatus =
                            when (result) {
                                is KafkaResult.Success ->
                                    ConnectionTestStatus.Success(
                                        clusterId = null,
                                        brokerCount = 0,
                                        controllerId = null,
                                        topicCount = result.value.size,
                                        latencyMs = latency,
                                    )
                                is KafkaResult.Failure ->
                                    ConnectionTestStatus.Failure(
                                        error = formatConnectionError(result.error),
                                    )
                            },
                    )
                }
            }
        },
        onSave = {
            val id = editorState.profileId ?: UUID.randomUUID().toString()
            ctx.profileStore.upsertProfile(editorState.toProfile(id))
            profiles.clear()
            profiles.addAll(ctx.profileStore.loadProfiles())
            onSelectedIdChange(id)
            onEditorStateChange(editorState.copy(profileId = id))
        },
        onConnect = connect@{
            val id = editorState.profileId ?: return@connect
            val profile = editorState.toProfile(id)
            ctx.coroutineScope.launch {
                val svc = DefaultKafkaAdminService(profile.toKafkaConnectionConfig())
                val startedAt = System.currentTimeMillis()
                ctx.connectionStateFlow.value = ctx.connectionStateFlow.value.copy(isConnecting = true)
                when (val result = svc.listTopics()) {
                    is KafkaResult.Success -> {
                        ctx.connectionStateFlow.value.activeAdminService?.close()
                        ctx.connectionStateFlow.value =
                            AppConnectionState(
                                activeProfile = profile,
                                activeAdminService = svc,
                                connectionStatus =
                                    ConnectionStatus(
                                        profileId = profile.id,
                                        state = ConnectionState.CONNECTED,
                                        latencyMs = System.currentTimeMillis() - startedAt,
                                        lastTestedMs = System.currentTimeMillis(),
                                    ),
                            )
                        ctx.shellStore.dispatch(
                            com.lightkafka.ui.shell.ShellAction.OpenTab(
                                com.lightkafka.ui.shell.TabType.CLUSTER_OVERVIEW,
                            ),
                        )
                    }
                    is KafkaResult.Failure -> {
                        svc.close()
                        ctx.connectionStateFlow.value = ctx.connectionStateFlow.value.copy(isConnecting = false)
                        onEditorStateUpdate { currentState ->
                            currentState.copy(
                                testStatus = ConnectionTestStatus.Failure(formatConnectionError(result.error)),
                            )
                        }
                    }
                }
            }
        },
        onDelete = delete@{
            val id = editorState.profileId ?: return@delete
            if (ctx.connectionStateFlow.value.activeProfile?.id == id) {
                val service = ctx.connectionStateFlow.value.activeAdminService
                ctx.connectionStateFlow.value = AppConnectionState()
                ctx.coroutineScope.launch { service?.close() }
            }
            ctx.profileStore.deleteProfile(id)
            profiles.clear()
            profiles.addAll(ctx.profileStore.loadProfiles())
            onSelectedIdChange(profiles.firstOrNull()?.id)
        },
    )

@Suppress("ktlint:standard:function-naming")
@Composable
private fun ProfileListPanel(
    profiles: List<ClusterProfile>,
    selectedProfileId: String?,
    onProfileSelect: (String) -> Unit,
    onNewProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.background(SurfaceCard).padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Profiles", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            TextButton(onClick = onNewProfile) { Text("+", color = TextSecondary) }
        }
        HorizontalDivider(color = BorderSubtle)
        LazyColumn(
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(profiles, key = { it.id }) { profile ->
                val isSelected = profile.id == selectedProfileId
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) SurfaceHover else SurfaceCard)
                            .clickable { onProfileSelect(profile.id) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        profile.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) TextPrimary else TextSecondary,
                    )
                    SecurityBadge(protocol = profile.securityProtocol)
                }
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun ConnectionEditorPanel(
    state: ConnectionEditorState,
    actions: EditorActions,
    connectionState: MutableStateFlow<AppConnectionState>,
    modifier: Modifier = Modifier,
) {
    val currentConnection by connectionState.collectAsState()
    Column(
        modifier = modifier.background(SurfaceElevated).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        EditorHeader(state = state, activeProfileId = currentConnection.activeProfile?.id)
        if (state.testStatus != null) {
            TestStatusBanner(state.testStatus)
        }
        EditorActionButtons(state = state, actions = actions, isConnecting = currentConnection.isConnecting)
        HorizontalDivider(color = BorderSubtle)
        ConnectionForm(
            state = state,
            onStateChange = actions.onStateChange,
            modifier = Modifier.widthIn(max = 720.dp),
        )
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun EditorHeader(
    state: ConnectionEditorState,
    activeProfileId: String?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (state.isNewProfile) "New Connection" else state.name.ifBlank { "Edit Connection" },
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
        )
        if (!state.isNewProfile && activeProfileId == state.profileId) {
            Text("Connected", style = MaterialTheme.typography.labelMedium, color = StatusSuccess)
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun EditorActionButtons(
    state: ConnectionEditorState,
    actions: EditorActions,
    isConnecting: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = actions.onTest,
            enabled = state.canSave && !state.isTesting,
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceHover, contentColor = TextPrimary),
        ) { Text(if (state.isTesting) "Testing..." else "Test Connection") }
        Button(
            onClick = actions.onSave,
            enabled = state.canSave,
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceHover, contentColor = TextPrimary),
        ) { Text("Save") }
        Button(
            onClick = actions.onConnect,
            enabled = state.canSave && state.profileId != null && !isConnecting,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) { Text(if (isConnecting) "Connecting…" else "Connect") }
        if (!state.isNewProfile) {
            Spacer(modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = actions.onDelete,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
            ) { Text("Delete") }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun ConnectionForm(
    state: ConnectionEditorState,
    onStateChange: (ConnectionEditorState) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { FormSectionHeader("Basic") }
        item { FormField("Name", state.name, { onStateChange(state.copy(name = it)) }) }
        item {
            FormField(
                "Bootstrap Servers",
                state.bootstrapServers,
                { onStateChange(state.copy(bootstrapServers = it)) },
                "host1:9092,host2:9092",
            )
        }
        item {
            FormField(
                "Client ID",
                state.clientId,
                { onStateChange(state.copy(clientId = it)) },
                "optional",
            )
        }
        item { FormSectionHeader("Security") }
        item {
            SecurityProtocolDropdown(
                selected = state.securityProtocol,
                onSelected = { onStateChange(state.copy(securityProtocol = it)) },
            )
        }
        if (state.needsSasl) {
            item { SaslFields(state, onStateChange) }
        }
        if (state.needsSsl) {
            item { SslFields(state, onStateChange) }
        }
        item { FormSectionHeader("Advanced Properties") }
        items(
            count = state.additionalProperties.size,
            key = { idx ->
                state.additionalProperties.entries
                    .toList()[idx]
                    .key
            },
        ) { idx ->
            val entry = state.additionalProperties.entries.toList()[idx]
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    entry.key,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (entry.key.contains("password", true)) "••••" else entry.value,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
