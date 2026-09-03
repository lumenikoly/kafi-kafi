@file:Suppress("TooManyFunctions", "LongParameterList")

package com.lightkafka.ui.topic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightkafka.core.kafka.ConsumedMessage
import com.lightkafka.core.kafka.ConsumerEvent
import com.lightkafka.core.kafka.ConsumerSessionRequest
import com.lightkafka.core.kafka.ConsumerStartPosition
import com.lightkafka.core.kafka.DefaultKafkaConsumerService
import com.lightkafka.core.kafka.DefaultKafkaProducerService
import com.lightkafka.core.kafka.KafkaConsumerService
import com.lightkafka.core.kafka.KafkaResult
import com.lightkafka.core.kafka.ProducerMessage
import com.lightkafka.core.kafka.ProducerSendResult
import com.lightkafka.core.storage.AppSettings
import com.lightkafka.core.storage.DefaultConsumerStartPosition
import com.lightkafka.ui.connection.AppConnectionState
import com.lightkafka.ui.connection.toKafkaConnectionConfig
import com.lightkafka.ui.infra.AccentViolet
import com.lightkafka.ui.infra.BorderSubtle
import com.lightkafka.ui.infra.StatusError
import com.lightkafka.ui.infra.StatusErrorBg
import com.lightkafka.ui.infra.StatusSuccess
import com.lightkafka.ui.infra.StatusWarning
import com.lightkafka.ui.infra.SurfaceElevated
import com.lightkafka.ui.infra.TextMuted
import com.lightkafka.ui.infra.TextPrimary
import com.lightkafka.ui.infra.TextSecondary
import com.lightkafka.ui.infra.formatKafkaError
import com.lightkafka.ui.infra.formatTimestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MESSAGE_TRIM_BATCH = 1_000

// ── Types ──────────────────────────────────────────────────────────────────

internal enum class StartPositionOption(
    val label: String,
) {
    LATEST("Latest"),
    EARLIEST("Earliest"),
    OFFSET("Specific Offset"),
    TIMESTAMP("Timestamp"),
}

internal data class ConsumerSessionState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val messageCount: Int = 0,
    val errorMessage: String? = null,
)

internal data class MessageFilterState(
    val keyFilter: String = "",
    val valueFilter: String = "",
    val partitionFilter: Int = -1,
)

internal data class StartPositionState(
    val option: StartPositionOption = StartPositionOption.LATEST,
    val offsetText: String = "0",
    val timestampText: String = "",
)

internal data class MessageId(
    val partition: Int,
    val offset: Long,
)

internal sealed interface SendStatus {
    data class Success(
        val result: ProducerSendResult,
    ) : SendStatus

    data class Error(
        val message: String,
    ) : SendStatus
}

internal data class MessageComposerState(
    val isVisible: Boolean = false,
    val key: String = "",
    val value: String = "",
    val partitionText: String = "",
    val isSending: Boolean = false,
    val status: SendStatus? = null,
)

/** Aggregates action callbacks to keep composable signatures under detekt limits. */
internal data class MessagesTabCallbacks(
    val onPositionChanged: (StartPositionState) -> Unit = {},
    val onFilterChanged: (MessageFilterState) -> Unit = {},
    val onStart: () -> Unit = {},
    val onPause: () -> Unit = {},
    val onResume: () -> Unit = {},
    val onStop: () -> Unit = {},
    val onToggleExpand: (MessageId) -> Unit = {},
    val onConsumerCreated: (KafkaConsumerService) -> Unit = {},
    val onMessagesCleared: () -> Unit = {},
    val onSessionStateChanged: (ConsumerSessionState) -> Unit = {},
    val onMessageReceived: (ConsumedMessage) -> Unit = {},
    val onComposerChanged: (MessageComposerState) -> Unit = {},
    val onSend: () -> Unit = {},
)

