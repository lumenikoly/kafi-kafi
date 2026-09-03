package com.lightkafka.ui.topic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lightkafka.core.kafka.CreateTopicRequest
import com.lightkafka.core.kafka.KafkaAdminService
import com.lightkafka.core.kafka.KafkaResult
import com.lightkafka.ui.connection.AppConnectionState
import com.lightkafka.ui.connection.ConnectionState
import com.lightkafka.ui.infra.AccentViolet
import com.lightkafka.ui.infra.BorderSubtle
import com.lightkafka.ui.infra.StatusError
import com.lightkafka.ui.infra.StatusSuccess
import com.lightkafka.ui.infra.TextMuted
import com.lightkafka.ui.infra.TextPrimary
import com.lightkafka.ui.infra.TextSecondary
import com.lightkafka.ui.shell.ShellAction
import com.lightkafka.ui.shell.Store
import com.lightkafka.ui.shell.TabType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

// ── Main Entry ─────────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
fun CreateTopicPage(
    connectionStateFlow: MutableStateFlow<AppConnectionState>,
    shellStore: Store<*, ShellAction>,
    modifier: Modifier = Modifier,
) {
    val connectionState by connectionStateFlow.collectAsState()
    val adminService = connectionState.activeAdminService

    if (adminService == null || connectionState.connectionStatus?.state != ConnectionState.CONNECTED) {
        CreateTopicDisconnected(modifier = modifier)
        return
    }

    CreateTopicForm(
        adminService = adminService,
        onCreateSuccess = { topicName ->
            shellStore.dispatch(
                ShellAction.OpenEntityTab(
                    type = TabType.TOPIC_DETAIL,
                    entityId = topicName,
                    title = topicName,
                ),
            )
        },
        modifier = modifier,
    )
}

// ── Disconnected ───────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun CreateTopicDisconnected(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "Connect to a cluster to create topics",
            style = MaterialTheme.typography.headlineMedium,
            color = TextSecondary,
        )
    }
}

// ── Form State ─────────────────────────────────────────────────────────────

private class FormState {
    var name by mutableStateOf("")
    var partitionsText by mutableStateOf("1")
    var replicationText by mutableStateOf("1")
    var isSubmitting by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var successMessage by mutableStateOf<String?>(null)

    val partitions: Int? get() = partitionsText.toIntOrNull()
    val replication: Short? get() = replicationText.toShortOrNull()
    val nameValid: Boolean get() = name.isNotBlank()
    val partitionsValid: Boolean get() = partitions != null && partitions!! >= 1
    val replicationValid: Boolean get() = replication != null && replication!! >= 1
    val formValid: Boolean get() = nameValid && partitionsValid && replicationValid
}

// ── Form ───────────────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun CreateTopicForm(
    adminService: KafkaAdminService,
    onCreateSuccess: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = remember { FormState() }
    val configs = remember { mutableStateListOf<Pair<String, String>>() }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Text(
            "Create Topic",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Define a new topic on the cluster",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )

        HorizontalDivider(color = BorderSubtle)

        CreateTopicCoreFields(state)

        HorizontalDivider(color = BorderSubtle)

        CreateTopicConfigSection(
            configs = configs,
            onAddConfig = { configs.add("" to "") },
        )

        // Status messages
        state.errorMessage?.let { msg ->
            Text(msg, style = MaterialTheme.typography.bodySmall, color = StatusError)
        }
        state.successMessage?.let { msg ->
            Text(msg, style = MaterialTheme.typography.bodySmall, color = StatusSuccess)
        }

        // Submit button
        CreateTopicSubmitButton(
            state = state,
            onSubmit = {
                val request = buildCreateRequest(state, configs)
                scope.launch { submitCreateTopic(adminService, request, state, onCreateSuccess) }
            },
        )
    }
}

