package com.lightkafka.ui.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.lightkafka.core.storage.SaslMechanism
import com.lightkafka.core.storage.SecurityProtocol
import com.lightkafka.ui.infra.StatusError
import com.lightkafka.ui.infra.StatusErrorBg
import com.lightkafka.ui.infra.StatusInfo
import com.lightkafka.ui.infra.StatusSuccess
import com.lightkafka.ui.infra.StatusSuccessBg
import com.lightkafka.ui.infra.StatusWarning
import com.lightkafka.ui.infra.SurfaceCard
import com.lightkafka.ui.infra.TextMuted
import com.lightkafka.ui.infra.TextPrimary
import com.lightkafka.ui.infra.TextSecondary

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun FormSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = TextSecondary,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = TextSecondary) },
        placeholder =
            if (placeholder.isNotBlank()) {
                { Text(placeholder, color = TextMuted) }
            } else {
                null
            },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        textStyle =
            MaterialTheme.typography.bodyMedium.copy(
                color = com.lightkafka.ui.infra.TextPrimary,
            ),
    )
}

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun PasswordField(
    label: String,
    value: String,
    showPassword: Boolean,
    onValueChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = TextSecondary) },
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = onToggleVisibility) {
                Text(
                    if (showPassword) "Hide" else "Show",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        textStyle =
            MaterialTheme.typography.bodyMedium.copy(
                color = com.lightkafka.ui.infra.TextPrimary,
            ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("ktlint:standard:function-naming")
@Composable
internal fun SecurityProtocolDropdown(
    selected: SecurityProtocol,
    onSelected: (SecurityProtocol) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("Security Protocol", color = TextSecondary) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            textStyle =
                MaterialTheme.typography.bodyMedium.copy(
                    color = com.lightkafka.ui.infra.TextPrimary,
                ),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SecurityProtocol.entries.forEach { protocol ->
                DropdownMenuItem(
                    text = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                protocol.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = com.lightkafka.ui.infra.TextPrimary,
                            )
                            SecurityBadge(protocol = protocol)
                        }
                    },
                    onClick = {
                        onSelected(protocol)
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
internal fun SaslMechanismDropdown(
    selected: SaslMechanism,
    onSelected: (SaslMechanism) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("SASL Mechanism", color = TextSecondary) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            textStyle =
                MaterialTheme.typography.bodyMedium.copy(
                    color = com.lightkafka.ui.infra.TextPrimary,
                ),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SaslMechanism.entries.forEach { mechanism ->
                DropdownMenuItem(
                    text = {
                        Text(
                            mechanism.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = com.lightkafka.ui.infra.TextPrimary,
                        )
                    },
                    onClick = {
                        onSelected(mechanism)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun SecurityBadge(
    protocol: SecurityProtocol,
    modifier: Modifier = Modifier,
) {
    val (bgColor, textColor) =
        when (protocol) {
            SecurityProtocol.PLAINTEXT -> StatusSuccessBg to StatusSuccess
            SecurityProtocol.SSL -> StatusInfo to StatusInfo
            SecurityProtocol.SASL_PLAINTEXT -> StatusWarning to SurfaceCard
            SecurityProtocol.SASL_SSL -> StatusErrorBg to StatusError
        }
    Box(
        modifier =
            modifier
                .background(bgColor, shape = MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 6.dp, vertical = 2.dp),
    ) { Text(protocol.name, style = MaterialTheme.typography.labelSmall, color = textColor) }
}

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun SaslFields(
    state: ConnectionEditorState,
    onStateChange: (ConnectionEditorState) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SaslMechanismDropdown(
            selected = state.saslMechanism,
            onSelected = { onStateChange(state.copy(saslMechanism = it)) },
        )
        FormField(
            label = "SASL Username",
            value = state.saslUsername,
            onValueChange = { onStateChange(state.copy(saslUsername = it)) },
        )
        PasswordField(
            label = "SASL Password",
            value = state.saslPassword,
            showPassword = state.showPassword,
            onValueChange = { onStateChange(state.copy(saslPassword = it)) },
            onToggleVisibility = { onStateChange(state.copy(showPassword = !state.showPassword)) },
        )
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun SslFields(
    state: ConnectionEditorState,
    onStateChange: (ConnectionEditorState) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FormField(
            label = "Truststore Path",
            value = state.truststorePath,
            onValueChange = { onStateChange(state.copy(truststorePath = it)) },
            placeholder = "/path/to/truststore.jks",
        )
        PasswordField(
            label = "Truststore Password",
            value = state.truststorePassword,
            showPassword = state.showPassword,
            onValueChange = { onStateChange(state.copy(truststorePassword = it)) },
            onToggleVisibility = { onStateChange(state.copy(showPassword = !state.showPassword)) },
        )
        FormField(
            label = "Keystore Path",
            value = state.keystorePath,
            onValueChange = { onStateChange(state.copy(keystorePath = it)) },
            placeholder = "/path/to/keystore.jks",
        )
        PasswordField(
            label = "Keystore Password",
            value = state.keystorePassword,
            showPassword = state.showPassword,
            onValueChange = { onStateChange(state.copy(keystorePassword = it)) },
            onToggleVisibility = { onStateChange(state.copy(showPassword = !state.showPassword)) },
        )
        PasswordField(
            label = "Key Password",
            value = state.keyPassword,
            showPassword = state.showPassword,
            onValueChange = { onStateChange(state.copy(keyPassword = it)) },
            onToggleVisibility = { onStateChange(state.copy(showPassword = !state.showPassword)) },
        )
    }
}