// ── Main Composable ────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming", "TooManyFunctions")
@Composable
fun TopicMessagesTab(
    connectionStateFlow: MutableStateFlow<AppConnectionState>,
    settingsStateFlow: MutableStateFlow<AppSettings>,
    topicName: String,
    partitionCount: Int,
    modifier: Modifier = Modifier,
) {
    val connectionState by connectionStateFlow.collectAsState()
    val settings by settingsStateFlow.collectAsState()
    val profile = connectionState.activeProfile
    var consumer by remember { mutableStateOf<KafkaConsumerService?>(null) }
    var sessionState by remember { mutableStateOf(ConsumerSessionState()) }
    val messages = remember { mutableStateListOf<ConsumedMessage>() }
    var startKey by remember { mutableStateOf(0) }
    var positionState by
        remember(topicName) {
            mutableStateOf(StartPositionState(option = settings.defaultConsumerStartPosition.toStartPositionOption()))
        }
    var filterState by remember { mutableStateOf(MessageFilterState()) }
    var expandedMessageId by remember { mutableStateOf<MessageId?>(null) }
    var composerState by remember { mutableStateOf(MessageComposerState()) }
    val scope = rememberCoroutineScope()
    val producer = remember(profile?.id) { profile?.let { DefaultKafkaProducerService(it.toKafkaConnectionConfig()) } }
    CloseProducerOnDispose(producer)
    val callbacks =
        MessagesTabCallbacks(
            onPositionChanged = { positionState = it },
            onFilterChanged = { filterState = it },
            onStart = {
                val error = validateStart(profile != null, positionState)
                if (error == null) {
                    startKey++
                } else {
                    sessionState = sessionState.copy(errorMessage = error)
                }
            },
            onPause = handlePause(consumer, sessionState, scope) { sessionState = it },
            onResume = handleResume(consumer, sessionState, scope) { sessionState = it },
            onStop = handleStop(consumer, scope) { sessionState = it },
            onToggleExpand = { expandedMessageId = if (expandedMessageId == it) null else it },
            onConsumerCreated = { consumer = it },
            onMessagesCleared = { messages.clear() },
            onSessionStateChanged = {
                sessionState = if (it.isRunning) it.copy(isPaused = sessionState.isPaused) else it
            },
            onMessageReceived = {
                appendMessage(messages, it, settings.messageBufferLimit)
            },
            onComposerChanged = { composerState = it },
            onSend = {
                val producerService = producer
                val error = validateMessage(composerState, partitionCount, producerService != null)
                if (error != null) {
                    composerState = composerState.copy(status = SendStatus.Error(error))
                } else {
                    scope.launch {
                        composerState = composerState.copy(isSending = true, status = null)
                        val message = buildProducerMessage(topicName, composerState)
                        when (val result = checkNotNull(producerService).send(message)) {
                            is KafkaResult.Success ->
                                composerState =
                                    composerState.copy(
                                        value = "",
                                        isSending = false,
                                        status = SendStatus.Success(result.value),
                                    )
                            is KafkaResult.Failure ->
                                composerState =
                                    composerState.copy(
                                        isSending = false,
                                        status = SendStatus.Error(formatKafkaError(result.error)),
                                    )
                        }
                    }
                }
            },
        )

    if (startKey > 0) {
        ConsumerSessionLaunchedEffect(
            startKey = startKey,
            profile = profile,
            topicName = topicName,
            positionState = positionState,
            partitionFilter = filterState.partitionFilter,
            partitionCount = partitionCount,
            callbacks = callbacks,
        )
    }

    TopicMessagesLayout(
        sessionState = sessionState,
        positionState = positionState,
        filterState = filterState,
        composerState = composerState,
        messages = messages,
        expandedMessageId = expandedMessageId,
        partitionCount = partitionCount,
        hasProfile = profile != null,
        callbacks = callbacks,
        modifier = modifier,
    )
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun CloseProducerOnDispose(producer: DefaultKafkaProducerService?) {
    LaunchedEffect(producer) {
        try {
            awaitCancellation()
        } finally {
            withContext(NonCancellable) { producer?.close() }
        }
    }
}

private fun DefaultConsumerStartPosition.toStartPositionOption(): StartPositionOption =
    when (this) {
        DefaultConsumerStartPosition.LATEST -> StartPositionOption.LATEST
        DefaultConsumerStartPosition.EARLIEST -> StartPositionOption.EARLIEST
    }

internal fun appendMessage(
    messages: MutableList<ConsumedMessage>,
    message: ConsumedMessage,
    limit: Int,
) {
    val trimCount = maxOf(messages.size - limit + 1, minOf(MESSAGE_TRIM_BATCH, maxOf(1, limit / 10)))
    if (messages.size >= limit) messages.subList(0, trimCount).clear()
    messages.add(message)
}

private fun handlePause(
    consumer: KafkaConsumerService?,
    state: ConsumerSessionState,
    scope: kotlinx.coroutines.CoroutineScope,
    setState: (ConsumerSessionState) -> Unit,
): () -> Unit =
    {
        consumer?.let { c ->
            scope.launch { c.pause() }
            setState(state.copy(isPaused = true))
        }
    }

private fun handleResume(
    consumer: KafkaConsumerService?,
    state: ConsumerSessionState,
    scope: kotlinx.coroutines.CoroutineScope,
    setState: (ConsumerSessionState) -> Unit,
): () -> Unit =
    {
        consumer?.let { c ->
            scope.launch { c.resume() }
            setState(state.copy(isPaused = false))
        }
    }

private fun handleStop(
    consumer: KafkaConsumerService?,
    scope: kotlinx.coroutines.CoroutineScope,
    setState: (ConsumerSessionState) -> Unit,
): () -> Unit =
    {
        consumer?.let { c ->
            scope.launch { c.stop() }
            setState(ConsumerSessionState())
        }
    }

// ── Layout ─────────────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun TopicMessagesLayout(
    sessionState: ConsumerSessionState,
    positionState: StartPositionState,
    filterState: MessageFilterState,
    composerState: MessageComposerState,
    messages: List<ConsumedMessage>,
    expandedMessageId: MessageId?,
    partitionCount: Int,
    hasProfile: Boolean,
    callbacks: MessagesTabCallbacks,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = sessionState.errorMessage != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            ErrorBanner(error = sessionState.errorMessage.orEmpty())
        }

        MessageControls(
            positionState = positionState,
            onPositionChanged = callbacks.onPositionChanged,
            filterState = filterState,
            onFilterChanged = callbacks.onFilterChanged,
            sessionState = sessionState,
            partitionCount = partitionCount,
            hasProfile = hasProfile,
            composerVisible = composerState.isVisible,
            onToggleComposer = {
                callbacks.onComposerChanged(composerState.copy(isVisible = !composerState.isVisible, status = null))
            },
            callbacks = callbacks,
        )
        HorizontalDivider(color = BorderSubtle)
        AnimatedVisibility(
            visible = composerState.isVisible,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            MessageComposer(
                state = composerState,
                partitionCount = partitionCount,
                onStateChanged = callbacks.onComposerChanged,
                onSend = callbacks.onSend,
            )
        }
        if (composerState.isVisible) {
            HorizontalDivider(color = BorderSubtle)
        }
        MessageFilterBar(filterState = filterState, onFilterChanged = callbacks.onFilterChanged)
        HorizontalDivider(color = BorderSubtle)

        val filtered by remember(messages, filterState) {
            derivedStateOf { applyFilters(messages, filterState) }
        }
        MessageListContent(
            filteredMessages = filtered,
            isRunning = sessionState.isRunning,
            totalMessageCount = sessionState.messageCount,
            expandedMessageId = expandedMessageId,
            onToggleExpand = callbacks.onToggleExpand,
        )
    }
}

