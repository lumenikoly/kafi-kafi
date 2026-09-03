package com.lightkafka.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lightkafka.core.storage.AppSettings
import com.lightkafka.core.storage.DefaultConsumerStartPosition
import com.lightkafka.core.storage.SettingsStore
import com.lightkafka.ui.infra.AccentViolet
import com.lightkafka.ui.infra.BorderSubtle
import com.lightkafka.ui.infra.StatusError
import com.lightkafka.ui.infra.StatusSuccess
import com.lightkafka.ui.infra.SurfaceCard
import com.lightkafka.ui.infra.TextMuted
import com.lightkafka.ui.infra.TextPrimary
import com.lightkafka.ui.infra.TextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

private data class SettingsDraft(
    val defaultStartPosition: DefaultConsumerStartPosition,
    val messageBufferLimit: String,
)

@Suppress("ktlint:standard:function-naming")
@Composable
fun SettingsPage(
    settingsStore: SettingsStore,
    settingsStateFlow: MutableStateFlow<AppSettings>,
    modifier: Modifier = Modifier,
) {
    val savedSettings by settingsStateFlow.collectAsState()
    var draft by remember(savedSettings) { mutableStateOf(savedSettings.toDraft()) }
    var isSaving by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var resultIsError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val validationError = validate(draft)
    val isDirty = draft != savedSettings.toDraft()
    val saveSettings: () -> Unit = {
        val settings = draft.toSettings()
        if (settings != null) {
            isSaving = true
            resultMessage = null
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { settingsStore.saveSettings(settings) }
                    settingsStateFlow.value = settings
                    resultMessage = "Settings saved"
                    resultIsError = false
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    resultMessage = error.message ?: "Could not save settings"
                    resultIsError = true
                } finally {
                    isSaving = false
                }
            }
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsHeader()
        MessageConsumptionSettings(
            draft = draft,
            validationError = validationError,
            onDraftChange = { draft = it },
        )

        SettingsSection(title = "Runtime") {
            RuntimeRow("Java", System.getProperty("java.version"))
            RuntimeRow(
                "Local data",
                Path.of(System.getProperty("user.home"), ".lightkafka", "storage.json").toString(),
            )
        }

        SettingsActions(
            resultMessage = resultMessage,
            resultIsError = resultIsError,
            isSaving = isSaving,
            saveEnabled = isDirty && validationError == null,
            onReset = {
                draft = AppSettings().toDraft()
                resultMessage = null
            },
            onSave = saveSettings,
        )
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun SettingsHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        Text(
            "Defaults are stored locally and apply to newly opened message sessions.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun MessageConsumptionSettings(
    draft: SettingsDraft,
    validationError: String?,
    onDraftChange: (SettingsDraft) -> Unit,
) {
    SettingsSection(title = "Message consumption") {
        Text("Default start position", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
        Text(
            "Choose where a new consumer session starts unless you change it in the topic.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StartPositionButton(
                label = "Latest",
                selected = draft.defaultStartPosition == DefaultConsumerStartPosition.LATEST,
                onClick = {
                    onDraftChange(draft.copy(defaultStartPosition = DefaultConsumerStartPosition.LATEST))
                },
            )
            StartPositionButton(
                label = "Earliest",
                selected = draft.defaultStartPosition == DefaultConsumerStartPosition.EARLIEST,
                onClick = {
                    onDraftChange(draft.copy(defaultStartPosition = DefaultConsumerStartPosition.EARLIEST))
                },
            )
        }
        HorizontalDivider(color = BorderSubtle)
        OutlinedTextField(
            value = draft.messageBufferLimit,
            onValueChange = { value ->
                if (value.all(Char::isDigit)) onDraftChange(draft.copy(messageBufferLimit = value))
            },
            label = { Text("Messages kept in memory") },
            supportingText = {
                Text(
                    validationError
                        ?: "${AppSettings.MIN_MESSAGE_BUFFER_LIMIT}–${AppSettings.MAX_MESSAGE_BUFFER_LIMIT}",
                )
            },
            isError = validationError != null,
            singleLine = true,
            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
        )
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun SettingsActions(
    resultMessage: String?,
    resultIsError: Boolean,
    isSaving: Boolean,
    saveEnabled: Boolean,
    onReset: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        resultMessage?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (resultIsError) StatusError else StatusSuccess,
            )
        } ?: Spacer(modifier = Modifier)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onReset, enabled = !isSaving) { Text("Reset to defaults") }
            Button(
                onClick = onSave,
                enabled = saveEnabled && !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = AccentViolet, contentColor = TextPrimary),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save settings")
                }
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
        color = SurfaceCard,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            content()
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun StartPositionButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun RuntimeRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            modifier = Modifier.widthIn(min = 120.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

private fun AppSettings.toDraft(): SettingsDraft =
    SettingsDraft(
        defaultStartPosition = defaultConsumerStartPosition,
        messageBufferLimit = messageBufferLimit.toString(),
    )

private fun SettingsDraft.toSettings(): AppSettings? =
    messageBufferLimit.toIntOrNull()?.let {
        AppSettings(defaultConsumerStartPosition = defaultStartPosition, messageBufferLimit = it)
    }

private fun validate(draft: SettingsDraft): String? {
    val limit = draft.messageBufferLimit.toIntOrNull() ?: return "Enter a message limit"
    return if (limit in AppSettings.MIN_MESSAGE_BUFFER_LIMIT..AppSettings.MAX_MESSAGE_BUFFER_LIMIT) {
        null
    } else {
        "Use a value from ${AppSettings.MIN_MESSAGE_BUFFER_LIMIT} to ${AppSettings.MAX_MESSAGE_BUFFER_LIMIT}"
    }
}
