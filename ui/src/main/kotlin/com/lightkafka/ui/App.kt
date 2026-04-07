package com.lightkafka.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lightkafka.core.kafka.ConsumedMessage
import com.lightkafka.core.kafka.ConsumerEvent
import com.lightkafka.core.kafka.ConsumerGroupDetail
import com.lightkafka.core.kafka.ConsumerGroupSummary
import com.lightkafka.core.kafka.ConsumerSessionRequest
import com.lightkafka.core.kafka.ConsumerStartPosition
import com.lightkafka.core.kafka.CreateTopicRequest
import com.lightkafka.core.kafka.DefaultKafkaAdminService
import com.lightkafka.core.kafka.DefaultKafkaConsumerService
import com.lightkafka.core.kafka.DefaultKafkaProducerService
import com.lightkafka.core.kafka.KafkaConnectionConfig
import com.lightkafka.core.kafka.KafkaResult
import com.lightkafka.core.kafka.PartitionDetail
import com.lightkafka.core.kafka.ProducerMessage
import com.lightkafka.core.kafka.ProducerSendResult
import com.lightkafka.core.kafka.TopicConfig
import com.lightkafka.core.kafka.defaultKafkaConsumerGroupClientFactory
import com.lightkafka.core.storage.SendStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createDirectories