// ── Filter logic ───────────────────────────────────────────────────────────

private fun applyFilters(
    messages: List<ConsumedMessage>,
    filter: MessageFilterState,
): List<ConsumedMessage> =
    messages.filter { msg ->
        matchesText(msg.key, filter.keyFilter) &&
            matchesText(msg.value, filter.valueFilter) &&
            (filter.partitionFilter < 0 || msg.partition == filter.partitionFilter)
    }

private fun matchesText(
    bytes: ByteArray?,
    pattern: String,
): Boolean {
    if (pattern.isEmpty()) return true
    return bytes
        ?.toString(Charsets.UTF_8)
        ?.contains(pattern, ignoreCase = true) ?: false
}

// ── Consumer Session Effect ────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun ConsumerSessionLaunchedEffect(
    startKey: Int,
    profile: com.lightkafka.core.storage.ClusterProfile?,
    topicName: String,
    positionState: StartPositionState,
    partitionFilter: Int,
    partitionCount: Int,
    callbacks: MessagesTabCallbacks,
) {
    LaunchedEffect(startKey, profile?.id, topicName) {
        val connConfig = (profile ?: return@LaunchedEffect).toKafkaConnectionConfig()
        val service = DefaultKafkaConsumerService(connConfig)
        callbacks.onConsumerCreated(service)
        callbacks.onMessagesCleared()
        callbacks.onSessionStateChanged(ConsumerSessionState())

        val request = buildSessionRequest(topicName, positionState, partitionFilter, partitionCount)
        callbacks.onSessionStateChanged(ConsumerSessionState(isRunning = true))
        collectEvents(service, request, callbacks.onMessageReceived, callbacks.onSessionStateChanged)
    }
}