// ── Core Fields (name, partitions, replication) ────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun CreateTopicCoreFields(state: FormState) {
    CreateTopicFormField(
        label = "Topic Name",
        value = state.name,
        onValueChange = { state.name = it },
        placeholder = "my-topic",
        isError = state.name.isNotEmpty() && !state.nameValid,
        errorText = "Name must not be empty",
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            CreateTopicFormField(
                label = "Partitions",
                value = state.partitionsText,
                onValueChange = { state.partitionsText = it },
                placeholder = "1",
                isError = state.partitionsText.isNotEmpty() && !state.partitionsValid,
                errorText = "Must be ≥ 1",
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            CreateTopicFormField(
                label = "Replication Factor",
                value = state.replicationText,
                onValueChange = { state.replicationText = it },
                placeholder = "1",
                isError = state.replicationText.isNotEmpty() && !state.replicationValid,
                errorText = "Must be ≥ 1",
            )
        }
    }
}

// ── Config Section ─────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun CreateTopicConfigSection(
    configs: MutableList<Pair<String, String>>,
    onAddConfig: () -> Unit,
) {
    Text(
        "Additional Configuration (optional)",
        style = MaterialTheme.typography.titleSmall,
        color = TextSecondary,
    )

    configs.forEachIndexed { index, _ ->
        ConfigEntryRow(
            name = configs[index].first,
            value = configs[index].second,
            onNameChange = { configs[index] = it to configs[index].second },
            onValueChange = { configs[index] = configs[index].first to it },
            onRemove = { configs.removeAt(index) },
        )
    }

    Button(
        onClick = onAddConfig,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentViolet),
    ) {
        Text("+ Add Config", style = MaterialTheme.typography.bodySmall)
    }
}

// ── Submit Button ──────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun CreateTopicSubmitButton(
    state: FormState,
    onSubmit: () -> Unit,
) {
    Button(
        onClick = { if (state.formValid) onSubmit() },
        enabled = state.formValid && !state.isSubmitting,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = AccentViolet,
                contentColor = TextPrimary,
                disabledContainerColor = AccentViolet.copy(alpha = 0.4f),
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (state.isSubmitting) "Creating…" else "Create Topic")
    }
}

// ── Submit Logic ───────────────────────────────────────────────────────────

private fun buildCreateRequest(
    state: FormState,
    configs: List<Pair<String, String>>,
): CreateTopicRequest =
    CreateTopicRequest(
        name = state.name.trim(),
        partitions = state.partitions!!,
        replicationFactor = state.replication!!,
        configs =
            configs
                .filter { it.first.isNotBlank() }
                .associate { it.first.trim() to it.second.trim() },
    )

private suspend fun submitCreateTopic(
    adminService: KafkaAdminService,
    request: CreateTopicRequest,
    state: FormState,
    onSuccess: (String) -> Unit,
) {
    state.isSubmitting = true
    state.errorMessage = null
    state.successMessage = null
    when (val result = adminService.createTopic(request)) {
        is KafkaResult.Success -> {
            state.successMessage = "Topic \"${request.name}\" created successfully"
            onSuccess(request.name)
        }
        is KafkaResult.Failure -> {
            state.errorMessage = result.error.toString()
        }
    }
    state.isSubmitting = false
}

// ── Form Field ─────────────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun CreateTopicFormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isError: Boolean,
    errorText: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = TextMuted) },
            singleLine = true,
            isError = isError,
            modifier = Modifier.fillMaxWidth(),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AccentViolet,
                    unfocusedBorderColor = BorderSubtle,
                    cursorColor = AccentViolet,
                    errorBorderColor = StatusError,
                    errorTextColor = TextPrimary,
                ),
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        if (isError) {
            Text(errorText, style = MaterialTheme.typography.labelSmall, color = StatusError)
        }
    }
}

// ── Config Entry Row ───────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun ConfigEntryRow(
    name: String,
    value: String,
    onNameChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = { Text("config name", color = TextMuted) },
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
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("value", color = TextMuted) },
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
        Button(
            onClick = onRemove,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text("✕", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
