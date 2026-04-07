package com.lightkafka.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lightkafka.core.storage.SendStatus

@Composable
internal fun producerDialog(
    state: MainUiState,
    onAction: (MainUiAction) -> Unit,
    onSend: () -> Unit,
) {
    Dialog(
        onDismissRequest = { onAction(MainUiAction.SetProducerPanelOpen(false)) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier
                    .width(1020.dp)
                    .height(760.dp),
            shape = RoundedCornerShape(20.dp),
            color = SurfaceCard,
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Templates sidebar
                templateSidebar(state = state, onAction = onAction)

                // Vertical divider
                Box(
                    modifier =
                        Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(BorderSubtle),
                )

                // Main content
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    producerForm(state = state, onAction = onAction, onSend = onSend)

                    // Divider
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(BorderSubtle),
                    )

                    // History section
                    Text(
                        text = "Send History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    )
                    historyList(state = state)
                }
            }
        }
    }
}

@Composable
private fun templateSidebar(
    state: MainUiState,
    onAction: (MainUiAction) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(280.dp)
                .fillMaxHeight()
                .background(SurfaceDark)
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .width(4.dp)
                        .height(24.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(AccentViolet, AccentPink),
                            ),
                            RoundedCornerShape(2.dp),
                        ),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Templates",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
        }

        // Template list
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.templates) { template ->
                templateCard(
                    template = template,
                    onClick = { onAction(MainUiAction.ApplyTemplate(template.id)) },
                )
            }
        }
    }
}

@Composable
private fun templateCard(
    template: com.lightkafka.core.storage.ProducerTemplate,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgColor by animateColorAsState(
        targetValue = if (isHovered) SurfaceHover else SurfaceElevated,
        animationSpec =
            androidx.compose.animation.core
                .tween(150),
    )

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .background(AccentViolet, RoundedCornerShape(4.dp)),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = template.name,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = template.topic,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun producerForm(
    state: MainUiState,
    onAction: (MainUiAction) -> Unit,
    onSend: () -> Unit,
) {
    // Header
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .width(4.dp)
                    .height(24.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(AccentViolet, AccentPink),
                        ),
                        RoundedCornerShape(2.dp),
                    ),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Producer",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
        )
    }

    // Form fields
    OutlinedTextField(
        value = state.producerDraft.topic,
        onValueChange = { onAction(MainUiAction.UpdateProducerTopic(it)) },
        label = { Text("Topic") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = outlinedTextFieldColors(),
        shape = RoundedCornerShape(12.dp),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = state.producerDraft.partitionText,
            onValueChange = { onAction(MainUiAction.UpdateProducerPartition(it)) },
            label = { Text("Partition (optional)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
            colors = outlinedTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
        )
        OutlinedTextField(
            value = state.producerDraft.key,
            onValueChange = { onAction(MainUiAction.UpdateProducerKey(it)) },
            label = { Text("Key") },
            singleLine = true,
            modifier = Modifier.weight(2f),
            colors = outlinedTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
        )
    }

    OutlinedTextField(
        value = state.producerDraft.value,
        onValueChange = { onAction(MainUiAction.UpdateProducerValue(it)) },
        label = { Text("Value (JSON)") },
        modifier =
            Modifier
                .fillMaxWidth()
                .height(140.dp),
        colors = outlinedTextFieldColors(),
        shape = RoundedCornerShape(12.dp),
    )

    OutlinedTextField(
        value = state.producerDraft.headersText,
        onValueChange = { onAction(MainUiAction.UpdateProducerHeaders(it)) },
        label = { Text("Headers (key=value per line)") },
        modifier =
            Modifier
                .fillMaxWidth()
                .height(80.dp),
        colors = outlinedTextFieldColors(),
        shape = RoundedCornerShape(12.dp),
    )

    // Action buttons
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        // Send button with gradient
        Button(
            onClick = onSend,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = AccentViolet,
                    contentColor = Color.White,
                ),
            shape = RoundedCornerShape(12.dp),
            modifier =
                Modifier
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(AccentViolet, AccentPink),
                        ),
                        RoundedCornerShape(12.dp),
                    ),
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Send Message", fontWeight = FontWeight.SemiBold)
        }

        TextButton(
            onClick = { onAction(MainUiAction.SetProducerPanelOpen(false)) },
        ) {
            Text("Close", color = TextSecondary)
        }
    }
}

@Composable
private fun historyList(state: MainUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.history) { entry ->
            historyEntry(entry = entry)
        }
    }
}

@Composable
private fun historyEntry(entry: com.lightkafka.core.storage.SendHistoryEntry) {
    val isSuccess = entry.status == SendStatus.SUCCESS

    Surface(
        color = if (isSuccess) StatusSuccessBg.copy(alpha = 0.3f) else StatusErrorBg.copy(alpha = 0.3f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Status icon
            Box(
                modifier =
                    Modifier
                        .size(24.dp)
                        .background(
                            if (isSuccess) StatusSuccess else StatusError,
                            RoundedCornerShape(6.dp),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isSuccess) Icons.Default.Check else Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }

            // Topic
            Text(
                text = entry.topic,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
            )

            // Partition badge
            if (entry.partition != null) {
                Surface(
                    color = SurfaceElevated,
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = "P${entry.partition}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Timestamp
            Text(
                text = formatTimestamp(entry.timestampEpochMillis),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
            )

            // Error message
            val errorMsg = entry.errorMessage
            if (!isSuccess && errorMsg != null) {
                Text(
                    text = errorMsg,
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusError,
                    maxLines = 1,
                )
            }
        }
    }
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