internal fun buildSessionRequest(
    topicName: String,
    state: StartPositionState,
    partitionFilter: Int,
    partitionCount: Int,
): ConsumerSessionRequest {
    val selectedPartitions =
        if (partitionFilter >= 0) {
            setOf(partitionFilter)
        } else {
            (0 until partitionCount).toSet()
        }
    val partitions =
        if (partitionFilter >= 0 || state.option == StartPositionOption.OFFSET) selectedPartitions else null
    return ConsumerSessionRequest(
        topic = topicName,
        partitions = partitions,
        startPosition = resolveStartPosition(state, selectedPartitions),
    )
}

private fun resolveStartPosition(
    state: StartPositionState,
    partitions: Set<Int>,
): ConsumerStartPosition =
    when (state.option) {
        StartPositionOption.LATEST -> ConsumerStartPosition.Latest
        StartPositionOption.EARLIEST -> ConsumerStartPosition.Earliest
        StartPositionOption.OFFSET -> {
            val offset = checkNotNull(state.offsetText.toLongOrNull())
            ConsumerStartPosition.SpecificOffsets(partitions.associateWith { offset })
        }
        StartPositionOption.TIMESTAMP -> {
            val ts = checkNotNull(state.timestampText.toLongOrNull())
            ConsumerStartPosition.Timestamp(ts)
        }
    }

private fun validateStart(
    hasProfile: Boolean,
    state: StartPositionState,
): String? =
    when {
        !hasProfile -> "Connect to a Kafka cluster before starting the consumer."
        state.option == StartPositionOption.OFFSET &&
            (state.offsetText.toLongOrNull() == null || state.offsetText.toLong() < 0) ->
            "Offset must be a non-negative integer."
        state.option == StartPositionOption.TIMESTAMP &&
            (state.timestampText.toLongOrNull() == null || state.timestampText.toLong() < 0) ->
            "Timestamp must be epoch milliseconds."
        else -> null
    }

internal fun buildProducerMessage(
    topicName: String,
    state: MessageComposerState,
): ProducerMessage =
    ProducerMessage(
        topic = topicName,
        key = state.key.takeIf(String::isNotEmpty)?.encodeToByteArray(),
        value = state.value.encodeToByteArray(),
        partition = state.partitionText.toIntOrNull(),
    )

private fun validateMessage(
    state: MessageComposerState,
    partitionCount: Int,
    hasProducer: Boolean,
): String? {
    val partition = state.partitionText.toIntOrNull()
    return when {
        !hasProducer -> "Connect to a Kafka cluster before sending."
        state.partitionText.isNotBlank() && partition == null -> "Partition must be an integer."
        partition != null && partition !in 0 until partitionCount ->
            "Partition must be between 0 and ${partitionCount - 1}."
        else -> null
    }
}

