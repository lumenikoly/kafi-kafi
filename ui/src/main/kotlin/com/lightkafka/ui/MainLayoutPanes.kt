package com.lightkafka.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lightkafka.core.kafka.ConsumedMessage
import com.lightkafka.core.storage.ClusterProfile

@Composable
internal fun topBar(
    state: MainUiState,
    onAction: (MainUiAction) -> Unit,
) {
    Surface(color = HeaderBackgroundColor) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Light Kafka",
                style = MaterialTheme.typography.titleLarge,
                color = AccentTextStrong,
                fontWeight = FontWeight.ExtraBold,
            )

            profileSwitcher(
                profiles = state.profiles,
                activeProfileId = state.activeProfileId,
                onSelect = { onAction(MainUiAction.SetActiveProfile(it)) },
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { onAction(MainUiAction.TogglePause) },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor, contentColor = Color.White),
            ) {
                Text(if (state.isConsumerPaused) "Resume" else "Pause")
            }

            topBarLink("Producer") { onAction(MainUiAction.SetProducerPanelOpen(true)) }
            topBarLink("Connections") { onAction(MainUiAction.SetConnectionManagerOpen(true)) }
            topBarLink("Diagnostics") { onAction(MainUiAction.SetDiagnosticsOpen(true)) }
        }
    }
}

@Composable
private fun topBarLink(
    text: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(text, color = AccentColor, fontWeight = FontWeight.Medium)
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

    Box {
        Surface(
            color = Color(0xFFE9D5FF),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.clickable { expanded = true },
        ) {
            Text(
                text = activeName,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = AccentColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            profiles.forEach { profile ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(profile.name) },
                    onClick = {
                        expanded = false
                        onSelect(profile.id)
                    },
                )
            }
        }
    }
}

@Composable
internal fun topicsPane(
    state: MainUiState,
    onAction: (MainUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.background(Color(0xFFF9FAFB)),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            compactInput(
                value = state.topicSearchQuery,
                onValueChange = { onAction(MainUiAction.SetTopicSearch(it)) },
                placeholder = "Search topics...",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        HorizontalDivider(color = Color(0xFFE5E7EB))

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(state.visibleTopics()) { topic ->
                val selected = topic == state.selectedTopic
                val background = if (selected) Color(0xFFF3E8FF) else Color.Transparent
                val textColor = if (selected) AccentTextStrong else Color(0xFF4B5563)
                Text(
                    text = topic,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(background)
                            .clickable { onAction(MainUiAction.SelectTopic(topic)) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
internal fun messagesPane(
    state: MainUiState,
    messages: List<ConsumedMessage>,
    selectedMessage: ConsumedMessage?,
    onAction: (MainUiAction) -> Unit,
    onExport: (MessageExportFormat, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxSize()) {
        topicsPane(
            state = state,
            onAction = onAction,
            modifier = Modifier.width(260.dp).fillMaxHeight(),
        )

        VerticalDivider(color = Color(0xFFD1D5DB))

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Column(modifier = Modifier.weight(0.6f).fillMaxWidth().background(Color.White)) {
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
                    modifier = Modifier.fillMaxSize().background(Color.White).padding(8.dp),
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

            HorizontalDivider(color = Color(0xFFD1D5DB), thickness = 1.dp)

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
    Row(
        modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        compactInput(
            value = state.filters.globalSearch,
            onValueChange = { onAction(MainUiAction.SetGlobalSearch(it)) },
            placeholder = "Filter globally...",
            modifier = Modifier.width(180.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        compactInput(
            value = state.filters.partitionFilter,
            onValueChange = { onAction(MainUiAction.SetPartitionFilter(it)) },
            placeholder = "Partition",
            modifier = Modifier.width(100.dp),
        )

        Spacer(modifier = Modifier.weight(1f))

        TextButton(onClick = { onExport(MessageExportFormat.JSONL, false) }) {
            Text("Export JSONL", color = AccentColor)
        }
        TextButton(
            onClick = {
                onAction(MainUiAction.SetGlobalSearch(""))
                onAction(MainUiAction.SetKeySearch(""))
                onAction(MainUiAction.SetValueSearch(""))
                onAction(MainUiAction.SetPartitionFilter(""))
            },
        ) {
            Text("Clear Filters", color = Color(0xFF6B7280))
        }
    }
}

@Composable
private fun compactInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF111827)),
        modifier =
            modifier.clip(
                RoundedCornerShape(6.dp),
            ).background(
                Color.White,
            ).border(1.dp, Color(0xFFD1D5DB), RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(placeholder, color = Color(0xFF9CA3AF), style = MaterialTheme.typography.bodyMedium)
                }
                inner()
            }
        },
    )
}

@Composable
private fun messageHeaderRow() {
    Row(
        modifier =
            Modifier.fillMaxWidth().background(
                Color(0xFFF3F4F6),
            ).border(1.dp, Color(0xFFE5E7EB)).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        headerCell("TIME", 90.dp)
        headerCell("P", 32.dp)
        headerCell("OFFSET", 60.dp)
        headerCell("KEY", 120.dp)
        headerCell("VALUE", 400.dp)
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
        color = Color(0xFF6B7280),
    )
}

@Composable
private fun messageRow(
    message: ConsumedMessage,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) Color(0xFFF3E8FF) else Color.Transparent
    val border =
        if (selected) {
            Modifier.border(
                1.dp,
                AccentColor.copy(alpha = 0.5f),
                RoundedCornerShape(4.dp),
            )
        } else {
            Modifier.border(1.dp, Color.Transparent, RoundedCornerShape(4.dp))
        }
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(background)
                .then(border)
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cellText(formatTimestamp(message.timestamp), 90.dp)
        partitionCell(message.partition, 32.dp)
        cellText(message.offset.toString(), 60.dp)
        cellText(previewBytes(message.key), 120.dp)
        cellText(previewBytes(message.value), 400.dp)
    }
}

@Composable
private fun partitionCell(
    partition: Int,
    width: Dp,
) {
    Box(
        modifier =
            Modifier.width(
                width,
            ).background(partitionTint(partition), RoundedCornerShape(6.dp)).padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = partition.toString(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1F2937),
        )
    }
}

@Composable
private fun cellText(
    text: String,
    width: Dp,
) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fontFamily = FontFamily.Monospace,
        color = Color(0xFF374151),
    )
}