@Composable
fun appContent() {
    MaterialTheme(
        colorScheme =
            androidx.compose.material3.darkColorScheme(
                primary = AccentViolet,
                secondary = AccentPink,
                tertiary = AccentCyan,
                surface = SurfaceCard,
                background = AppBackgroundColor,
                onSurface = TextPrimary,
                onBackground = TextPrimary,
            ),
    ) {
        val appDataDirectory = remember { defaultAppDataDirectory() }
        val messageExporter = remember { MessageExporter(exportDirectory = appDataDirectory.resolve("exports")) }
        val appLogStore = remember { AppLogStore(logFile = appDataDirectory.resolve("logs").resolve("app.log")) }
        val kraftLauncher = remember { KraftLauncher() }
        val scope = rememberCoroutineScope()
        var state by remember { mutableStateOf(initialMainUiState()) }
        var kraftStatusText by remember { mutableStateOf("KRaft: checking...") }
        var isKraftRunning by remember { mutableStateOf(false) }
        var consumerJob by remember { mutableStateOf<Job?>(null) }
        var consumerService by remember { mutableStateOf<DefaultKafkaConsumerService?>(null) }
        val dispatch = { action: MainUiAction ->
            state = reduceMainUiState(state, action)
            logAction(appLogStore, action, state)
        }
        val refreshKraftStatus: suspend () -> Unit = {
            val statusResult = withContext(Dispatchers.IO) { kraftLauncher.status() }
            kraftStatusText =
                when (statusResult.state) {
                    KraftContainerState.RUNNING -> "KRaft: running"
                    KraftContainerState.STOPPED -> "KRaft: stopped"
                    KraftContainerState.NOT_FOUND -> "KRaft: not created"
                    KraftContainerState.UNKNOWN -> "KRaft: unknown"
                }
            isKraftRunning = statusResult.state == KraftContainerState.RUNNING
            if (!statusResult.success) {
                appLogStore.append(AppLogLevel.ERROR, statusResult.message)
            }
        }
        LaunchedEffect(Unit) {
            refreshKraftStatus()
        }
        val launchKraft: () -> Unit = {
            scope.launch {
                val result = withContext(Dispatchers.IO) { kraftLauncher.launch() }
                dispatch(MainUiAction.SetExportStatus(result.message))
                val logLevel = if (result.success) AppLogLevel.INFO else AppLogLevel.ERROR
                appLogStore.append(logLevel, result.message)
                refreshKraftStatus()
            }
            Unit
        }
        val stopKraft: () -> Unit = {
            scope.launch {
                val result = withContext(Dispatchers.IO) { kraftLauncher.stop() }
                dispatch(MainUiAction.SetExportStatus(result.message))
                val logLevel = if (result.success) AppLogLevel.INFO else AppLogLevel.ERROR
                appLogStore.append(logLevel, result.message)
                refreshKraftStatus()
            }
            Unit
        }

        LaunchedEffect(state.activeProfileId) {
            val activeProfile = state.activeProfile()
            if (activeProfile == null) {
                dispatch(MainUiAction.SetTopics(emptyList()))
                dispatch(MainUiAction.SelectTopic(null))
                dispatch(MainUiAction.ClearMessages)
                dispatch(MainUiAction.SetConsumerGroups(emptyList()))
                dispatch(MainUiAction.SelectConsumerGroup(null))
                dispatch(MainUiAction.SetConsumerGroupDetail(null))
                dispatch(MainUiAction.SetConsumerGroupsError(null))
                return@LaunchedEffect
            }

            when (val topicsResult = withContext(Dispatchers.IO) { loadTopicsFromKafka(activeProfile.bootstrapServers) }) {
                is KafkaResult.Success -> {
                    dispatch(MainUiAction.SetTopics(topicsResult.value))
                    val nextTopic =
                        state.selectedTopic
                            ?.takeIf { topicsResult.value.contains(it) }
                            ?: topicsResult.value.firstOrNull()
                    dispatch(MainUiAction.SelectTopic(nextTopic))
                    dispatch(MainUiAction.ClearMessages)
                    appLogStore.append(
                        AppLogLevel.INFO,
                        "Loaded ${topicsResult.value.size} topic(s) for profile ${activeProfile.name}",
                    )
                }

                is KafkaResult.Failure -> {
                    val message = "Failed to load topics: ${formatConnectionError(topicsResult.error)}"
                    dispatch(MainUiAction.SetTopics(emptyList()))
                    dispatch(MainUiAction.SelectTopic(null))
                    dispatch(MainUiAction.ClearMessages)
                    dispatch(MainUiAction.SetExportStatus(message))
                    appLogStore.append(AppLogLevel.ERROR, message)
                }
            }

            dispatch(MainUiAction.SetConsumerGroupsLoading(true))
            val groupsResult = withContext(Dispatchers.IO) { listConsumerGroupsFromKafka(activeProfile.bootstrapServers) }
            if (groupsResult.isSuccess) {
                dispatch(MainUiAction.SetConsumerGroups(groupsResult.getOrDefault(emptyList())))
                dispatch(MainUiAction.SetConsumerGroupDetail(null))
            } else {
                dispatch(MainUiAction.SetConsumerGroups(emptyList()))
                dispatch(MainUiAction.SetConsumerGroupDetail(null))
                dispatch(MainUiAction.SetConsumerGroupsError(groupsResult.exceptionOrNull()?.message ?: "Unknown error"))
            }
        }

        // Manage consumer session based on active profile and selected topic
        LaunchedEffect(state.activeProfileId, state.selectedTopic) {
            consumerJob?.cancel()
            consumerService?.close()
            consumerService = null

            val activeProfile = state.profiles.firstOrNull { it.id == state.activeProfileId }
            val topic = state.selectedTopic

            if (activeProfile != null && topic != null) {
                val config = KafkaConnectionConfig(bootstrapServers = activeProfile.bootstrapServers)
                val service = DefaultKafkaConsumerService(config)
                consumerService = service

                appLogStore.append(AppLogLevel.INFO, "Starting consumer session for topic '$topic'")
                consumerJob =
                    launch {
                        service
                            .startSession(
                                ConsumerSessionRequest(
                                    topic = topic,
                                    startPosition = ConsumerStartPosition.Latest,
                                ),
                            ).onEach { event ->
                                when (event) {
                                    is ConsumerEvent.MessageReceived -> {
                                        dispatch(MainUiAction.AddMessages(listOf(event.message)))
                                    }
                                    is ConsumerEvent.Error -> {
                                        appLogStore.append(
                                            AppLogLevel.ERROR,
                                            "Consumer error: ${event.error}",
                                        )
                                    }
                                    is ConsumerEvent.Stats -> Unit
                                }
                            }.catch { error ->
                                appLogStore.append(
                                    AppLogLevel.ERROR,
                                    "Consumer session failed: ${error.message}",
                                )
                            }.collect {}
                    }
            }
        }

        // Handle pause/resume
        LaunchedEffect(state.isConsumerPaused) {
            val service = consumerService
            if (service != null) {
                try {
                    if (state.isConsumerPaused) {
                        service.pause()
                        appLogStore.append(AppLogLevel.INFO, "Consumer paused")
                    } else {
                        service.resume()
                        appLogStore.append(AppLogLevel.INFO, "Consumer resumed")
                    }
                } catch (e: Exception) {
                    appLogStore.append(AppLogLevel.ERROR, "Failed to toggle pause: ${e.message}")
                }
            }
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
        val createTopic: (String) -> Unit = createTopic@{ topicName ->
            val normalizedTopicName = topicName.trim()
            if (normalizedTopicName.isBlank()) {
                dispatch(MainUiAction.SetExportStatus("Topic name cannot be empty"))
                appLogStore.append(AppLogLevel.WARN, "Create topic skipped because topic name is empty")
                return@createTopic
            }

            val activeProfile = state.profiles.firstOrNull { it.id == state.activeProfileId }
            if (activeProfile == null) {
                dispatch(MainUiAction.SetExportStatus("Select or create a connection profile first"))
                appLogStore.append(AppLogLevel.WARN, "Create topic skipped because no active profile is selected")
                return@createTopic
            }

            scope.launch {
                val result =
                    withContext(Dispatchers.IO) {
                        createTopicInKafka(
                            bootstrapServers = activeProfile.bootstrapServers,
                            topicName = normalizedTopicName,
                        )
                    }

                when (result) {
                    is KafkaResult.Success -> {
                        dispatch(MainUiAction.AddTopic(normalizedTopicName))
                        dispatch(MainUiAction.SelectTopic(normalizedTopicName))
                        dispatch(MainUiAction.SetExportStatus("Topic '$normalizedTopicName' created"))
                        appLogStore.append(AppLogLevel.INFO, "Created topic '$normalizedTopicName'")
                    }

                    is KafkaResult.Failure -> {
                        val message = "Create topic failed: ${formatConnectionError(result.error)}"
                        dispatch(MainUiAction.SetExportStatus(message))
                        appLogStore.append(AppLogLevel.ERROR, message)
                    }
                }
            }
            Unit
        }
        val deleteTopic: (String) -> Unit = deleteTopic@{ topicName ->
            val activeProfile = state.profiles.firstOrNull { it.id == state.activeProfileId }
            if (activeProfile == null) {
                dispatch(MainUiAction.SetExportStatus("Select or create a connection profile first"))
                appLogStore.append(AppLogLevel.WARN, "Delete topic skipped because no active profile is selected")
                return@deleteTopic
            }

            scope.launch {
                val result =
                    withContext(Dispatchers.IO) {
                        deleteTopicInKafka(
                            bootstrapServers = activeProfile.bootstrapServers,
                            topicName = topicName,
                        )
                    }

                when (result) {
                    is KafkaResult.Success -> {
                        dispatch(MainUiAction.RemoveTopic(topicName))
                        dispatch(MainUiAction.SetExportStatus("Topic '$topicName' deleted"))
                        appLogStore.append(AppLogLevel.INFO, "Deleted topic '$topicName'")
                    }

                    is KafkaResult.Failure -> {
                        val message = "Delete topic failed: ${formatConnectionError(result.error)}"
                        dispatch(MainUiAction.SetExportStatus(message))
                        appLogStore.append(AppLogLevel.ERROR, message)
                    }
                }
            }
            Unit
        }
        val sendProducerMessage: () -> Unit = sendProducerMessage@{
            val draft = state.producerDraft
            val topic = draft.topic.ifBlank { state.selectedTopic ?: "" }.trim()
            val partition = draft.partitionText.trim().toIntOrNull()
            val value = draft.value
            val activeProfile = state.profiles.firstOrNull { it.id == state.activeProfileId }
            val now = System.currentTimeMillis()

            if (topic.isBlank()) {
                dispatch(
                    MainUiAction.AddHistoryEntry(
                        createHistoryEntry(
                            state = state,
                            topic = "unknown",
                            partition = partition,
                            status = SendStatus.FAILURE,
                            timestampEpochMillis = now,
                            errorMessage = "Topic cannot be empty",
                        ),
                    ),
                )
                dispatch(MainUiAction.SetExportStatus("Producer failure: topic cannot be empty"))
                return@sendProducerMessage
            }

            if (value.isBlank()) {
                dispatch(
                    MainUiAction.AddHistoryEntry(
                        createHistoryEntry(
                            state = state,
                            topic = topic,
                            partition = partition,
                            status = SendStatus.FAILURE,
                            timestampEpochMillis = now,
                            errorMessage = "Value cannot be empty",
                        ),
                    ),
                )
                dispatch(MainUiAction.SetExportStatus("Producer failure on $topic: value cannot be empty"))
                return@sendProducerMessage
            }

            if (activeProfile == null) {
                dispatch(
                    MainUiAction.AddHistoryEntry(
                        createHistoryEntry(
                            state = state,
                            topic = topic,
                            partition = partition,
                            status = SendStatus.FAILURE,
                            timestampEpochMillis = now,
                            errorMessage = "No active connection profile",
                        ),
                    ),
                )
                dispatch(MainUiAction.SetExportStatus("Producer failure on $topic: select a connection profile"))
                return@sendProducerMessage
            }

            scope.launch {
                val producerMessage =
                    ProducerMessage(
                        topic = topic,
                        partition = partition,
                        key = draft.key.ifBlank { null }?.encodeToByteArray(),
                        value = value.encodeToByteArray(),
                        headers = parseHeaders(draft.headersText),
                    )

                val result =
                    withContext(Dispatchers.IO) {
                        sendMessageToKafka(
                            bootstrapServers = activeProfile.bootstrapServers,
                            message = producerMessage,
                        )
                    }

                val timestamp = System.currentTimeMillis()
                when (result) {
                    is KafkaResult.Success -> {
                        dispatch(
                            MainUiAction.AddHistoryEntry(
                                createHistoryEntry(
                                    state = state,
                                    topic = topic,
                                    partition = result.value.partition,
                                    status = SendStatus.SUCCESS,
                                    timestampEpochMillis = timestamp,
                                ),
                            ),
                        )
                        dispatch(
                            MainUiAction.AddMessages(
                                listOf(
                                    ConsumedMessage(
                                        topic = result.value.topic,
                                        partition = result.value.partition,
                                        offset = result.value.offset,
                                        timestamp = result.value.timestamp,
                                        key = producerMessage.key,
                                        value = producerMessage.value,
                                        headers = producerMessage.headers,
                                    ),
                                ),
                            ),
                        )
                        dispatch(MainUiAction.SelectTopic(result.value.topic))
                        dispatch(MainUiAction.SetExportStatus("Produced message to ${result.value.topic}"))
                    }

                    is KafkaResult.Failure -> {
                        val errorMessage = formatConnectionError(result.error)
                        dispatch(
                            MainUiAction.AddHistoryEntry(
                                createHistoryEntry(
                                    state = state,
                                    topic = topic,
                                    partition = partition,
                                    status = SendStatus.FAILURE,
                                    timestampEpochMillis = timestamp,
                                    errorMessage = errorMessage,
                                ),
                            ),
                        )
                        dispatch(MainUiAction.SetExportStatus("Producer failure on $topic: $errorMessage"))
                    }
                }
            }
            Unit
        }
        val refreshConsumerGroups: () -> Unit = refreshConsumerGroups@{
            val activeProfile = state.activeProfile()
            if (activeProfile == null) {
                dispatch(MainUiAction.SetConsumerGroupsError("Select or create a connection profile first"))
                return@refreshConsumerGroups
            }
            scope.launch {
                dispatch(MainUiAction.SetConsumerGroupsLoading(true))
                val result = withContext(Dispatchers.IO) { listConsumerGroupsFromKafka(activeProfile.bootstrapServers) }
                if (result.isSuccess) {
                    dispatch(MainUiAction.SetConsumerGroups(result.getOrDefault(emptyList())))
                    dispatch(MainUiAction.SetConsumerGroupDetail(null))
                } else {
                    dispatch(MainUiAction.SetConsumerGroupsError(result.exceptionOrNull()?.message ?: "Unknown error"))
                }
            }
            Unit
        }
        val selectConsumerGroup: (String) -> Unit = selectConsumerGroup@{ groupId ->
            dispatch(MainUiAction.SelectConsumerGroup(groupId))
            val activeProfile = state.activeProfile() ?: return@selectConsumerGroup
            scope.launch {
                val detailResult =
                    withContext(Dispatchers.IO) {
                        describeConsumerGroupFromKafka(
                            bootstrapServers = activeProfile.bootstrapServers,
                            groupId = groupId,
                        )
                    }
                if (detailResult.isSuccess) {
                    dispatch(MainUiAction.SetConsumerGroupDetail(detailResult.getOrNull()))
                } else {
                    dispatch(
                        MainUiAction.SetConsumerGroupsError(
                            detailResult.exceptionOrNull()?.message ?: "Unknown error",
                        ),
                    )
                }
            }
        }
        val deleteConsumerGroup: (String) -> Unit = deleteConsumerGroup@{ groupId ->
            val activeProfile = state.activeProfile() ?: return@deleteConsumerGroup
            scope.launch {
                val result =
                    withContext(Dispatchers.IO) {
                        deleteConsumerGroupFromKafka(
                            bootstrapServers = activeProfile.bootstrapServers,
                            groupId = groupId,
                        )
                    }
                if (result.isSuccess) {
                    dispatch(MainUiAction.RemoveConsumerGroup(groupId))
                    dispatch(MainUiAction.SetExportStatus("Consumer group '$groupId' deleted"))
                } else {
                    dispatch(
                        MainUiAction.SetConsumerGroupsError(
                            result.exceptionOrNull()?.message ?: "Unknown error",
                        ),
                    )
                }
            }
        }

        Scaffold(
            containerColor = AppBackgroundColor,
            topBar = {
                topBar(
                    state = state,
                    onAction = dispatch,
                    kraftStatus = kraftStatusText,
                    isKraftRunning = isKraftRunning,
                    onLaunchKraft = launchKraft,
                    onStopKraft = stopKraft,
                )
            },
        ) { innerPadding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(AppBackgroundColor),
            ) {
                messagesPane(
                    state = state,
                    messages = visibleMessages,
                    selectedMessage = selectedMessage,
                    onAction = dispatch,
                    onCreateTopic = createTopic,
                    onDeleteTopic = deleteTopic,
                    onExport = exportMessages,
                    onRefreshConsumerGroups = refreshConsumerGroups,
                    onSelectConsumerGroup = selectConsumerGroup,
                    onDeleteConsumerGroup = deleteConsumerGroup,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (state.isProducerPanelOpen) {
            producerDialog(
                state = state,
                onAction = dispatch,
                onSend = sendProducerMessage,
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

private suspend fun createTopicInKafka(
    bootstrapServers: List<String>,
    topicName: String,
): KafkaResult<Unit> {
    val adminService = DefaultKafkaAdminService(KafkaConnectionConfig(bootstrapServers = bootstrapServers))
    return try {
        adminService.createTopic(CreateTopicRequest(name = topicName, partitions = 1))
    } finally {
        adminService.close()
    }
}

private suspend fun deleteTopicInKafka(
    bootstrapServers: List<String>,
    topicName: String,
): KafkaResult<Unit> {
    val adminService = DefaultKafkaAdminService(KafkaConnectionConfig(bootstrapServers = bootstrapServers))
    return try {
        adminService.deleteTopic(topicName)
    } finally {
        adminService.close()
    }
}

private suspend fun loadTopicsFromKafka(bootstrapServers: List<String>): KafkaResult<List<String>> {
    val adminService = DefaultKafkaAdminService(KafkaConnectionConfig(bootstrapServers = bootstrapServers))
    return try {
        when (val result = adminService.listTopics(includeInternal = false)) {
            is KafkaResult.Success -> KafkaResult.Success(result.value.map { it.name }.sorted())
            is KafkaResult.Failure -> result
        }
    } finally {
        adminService.close()
    }
}

private suspend fun listConsumerGroupsFromKafka(bootstrapServers: List<String>): Result<List<ConsumerGroupSummary>> {
    val client = defaultKafkaConsumerGroupClientFactory.create(KafkaConnectionConfig(bootstrapServers = bootstrapServers))
    return try {
        Result.success(client.listGroups().sortedBy { it.groupId })
    } catch (error: Exception) {
        Result.failure(error)
    } finally {
        client.close()
    }
}

private suspend fun describeConsumerGroupFromKafka(
    bootstrapServers: List<String>,
    groupId: String,
): Result<ConsumerGroupDetail?> {
    val client = defaultKafkaConsumerGroupClientFactory.create(KafkaConnectionConfig(bootstrapServers = bootstrapServers))
    return try {
        Result.success(client.describeGroup(groupId))
    } catch (error: Exception) {
        Result.failure(error)
    } finally {
        client.close()
    }
}

private suspend fun deleteConsumerGroupFromKafka(
    bootstrapServers: List<String>,
    groupId: String,
): Result<Unit> {
    val client = defaultKafkaConsumerGroupClientFactory.create(KafkaConnectionConfig(bootstrapServers = bootstrapServers))
    return try {
        client.deleteGroup(groupId)
        Result.success(Unit)
    } catch (error: Exception) {
        Result.failure(error)
    } finally {
        client.close()
    }
}

private suspend fun sendMessageToKafka(
    bootstrapServers: List<String>,
    message: ProducerMessage,
): KafkaResult<ProducerSendResult> {
    val producerService = DefaultKafkaProducerService(KafkaConnectionConfig(bootstrapServers = bootstrapServers))
    return try {
        producerService.send(message)
    } finally {
        producerService.close()
    }
}

private fun parseHeaders(headersText: String): Map<String, ByteArray?> =
    headersText
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val delimiterIndex = line.indexOf('=')
            if (delimiterIndex <= 0) {
                null
            } else {
                val key = line.substring(0, delimiterIndex).trim()
                if (key.isEmpty()) {
                    null
                } else {
                    val value = line.substring(delimiterIndex + 1)
                    key to value.encodeToByteArray()
                }
            }
        }.toMap()

private fun createHistoryEntry(
    state: MainUiState,
    topic: String,
    partition: Int?,
    status: SendStatus,
    timestampEpochMillis: Long,
    errorMessage: String? = null,
) = com.lightkafka.core.storage.SendHistoryEntry(
    id = UUID.randomUUID().toString(),
    profileId = state.activeProfileId ?: "unknown",
    topic = topic,
    partition = partition,
    status = status,
    timestampEpochMillis = timestampEpochMillis,
    errorMessage = errorMessage,
)

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