private suspend fun collectEvents(
    service: KafkaConsumerService,
    request: ConsumerSessionRequest,
    onMessage: (ConsumedMessage) -> Unit,
    onState: (ConsumerSessionState) -> Unit,
) {
    var count = 0
    var terminalError: String? = null
    try {
        service.startSession(request).collect { event ->
            when (event) {
                is ConsumerEvent.MessageReceived -> {
                    onMessage(event.message)
                    count++
                    onState(ConsumerSessionState(isRunning = true, messageCount = count))
                }
                is ConsumerEvent.Stats -> Unit
                is ConsumerEvent.Error -> {
                    onState(
                        ConsumerSessionState(
                            isRunning = true,
                            messageCount = count,
                            errorMessage = formatKafkaError(event.error),
                        ),
                    )
                }
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        terminalError = error.message ?: "Consumer stopped unexpectedly."
    } finally {
        onState(ConsumerSessionState(isRunning = false, messageCount = count, errorMessage = terminalError))
    }
}

// ── Message List Content ───────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun MessageListContent(
    filteredMessages: List<ConsumedMessage>,
    isRunning: Boolean,
    totalMessageCount: Int,
    expandedMessageId: MessageId?,
    onToggleExpand: (MessageId) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (filteredMessages.isEmpty() && isRunning && totalMessageCount == 0) {
        EmptyState("Waiting for messages…", modifier)
    } else if (filteredMessages.isEmpty() && !isRunning) {
        EmptyState("No messages. Select a start position and click Start.", modifier)
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            MessageListHeader()
            HorizontalDivider(color = BorderSubtle)
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredMessages, key = { "${it.partition}-${it.offset}" }) { message ->
                    val messageId = MessageId(message.partition, message.offset)
                    MessageRow(
                        message = message,
                        isExpanded = expandedMessageId == messageId,
                        onToggleExpand = { onToggleExpand(messageId) },
                    )
                    HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun MessageListHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().background(SurfaceElevated).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TableHeader("Offset", Modifier.width(80.dp))
        TableHeader("Part.", Modifier.width(36.dp))
        TableHeader("Timestamp", Modifier.width(140.dp))
        TableHeader("Key", Modifier.weight(0.25f))
        TableHeader("Value", Modifier.weight(0.5f))
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun TableHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = modifier)
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun ErrorBanner(
    error: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(StatusErrorBg)
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text = error, style = MaterialTheme.typography.bodySmall, color = StatusError)
    }
}

