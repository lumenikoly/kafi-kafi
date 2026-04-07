package com.lightkafka.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lightkafka.core.kafka.ConsumedMessage
import com.lightkafka.core.kafka.ConsumerGroupSummary
import com.lightkafka.core.kafka.ConsumerGroupDetail
import com.lightkafka.core.storage.ClusterProfile

@Composable
internal fun topBar(
    state: MainUiState,
    onAction: (MainUiAction) -> Unit,
    kraftStatus: String,
    isKraftRunning: Boolean,
    onLaunchKraft: () -> Unit,
    onStopKraft: () -> Unit,
) {
    Surface(
        color = HeaderBackgroundColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Logo
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(36.dp)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(AccentViolet, AccentPink),
                                ),
                                CircleShape,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "K",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Light Kafka",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            // Divider
            Box(
                modifier =
                    Modifier
                        .width(1.dp)
                        .height(32.dp)
                        .background(BorderSubtle),
            )

            // Profile switcher
            profileSwitcher(
                profiles = state.profiles,
                activeProfileId = state.activeProfileId,
                onSelect = { onAction(MainUiAction.SetActiveProfile(it)) },
            )

            // Status message
            state.exportStatus?.let { status ->
                Surface(
                    color = SurfaceElevated,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = status,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            } ?: Spacer(modifier = Modifier.weight(1f))

            // KRaft controls
            kraftControlChip(
                status = kraftStatus,
                isRunning = isKraftRunning,
                onStart = onLaunchKraft,
                onStop = onStopKraft,
            )

            // Pause/Resume button
            pauseButton(
                isPaused = state.isConsumerPaused,
                onClick = { onAction(MainUiAction.TogglePause) },
            )

            // Action buttons
            navButton("Producer") { onAction(MainUiAction.SetProducerPanelOpen(true)) }
            navButton("Connections") { onAction(MainUiAction.SetConnectionManagerOpen(true)) }
            navButton("Diagnostics") { onAction(MainUiAction.SetDiagnosticsOpen(true)) }
        }
    }
}

