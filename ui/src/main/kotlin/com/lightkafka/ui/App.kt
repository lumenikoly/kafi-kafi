package com.lightkafka.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lightkafka.core.storage.SendStatus
import java.nio.file.Path
import kotlin.io.path.createDirectories

@Composable
fun appContent() {
    MaterialTheme {
        val appDataDirectory = remember { defaultAppDataDirectory() }
        val messageExporter = remember { MessageExporter(exportDirectory = appDataDirectory.resolve("exports")) }
        val appLogStore = remember { AppLogStore(logFile = appDataDirectory.resolve("logs").resolve("app.log")) }
        var state by remember { mutableStateOf(sampleMainUiState()) }
        val dispatch = { action: MainUiAction ->
            state = reduceMainUiState(state, action)
            logAction(appLogStore, action, state)
        }
        val visibleMessages = state.filteredMessages()
        val selectedMessage = selectedMessageOrNull(state)
        val exportMessages: (MessageExportFormat, Boolean) -> Unit = { format, selectedOnly ->
            val sourceMessages =
                if (selectedOnly) {
                    listOfNotNull(selectedMessage)
                } else {
                    visibleMessages
                }

            if (sourceMessages.isEmpty()) {
                dispatch(MainUiAction.SetExportStatus("No messages to export"))
                appLogStore.append(AppLogLevel.WARN, "Export skipped because there were no messages")
            } else {
                runCatching {
                    messageExporter.export(messages = sourceMessages, format = format)
                }.onSuccess { result ->
                    val scope = if (selectedOnly) "selected" else "loaded"
                    dispatch(
                        MainUiAction.SetExportStatus(
                            "Exported ${result.exportedCount} $scope messages to ${result.file.fileName}",
                        ),
                    )
                    appLogStore.append(
                        AppLogLevel.INFO,
                        "Exported ${result.exportedCount} messages as ${format.name} to ${result.file}",
                    )
                }.onFailure { error ->
                    dispatch(MainUiAction.SetExportStatus("Export failed: ${error.message.orEmpty()}"))
                    appLogStore.append(AppLogLevel.ERROR, "Export failed: ${error.message.orEmpty()}")
                }
            }
            Unit
        }

        Scaffold(
            topBar = {
                topBar(
                    state = state,
                    onAction = dispatch,
                )
            },
        ) { innerPadding ->
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
            ) {
                topicsPane(
                    state = state,
                    onAction = dispatch,
                    modifier = Modifier.width(260.dp).fillMaxHeight(),
                )

                VerticalDivider()

                messagesPane(
                    state = state,
                    messages = visibleMessages,
                    onAction = dispatch,
                    onExport = exportMessages,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )

                VerticalDivider()

                inspectorPane(
                    selectedMessage = selectedMessage,
                    modifier = Modifier.width(340.dp).fillMaxHeight(),
                )
            }
        }

        if (state.isProducerPanelOpen) {
            producerDialog(
                state = state,
                onAction = dispatch,
            )
        }

        if (state.isConnectionManagerOpen) {
            connectionManagerDialog(
                state = state,
                onAction = dispatch,
            )
        }

        if (state.isDiagnosticsOpen) {
            diagnosticsDialog(
                state = state,
                logStore = appLogStore,
                onAction = dispatch,
            )
        }
    }
}

private fun defaultAppDataDirectory(): Path {
    val directory = Path.of(System.getProperty("user.home"), ".light-kafka-viewer")
    directory.createDirectories()
    return directory
}

private fun logAction(
    logStore: AppLogStore,
    action: MainUiAction,
    state: MainUiState,
) {
    when (action) {
        MainUiAction.TogglePause -> {
            val status = if (state.isConsumerPaused) "paused" else "resumed"
            logStore.append(AppLogLevel.INFO, "Consumer $status")
        }

        is MainUiAction.AddHistoryEntry -> {
            val level = if (action.entry.status == SendStatus.SUCCESS) AppLogLevel.INFO else AppLogLevel.ERROR
            val message =
                if (action.entry.status == SendStatus.SUCCESS) {
                    "Produced message to ${action.entry.topic}"
                } else {
                    "Producer failure on ${action.entry.topic}: ${action.entry.errorMessage.orEmpty()}"
                }
            logStore.append(level, message)
        }

        is MainUiAction.SetActiveProfile -> {
            if (action.profileId != null) {
                logStore.append(AppLogLevel.INFO, "Active profile switched to ${action.profileId}")
            }
        }

        is MainUiAction.SetConnectionManagerOpen -> {
            if (action.open) {
                logStore.append(AppLogLevel.INFO, "Opened connection manager")
            }
        }

        is MainUiAction.SetProducerPanelOpen -> {
            if (action.open) {
                logStore.append(AppLogLevel.INFO, "Opened producer panel")
            }
        }

        is MainUiAction.SetDiagnosticsOpen -> {
            if (action.open) {
                logStore.append(AppLogLevel.INFO, "Opened diagnostics viewer")
            }
        }

        else -> Unit
    }
}