// ── Message Controls ───────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun MessageControls(
    positionState: StartPositionState,
    onPositionChanged: (StartPositionState) -> Unit,
    filterState: MessageFilterState,
    onFilterChanged: (MessageFilterState) -> Unit,
    sessionState: ConsumerSessionState,
    partitionCount: Int,
    hasProfile: Boolean,
    composerVisible: Boolean,
    onToggleComposer: () -> Unit,
    callbacks: MessagesTabCallbacks,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StartPositionDropdown(
                selected = positionState.option,
                onSelected = { onPositionChanged(positionState.copy(option = it)) },
                enabled = !sessionState.isRunning,
                modifier = Modifier.width(180.dp),
            )
            StartPositionInput(
                state = positionState,
                onStateChanged = onPositionChanged,
                enabled = !sessionState.isRunning,
            )
            PartitionDropdown(
                selected = filterState.partitionFilter,
                partitionCount = partitionCount,
                enabled = !sessionState.isRunning,
                onSelected = { onFilterChanged(filterState.copy(partitionFilter = it)) },
                modifier = Modifier.width(150.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = onToggleComposer,
                enabled = hasProfile,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentViolet),
            ) {
                Text(if (composerVisible) "Close composer" else "Produce")
            }
            SessionActionButtons(
                isRunning = sessionState.isRunning,
                isPaused = sessionState.isPaused,
                startEnabled = hasProfile,
                onStart = callbacks.onStart,
                onPause = callbacks.onPause,
                onResume = callbacks.onResume,
                onStop = callbacks.onStop,
            )
        }
        if (sessionState.messageCount > 0 || sessionState.isRunning) {
            StatsRow(messageCount = sessionState.messageCount, isPaused = sessionState.isPaused)
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun StartPositionInput(
    state: StartPositionState,
    onStateChanged: (StartPositionState) -> Unit,
    enabled: Boolean,
) {
    when (state.option) {
        StartPositionOption.OFFSET ->
            OutlinedTextField(
                value = state.offsetText,
                onValueChange = { onStateChanged(state.copy(offsetText = it)) },
                enabled = enabled,
                label = { Text("Offset") },
                singleLine = true,
                modifier = Modifier.width(130.dp),
            )
        StartPositionOption.TIMESTAMP ->
            OutlinedTextField(
                value = state.timestampText,
                onValueChange = { onStateChanged(state.copy(timestampText = it)) },
                enabled = enabled,
                label = { Text("Epoch ms") },
                singleLine = true,
                modifier = Modifier.width(170.dp),
            )
        else -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("ktlint:standard:function-naming")
@Composable
private fun StartPositionDropdown(
    selected: StartPositionOption,
    onSelected: (StartPositionOption) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Start Position") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
            enabled = enabled,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            StartPositionOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("ktlint:standard:function-naming")
@Composable
private fun PartitionDropdown(
    selected: Int,
    partitionCount: Int,
    enabled: Boolean,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = if (selected < 0) "All" else selected.toString(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Partition") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("All partitions") },
                onClick = {
                    onSelected(-1)
                    expanded = false
                },
            )
            repeat(partitionCount) { partition ->
                DropdownMenuItem(
                    text = { Text("Partition $partition") },
                    onClick = {
                        onSelected(partition)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun SessionActionButtons(
    isRunning: Boolean,
    isPaused: Boolean,
    startEnabled: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isRunning) {
        Button(
            onClick = onStart,
            enabled = startEnabled,
            colors = ButtonDefaults.buttonColors(containerColor = AccentViolet),
            modifier = modifier,
        ) { Text("Start", color = TextPrimary) }
    } else {
        OutlinedButton(
            onClick = if (isPaused) onResume else onPause,
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isPaused) StatusSuccess else StatusWarning,
                ),
        ) { Text(if (isPaused) "Resume" else "Pause") }
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(
            onClick = onStop,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
        ) { Text("Stop") }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun StatsRow(
    messageCount: Int,
    isPaused: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$messageCount messages loaded", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        if (isPaused) {
            Spacer(modifier = Modifier.width(12.dp))
            Text("PAUSED", style = MaterialTheme.typography.labelSmall, color = StatusWarning)
        }
    }
}

// ── Producer ───────────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun MessageComposer(
    state: MessageComposerState,
    partitionCount: Int,
    onStateChanged: (MessageComposerState) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().background(SurfaceElevated).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Produce message", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.key,
                onValueChange = { onStateChanged(state.copy(key = it, status = null)) },
                label = { Text("Key (optional)") },
                singleLine = true,
                enabled = !state.isSending,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.partitionText,
                onValueChange = { onStateChanged(state.copy(partitionText = it, status = null)) },
                label = { Text("Partition (auto)") },
                placeholder = { Text("0–${(partitionCount - 1).coerceAtLeast(0)}") },
                singleLine = true,
                enabled = !state.isSending,
                modifier = Modifier.width(170.dp),
            )
        }
        OutlinedTextField(
            value = state.value,
            onValueChange = { onStateChanged(state.copy(value = it, status = null)) },
            label = { Text("Value") },
            placeholder = { Text("Text or JSON") },
            enabled = !state.isSending,
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp, max = 180.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ComposerStatus(state.status, Modifier.weight(1f))
            Button(
                onClick = onSend,
                enabled = !state.isSending,
                colors = ButtonDefaults.buttonColors(containerColor = AccentViolet),
            ) {
                Text(if (state.isSending) "Sending…" else "Send message")
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun ComposerStatus(
    status: SendStatus?,
    modifier: Modifier = Modifier,
) {
    when (status) {
        is SendStatus.Success ->
            Text(
                "Sent to partition ${status.result.partition}, offset ${status.result.offset}",
                style = MaterialTheme.typography.bodySmall,
                color = StatusSuccess,
                modifier = modifier,
            )
        is SendStatus.Error ->
            Text(
                status.message,
                style = MaterialTheme.typography.bodySmall,
                color = StatusError,
                modifier = modifier,
            )
        null -> Spacer(modifier)
    }
}

// ── Message Filter Bar ─────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun MessageFilterBar(
    filterState: MessageFilterState,
    onFilterChanged: (MessageFilterState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = filterState.keyFilter,
            onValueChange = { onFilterChanged(filterState.copy(keyFilter = it)) },
            label = { Text("Filter by key") },
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
            singleLine = true,
        )
        OutlinedTextField(
            value = filterState.valueFilter,
            onValueChange = { onFilterChanged(filterState.copy(valueFilter = it)) },
            label = { Text("Filter by value") },
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
            singleLine = true,
        )
    }
}

// ── Message Row ────────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun MessageRow(
    message: ConsumedMessage,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() }
                .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OffsetText(offset = message.offset)
            PartitionBadge(partition = message.partition)
            TimestampText(epochMillis = message.timestamp)
            KeyPreview(key = message.key)
            ValuePreview(value = message.value)
        }
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            MessageExpandedView(message = message)
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun OffsetText(
    offset: Long,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "$offset",
        style =
            MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            ),
        color = TextSecondary,
        modifier = modifier.width(80.dp),
    )
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun PartitionBadge(
    partition: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "P$partition",
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = TextMuted,
        modifier = modifier.width(36.dp),
    )
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun TimestampText(
    epochMillis: Long,
    modifier: Modifier = Modifier,
) {
    Text(
        text = formatTimestamp(epochMillis, includeDate = true),
        style = MaterialTheme.typography.bodySmall,
        color = TextMuted,
        modifier = modifier.width(140.dp),
    )
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun RowScope.KeyPreview(
    key: ByteArray?,
    modifier: Modifier = Modifier,
) {
    val keyText = key?.toString(Charsets.UTF_8)
    Text(
        text = keyText ?: "null",
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = if (keyText != null) TextSecondary else TextMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.weight(0.25f),
    )
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun RowScope.ValuePreview(
    value: ByteArray?,
    modifier: Modifier = Modifier,
) {
    val formatted = JsonFormatter.detectAndFormat(value)
    val preview =
        when (formatted) {
            is FormattedContent.Json -> formatted.formatted.take(100)
            is FormattedContent.PlainText -> formatted.text.take(100)
            is FormattedContent.Binary -> "[Binary ${formatted.size}B]"
        }
    Text(
        text = preview,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.weight(0.5f),
    )
}

// ── Expanded Message View ──────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun MessageExpandedView(
    message: ConsumedMessage,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 8.dp, start = 272.dp)
                .background(SurfaceElevated, MaterialTheme.shapes.small)
                .padding(12.dp),
    ) {
        HeadersBlock(headers = message.headers)
        ExpandedValueDisplay(value = message.value)
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun HeadersBlock(
    headers: Map<String, ByteArray?>,
    modifier: Modifier = Modifier,
) {
    if (headers.isEmpty()) return
    Column(modifier = modifier) {
        Text("Headers", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        headers.forEach { (k, v) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    k,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = TextSecondary,
                )
                Text(
                    v?.toString(Charsets.UTF_8) ?: "null",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = TextPrimary,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun ExpandedValueDisplay(
    value: ByteArray?,
    modifier: Modifier = Modifier,
) {
    val formatted = JsonFormatter.detectAndFormat(value)
    Column(modifier = modifier) {
        when (formatted) {
            is FormattedContent.Json -> {
                Text("Value (JSON)", style = MaterialTheme.typography.labelSmall, color = AccentViolet)
                Text(
                    formatted.formatted,
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp,
                        ),
                    color = TextPrimary,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                )
            }
            is FormattedContent.PlainText -> {
                Text("Value (Text)", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Text(
                    formatted.text,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = TextPrimary,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                )
            }
            is FormattedContent.Binary -> {
                Text(
                    "Value (Binary — ${formatted.size} bytes)",
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusWarning,
                )
            }
        }
    }
}