@Composable
private fun kraftControlChip(
    status: String,
    isRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(
        color = if (isRunning) StatusSuccessBg.copy(alpha = 0.3f) else SurfaceElevated,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.clickable { if (isRunning) onStop() else onStart() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .background(
                            if (isRunning) StatusSuccess else StatusWarning,
                            CircleShape,
                        ),
            )
            Text(
                text = status,
                color = if (isRunning) StatusSuccess else TextSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun pauseButton(
    isPaused: Boolean,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = if (isPaused) StatusWarning.copy(alpha = 0.2f) else AccentViolet.copy(alpha = 0.2f),
        animationSpec = tween(200),
    )
    val iconColor by animateColorAsState(
        targetValue = if (isPaused) StatusWarning else AccentViolet,
        animationSpec = tween(200),
    )

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = if (isPaused) "Resume" else "Pause",
                tint = iconColor,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = if (isPaused) "Resume" else "Pause",
                color = iconColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun navButton(
    text: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val bgAlpha by animateColorAsState(
        targetValue = if (isHovered) SurfaceHover else Color.Transparent,
        animationSpec = tween(150),
    )

    Surface(
        color = bgAlpha,
        shape = RoundedCornerShape(10.dp),
        modifier =
            Modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Text(
            text = text,
            color = TextSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun profileSwitcher(
    profiles: List<ClusterProfile>,
    activeProfileId: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val activeName = profiles.firstOrNull { it.id == activeProfileId }?.name ?: "No profile"
    val activeProfile = profiles.firstOrNull { it.id == activeProfileId }

    Box {
        Surface(
            color = SurfaceElevated,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.clickable { expanded = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .background(
                                if (activeProfile != null) StatusSuccess else StatusWarning,
                                CircleShape,
                            ),
                )
                Text(
                    text = activeName,
                    color = TextPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier =
                Modifier
                    .background(SurfaceCard, RoundedCornerShape(12.dp))
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)),
        ) {
            profiles.forEach { profile ->
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(6.dp)
                                        .background(AccentViolet, CircleShape),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(profile.name, color = TextPrimary)
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(profile.id)
                    },
                    modifier =
                        Modifier.background(
                            if (profile.id == activeProfileId) SurfaceHover else Color.Transparent,
                        ),
                )
            }
        }
    }
}

@Composable
internal fun topicsPane(
    state: MainUiState,
    onAction: (MainUiAction) -> Unit,
    onCreateTopic: (String) -> Unit,
    onDeleteTopic: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isCreateTopicDialogOpen by remember { mutableStateOf(false) }
    var newTopicName by remember { mutableStateOf("") }
    var contextMenuTopic by remember { mutableStateOf<String?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.background(SidebarBackgroundColor),
    ) {
        // Header
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
        ) {
            Text(
                text = "Topics",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            glassInput(
                value = state.topicSearchQuery,
                onValueChange = { onAction(MainUiAction.SetTopicSearch(it)) },
                placeholder = "Search...",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BorderSubtle),
        )

        // Topic list
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                createTopicButton(onClick = {
                    newTopicName = ""
                    isCreateTopicDialogOpen = true
                })
            }
            items(state.visibleTopics()) { topic ->
                topicItem(
                    topic = topic,
                    selected = topic == state.selectedTopic,
                    onClick = { onAction(MainUiAction.SelectTopic(topic)) },
                    onRightClick = {
                        contextMenuTopic = topic
                        showContextMenu = true
                    },
                )
            }
        }
    }

    if (isCreateTopicDialogOpen) {
        createTopicDialog(
            topicName = newTopicName,
            onTopicNameChange = { newTopicName = it },
            onDismiss = { isCreateTopicDialogOpen = false },
            onCreate = {
                onCreateTopic(newTopicName.trim())
                isCreateTopicDialogOpen = false
            },
        )
    }

    // Context menu
    if (showContextMenu && contextMenuTopic != null) {
        topicContextMenu(
            topicName = contextMenuTopic!!,
            onDismiss = { showContextMenu = false },
            onDelete = {
                onAction(MainUiAction.RequestDeleteTopic(contextMenuTopic))
                showContextMenu = false
            },
            onConfigure = {
                onAction(MainUiAction.SetTopicConfigDialogOpen(true))
                showContextMenu = false
            },
        )
    }

    // Delete confirmation dialog
    state.topicToDelete?.let { topicName ->
        deleteTopicConfirmationDialog(
            topicName = topicName,
            onDismiss = { onAction(MainUiAction.CancelDeleteTopic) },
            onConfirm = {
                onDeleteTopic(topicName)
                onAction(MainUiAction.ConfirmDeleteTopic(topicName))
            },
        )
    }
}

@Composable
private fun createTopicButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Surface(
        color = if (isHovered) AccentViolet.copy(alpha = 0.15f) else AccentViolet.copy(alpha = 0.1f),
        shape = RoundedCornerShape(10.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = AccentViolet,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Create topic",
                color = AccentViolet,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun topicItem(
    topic: String,
    selected: Boolean,
    onClick: () -> Unit,
    onRightClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgColor by animateColorAsState(
        targetValue =
            when {
                selected -> AccentViolet.copy(alpha = 0.15f)
                isHovered -> SurfaceHover
                else -> Color.Transparent
            },
        animationSpec = tween(150),
    )

    val indicatorWidth by animateDpAsState(
        targetValue = if (selected) 3.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
    )

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(10.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
    ) {
        Row(
            modifier =
                Modifier
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onRightClick,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Selection indicator
            Box(
                modifier =
                    Modifier
                        .width(indicatorWidth)
                        .height(20.dp)
                        .background(AccentViolet, RoundedCornerShape(2.dp)),
            )
            if (selected) Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = topic,
                color = if (selected) AccentViolet else TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun createTopicDialog(
    topicName: String,
    onTopicNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Text(
                text = "Create New Topic",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter a name for your new Kafka topic.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = topicName,
                    onValueChange = onTopicNameChange,
                    singleLine = true,
                    label = { Text("Topic name") },
                    placeholder = { Text("e.g. orders.v2") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedTextFieldColors(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onCreate,
                enabled = topicName.trim().isNotEmpty(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = AccentViolet,
                        contentColor = Color.White,
                    ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
    )
}

@Composable
private fun topicContextMenu(
    topicName: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onConfigure: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Text(
                text = topicName,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = SurfaceElevated,
                    shape = RoundedCornerShape(8.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onConfigure),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "Configure Topic",
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Surface(
                    color = StatusError.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onDelete),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = StatusError,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "Delete Topic",
                            color = StatusError,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun deleteTopicConfirmationDialog(
    topicName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Text(
                text = "Delete Topic?",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column {
                Text(
                    text = "Are you sure you want to delete topic \"$topicName\"?",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This action cannot be undone. All messages in this topic will be permanently deleted.",
                    color = StatusError,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = StatusError,
                        contentColor = Color.White,
                    ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
    )
}

@Composable
internal fun messagesPane(
    state: MainUiState,
    messages: List<ConsumedMessage>,
    selectedMessage: ConsumedMessage?,
    onAction: (MainUiAction) -> Unit,
    onCreateTopic: (String) -> Unit,
    onDeleteTopic: (String) -> Unit,
    onExport: (MessageExportFormat, Boolean) -> Unit,
    onRefreshConsumerGroups: () -> Unit,
    onSelectConsumerGroup: (String) -> Unit,
    onDeleteConsumerGroup: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxSize()) {
        // Sidebar with tabs
        Column(modifier = Modifier.width(280.dp).fillMaxHeight()) {
            // Tab selector
            Row(
                modifier = Modifier.fillMaxWidth().background(SidebarBackgroundColor).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                sidebarTab(
                    text = "Topics",
                    selected = state.selectedSidebarTab == SidebarTab.Topics,
                    onClick = { onAction(MainUiAction.SelectSidebarTab(SidebarTab.Topics)) },
                    modifier = Modifier.weight(1f),
                )
                sidebarTab(
                    text = "Groups",
                    selected = state.selectedSidebarTab == SidebarTab.ConsumerGroups,
                    onClick = { onAction(MainUiAction.SelectSidebarTab(SidebarTab.ConsumerGroups)) },
                    modifier = Modifier.weight(1f),
                )
            }

            // Content based on selected tab
            when (state.selectedSidebarTab) {
                SidebarTab.Topics ->
                    topicsPane(
                        state = state,
                        onAction = onAction,
                        onCreateTopic = onCreateTopic,
                        onDeleteTopic = onDeleteTopic,
                        modifier = Modifier.fillMaxSize(),
                    )
                SidebarTab.ConsumerGroups ->
                    consumerGroupsPane(
                        state = ConsumerGroupsState(
                            groups = state.consumerGroups,
                            selectedGroupId = state.selectedConsumerGroupId,
                            selectedGroupDetail = state.selectedConsumerGroupDetail,
                            isLoading = state.isLoadingConsumerGroups,
                            error = state.consumerGroupsError,
                        ),
                        onRefresh = onRefreshConsumerGroups,
                        onSelectGroup = onSelectConsumerGroup,
                        onDeleteGroup = onDeleteConsumerGroup,
                        modifier = Modifier.fillMaxSize(),
                    )
            }
        }

        // Vertical divider
        Box(
            modifier =
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(BorderSubtle),
        )

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            // Messages list section
            Column(modifier = Modifier.weight(0.6f).fillMaxWidth().background(SurfaceDark)) {
                messagesControlBar(state = state, onAction = onAction, onExport = onExport)
                messageHeaderRow()

                val listState = rememberLazyListState()

                LaunchedEffect(messages.size) {
                    if (messages.isNotEmpty()) {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(SurfaceDark)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    items(items = messages, key = { messageId(it) }) { message ->
                        messageRow(
                            message = message,
                            selected = state.selectedMessageId == messageId(message),
                            onClick = { onAction(MainUiAction.SelectMessage(messageId(message))) },
                        )
                    }
                }
            }

            // Divider
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(BorderSubtle),
            )

            // Inspector
            inspectorPane(
                selectedMessage = selectedMessage,
                modifier = Modifier.weight(0.4f).fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun messagesControlBar(
    state: MainUiState,
    onAction: (MainUiAction) -> Unit,
    onExport: (MessageExportFormat, Boolean) -> Unit,
) {
    Surface(
        color = SurfaceCard,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            glassInput(
                value = state.filters.globalSearch,
                onValueChange = { onAction(MainUiAction.SetGlobalSearch(it)) },
                placeholder = "Search messages...",
                modifier = Modifier.width(220.dp),
            )

            glassInput(
                value = state.filters.partitionFilter,
                onValueChange = { onAction(MainUiAction.SetPartitionFilter(it)) },
                placeholder = "Partition",
                modifier = Modifier.width(100.dp),
            )

            Spacer(modifier = Modifier.weight(1f))

            // Action buttons
            surfaceButton(
                text = "Export JSONL",
                onClick = { onExport(MessageExportFormat.JSONL, false) },
            )

            surfaceButton(
                text = "Clear Filters",
                onClick = {
                    onAction(MainUiAction.SetGlobalSearch(""))
                    onAction(MainUiAction.SetKeySearch(""))
                    onAction(MainUiAction.SetValueSearch(""))
                    onAction(MainUiAction.SetPartitionFilter(""))
                },
                isSecondary = true,
            )
        }
    }
}

@Composable
private fun surfaceButton(
    text: String,
    onClick: () -> Unit,
    isSecondary: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Surface(
        color =
            if (isSecondary) {
                if (isHovered) SurfaceHover else SurfaceElevated
            } else {
                if (isHovered) AccentViolet.copy(alpha = 0.25f) else AccentViolet.copy(alpha = 0.15f)
            },
        shape = RoundedCornerShape(8.dp),
        modifier =
            Modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Text(
            text = text,
            color = if (isSecondary) TextSecondary else AccentViolet,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun sidebarTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (selected) AccentViolet.copy(alpha = 0.2f) else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            color = if (selected) AccentViolet else TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun glassInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
        cursorBrush = SolidColor(AccentViolet),
        modifier =
            modifier
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceElevated)
                .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        color = TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun messageHeaderRow() {
    Surface(
        color = SurfaceCard,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawLine(
                            color = BorderSubtle,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            headerCell("TIME", 80.dp)
            headerCell("P", 36.dp)
            headerCell("OFFSET", 70.dp)
            headerCell("KEY", 140.dp)
            headerCell("VALUE", 400.dp)
        }
    }
}

@Composable
private fun headerCell(
    text: String,
    width: Dp,
) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = TextMuted,
        letterSpacing =
            androidx.compose.ui.unit
                .TextUnit(0.5f, androidx.compose.ui.unit.TextUnitType.Sp),
    )
}

@Composable
private fun messageRow(
    message: ConsumedMessage,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgColor by animateColorAsState(
        targetValue =
            when {
                selected -> AccentViolet.copy(alpha = 0.12f)
                isHovered -> SurfaceHover
                else -> Color.Transparent
            },
        animationSpec = tween(150),
    )

    val borderColor by animateColorAsState(
        targetValue = if (selected) AccentViolet.copy(alpha = 0.4f) else Color.Transparent,
        animationSpec = tween(150),
    )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(bgColor)
                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cellText(formatTimestamp(message.timestamp), 80.dp, TextSecondary)
        partitionBadge(message.partition)
        cellText(message.offset.toString(), 70.dp, TextMuted, FontWeight.Medium)
        cellText(previewBytes(message.key), 140.dp, TextSecondary)
        cellText(previewBytes(message.value), 400.dp, TextPrimary)
    }
}

@Composable
private fun partitionBadge(partition: Int) {
    Surface(
        color = partitionTint(partition),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.width(36.dp),
    ) {
        Text(
            text = partition.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = partitionTextColor(partition),
            modifier = Modifier.padding(vertical = 4.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun cellText(
    text: String,
    width: Dp,
    color: Color,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fontFamily = FontFamily.Monospace,
        color = color,
        fontWeight = fontWeight,
    )
}

@Composable
private fun outlinedTextFieldColors() =
    androidx.compose.material3.OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AccentViolet,
        unfocusedBorderColor = BorderDefault,
        focusedContainerColor = SurfaceElevated,
        unfocusedContainerColor = SurfaceElevated,
        focusedLabelColor = AccentViolet,
        unfocusedLabelColor = TextMuted,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        cursorColor = AccentViolet,
    )
