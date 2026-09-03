package com.lightkafka.ui.topic

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lightkafka.core.kafka.KafkaResult
import com.lightkafka.core.kafka.TopicSummary
import com.lightkafka.ui.connection.AppConnectionState
import com.lightkafka.ui.connection.ConnectionState
import com.lightkafka.ui.infra.AccentViolet
import com.lightkafka.ui.infra.BorderSubtle
import com.lightkafka.ui.infra.StatusError
import com.lightkafka.ui.infra.SurfaceCard
import com.lightkafka.ui.infra.SurfaceElevated
import com.lightkafka.ui.infra.TextMuted
import com.lightkafka.ui.infra.TextPrimary
import com.lightkafka.ui.infra.TextSecondary
import com.lightkafka.ui.infra.formatKafkaError
import com.lightkafka.ui.shell.ShellAction
import com.lightkafka.ui.shell.Store
import com.lightkafka.ui.shell.TabType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

// ── Main Entry ─────────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
fun TopicListPage(
    connectionStateFlow: MutableStateFlow<AppConnectionState>,
    shellStore: Store<*, ShellAction>,
    modifier: Modifier = Modifier,
) {
    val connectionState by connectionStateFlow.collectAsState()
    val adminService = connectionState.activeAdminService

    if (adminService == null || connectionState.connectionStatus?.state != ConnectionState.CONNECTED) {
        TopicListDisconnected(
            onOpenConnections = { shellStore.dispatch(ShellAction.OpenTab(TabType.CONNECTIONS)) },
            modifier = modifier,
        )
        return
    }

    var topics by remember { mutableStateOf<List<TopicSummary>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableStateOf(0) }

    LaunchedEffect(connectionState.activeProfile?.id, retryKey) {
        isLoading = true
        errorMessage = null
        try {
            when (val result = adminService.listTopics(includeInternal = true)) {
                is KafkaResult.Success -> topics = result.value
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
        isLoading -> TopicListLoading(modifier = modifier)
        errorMessage != null ->
            TopicListError(
                error = errorMessage!!,
                onRetry = { retryKey++ },
                modifier = modifier,
            )
        topics != null ->
            TopicListConnected(
                topics = topics!!,
                onTopicDoubleClick = { topicName ->
                    shellStore.dispatch(
                        ShellAction.OpenEntityTab(
                            type = TabType.TOPIC_DETAIL,
                            entityId = topicName,
                            title = topicName,
                        ),
                    )
                },
                onCreateTopicClick = {
                    shellStore.dispatch(ShellAction.OpenTab(TabType.CREATE_TOPIC))
                },
                modifier = modifier,
            )
    }
}

// ── Connected State (search + filter + list) ───────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun TopicListConnected(
    topics: List<TopicSummary>,
    onTopicDoubleClick: (String) -> Unit,
    onCreateTopicClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var hideInternal by remember { mutableStateOf(false) }
    var selectedTopic by remember { mutableStateOf<String?>(null) }

    val filtered =
        remember(topics, searchQuery, hideInternal) {
            topics
                .asSequence()
                .let { seq ->
                    if (hideInternal) seq.filterNot { it.internal } else seq
                }.filter { it.name.contains(searchQuery, ignoreCase = true) }
                .sortedBy { it.name.lowercase() }
                .toList()
        }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        TopicListHeader(
            totalTopics = topics.size,
            filteredCount = filtered.size,
            onCreateTopicClick = onCreateTopicClick,
        )

        HorizontalDivider(
            color = BorderSubtle,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        TopicListSearchBar(
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            hideInternal = hideInternal,
            onHideInternalChange = { hideInternal = it },
        )

        TopicListContent(
            filtered = filtered,
            allTopics = topics,
            selectedTopic = selectedTopic,
            onTopicSelect = { selectedTopic = it },
            onTopicDoubleClick = onTopicDoubleClick,
        )
    }
}

// ── Header ─────────────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun TopicListHeader(
    totalTopics: Int,
    filteredCount: Int,
    onCreateTopicClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                "Topics",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "$filteredCount of $totalTopics topics",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
        Button(
            onClick = onCreateTopicClick,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = AccentViolet,
                    contentColor = TextPrimary,
                ),
        ) {
            Text("+ Create Topic")
        }
    }
}

// ── Search Bar ─────────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun TopicListSearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    hideInternal: Boolean,
    onHideInternalChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search topics…", color = TextMuted) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AccentViolet,
                    unfocusedBorderColor = BorderSubtle,
                    cursorColor = AccentViolet,
                ),
            textStyle = MaterialTheme.typography.bodyMedium,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Checkbox(
                checked = hideInternal,
                onCheckedChange = onHideInternalChange,
                colors =
                    CheckboxDefaults.colors(
                        checkedColor = AccentViolet,
                        uncheckedColor = TextMuted,
                    ),
            )
            Text(
                "Hide internal",
                style = MaterialTheme.typography.bodySmall,
                color = if (hideInternal) TextPrimary else TextSecondary,
            )
        }
    }
}

// ── List Content ───────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun TopicListContent(
    filtered: List<TopicSummary>,
    allTopics: List<TopicSummary>,
    selectedTopic: String?,
    onTopicSelect: (String) -> Unit,
    onTopicDoubleClick: (String) -> Unit,
) {
    if (filtered.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (allTopics.isEmpty()) "No topics found" else "No topics match your search",
                style = MaterialTheme.typography.bodyLarge,
                color = TextMuted,
            )
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(filtered, key = { it.name }) { topic ->
                TopicRow(
                    topic = topic,
                    isSelected = selectedTopic == topic.name,
                    onDoubleClick = { onTopicDoubleClick(topic.name) },
                    onClick = { onTopicSelect(topic.name) },
                )
            }
        }
    }
}

// ── Topic Row ──────────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun TopicRow(
    topic: TopicSummary,
    isSelected: Boolean,
    onDoubleClick: () -> Unit,
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
                .composed {
                    pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onClick() },
                            onDoubleTap = { onDoubleClick() },
                        )
                    }
                }.padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                topic.name,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            if (topic.internal) {
                InternalBadge()
            }
        }

        Text(
            "${topic.partitions} partitions",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

// ── Internal Badge ─────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun InternalBadge(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .background(SurfaceElevated, MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            "internal",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
        )
    }
}

// ── Disconnected State ─────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun TopicListDisconnected(
    onOpenConnections: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Connect to a cluster to view topics",
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
private fun TopicListLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = AccentViolet,
            )
            Text("Loading topics…", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
    }
}

// ── Error State ────────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun TopicListError(
    error: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Failed to load topics",
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
