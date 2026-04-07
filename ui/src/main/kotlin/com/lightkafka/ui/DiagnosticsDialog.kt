package com.lightkafka.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lightkafka.core.storage.SendStatus
import java.util.Locale

@Composable
internal fun diagnosticsDialog(
    state: MainUiState,
    logStore: AppLogStore,
    onAction: (MainUiAction) -> Unit,
) {
    var logEntries by remember(state.isDiagnosticsOpen) { mutableStateOf(logStore.readRecent(limit = 250)) }
    val lastError = state.history.firstOrNull { it.status == SendStatus.FAILURE }?.errorMessage ?: "None"
    val consumerStatus = if (state.isConsumerPaused) "Paused" else "Running"
    val messageRate = formatMessagesPerSecond(state)

    Dialog(
        onDismissRequest = { onAction(MainUiAction.SetDiagnosticsOpen(false)) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier
                    .width(920.dp)
                    .height(680.dp),
            shape = RoundedCornerShape(20.dp),
            color = SurfaceCard,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
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
                        text = "Diagnostics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    )
                }

                // Metrics row
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    diagnosticsMetricCard(
                        label = "Consumer Status",
                        value = consumerStatus,
                        valueColor = if (consumerStatus == "Running") StatusSuccess else StatusWarning,
                        modifier = Modifier.weight(1f),
                    )
                    diagnosticsMetricCard(
                        label = "Messages/sec",
                        value = messageRate,
                        valueColor = AccentCyan,
                        modifier = Modifier.weight(1f),
                    )
                    diagnosticsMetricCard(
                        label = "Last Error",
                        value = lastError,
                        valueColor = if (lastError == "None") StatusSuccess else StatusError,
                        modifier = Modifier.weight(1f),
                    )
                }

                // Log file path
                Surface(
                    color = SurfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Log file:",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = logStore.logFile.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }

                // Action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    surfaceActionButton(
                        icon = Icons.Default.Refresh,
                        text = "Refresh",
                        onClick = { logEntries = logStore.readRecent(limit = 250) },
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { onAction(MainUiAction.SetDiagnosticsOpen(false)) }) {
                        Text("Close", color = TextSecondary)
                    }
                }

                // Log entries
                Surface(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(12.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(logEntries) { entry ->
                            logEntryRow(entry = entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun diagnosticsMetricCard(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = SurfaceElevated,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontWeight = FontWeight.Bold,
                letterSpacing =
                    androidx.compose.ui.unit
                        .TextUnit(1f, androidx.compose.ui.unit.TextUnitType.Sp),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun logEntryRow(entry: AppLogEntry) {
    val levelColor =
        when (entry.level.name) {
            "ERROR" -> StatusError
            "WARN" -> StatusWarning
            "INFO" -> StatusInfo
            else -> TextMuted
        }

    val levelBgColor =
        when (entry.level.name) {
            "ERROR" -> StatusErrorBg.copy(alpha = 0.3f)
            "WARN" -> Color(0xFF78350F).copy(alpha = 0.3f)
            "INFO" -> Color(0xFF1E3A5F).copy(alpha = 0.3f)
            else -> SurfaceElevated
        }

    Surface(
        color = levelBgColor,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Timestamp
            Text(
                text = formatTimestamp(entry.timestampEpochMillis, includeDate = true),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(160.dp),
            )

            // Level badge
            Surface(
                color = levelColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(
                    text = entry.level.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = levelColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Message
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun surfaceActionButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgColor by animateColorAsState(
        targetValue = if (isHovered) AccentViolet.copy(alpha = 0.2f) else SurfaceElevated,
        animationSpec =
            androidx.compose.animation.core
                .tween(150),
    )

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(10.dp),
        modifier =
            Modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentViolet,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = text,
                color = AccentViolet,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun formatMessagesPerSecond(state: MainUiState): String {
    if (state.messages.size < 2) {
        return "0.0"
    }
    val minTimestamp = state.messages.minOfOrNull { it.timestamp } ?: return "0.0"
    val maxTimestamp = state.messages.maxOfOrNull { it.timestamp } ?: return "0.0"
    val durationMs = (maxTimestamp - minTimestamp).coerceAtLeast(1)
    val rate = state.messages.size * 1_000.0 / durationMs
    return String.format(Locale.US, "%.1f", rate)
}
