package com.lightkafka.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lightkafka.core.kafka.DefaultKafkaAdminService
import com.lightkafka.core.kafka.KafkaConnectionConfig
import com.lightkafka.core.kafka.KafkaResult
import com.lightkafka.core.storage.ClusterProfile
import com.lightkafka.core.storage.SaslMechanism
import com.lightkafka.core.storage.SecurityProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
internal fun connectionManagerDialog(
    state: MainUiState,
    onAction: (MainUiAction) -> Unit,
) {
    val scope = rememberCoroutineScope()

    // Local editor state
    var editorState by remember(state.isConnectionManagerOpen) {
        mutableStateOf(
            state.profiles.firstOrNull()?.toEditorState() ?: ConnectionEditorState(),
        )
    }

    Dialog(
        onDismissRequest = { onAction(MainUiAction.SetConnectionManagerOpen(false)) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier
                    .width(1000.dp)
                    .height(700.dp),
            shape = RoundedCornerShape(20.dp),
            color = SurfaceCard,
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Profile list sidebar
                profileListPane(
                    state = state,
                    editorState = editorState,
                    onSelectProfile = { profile ->
                        editorState = profile.toEditorState()
                    },
                    onNewProfile = {
                        editorState = ConnectionEditorState()
                    },
                    onDuplicateProfile = { profile ->
                        editorState =
                            profile.toEditorState().copy(
                                profileId = null,
                                name = "${profile.name} (Copy)",
                            )
                    },
                    onTestProfile = { profile ->
                        scope.launch {
                            editorState =
                                editorState.copy(
                                    isTesting = true,
                                    testStatus = ConnectionTestStatus.InProgress(),
                                )
                            val result =
                                withContext(Dispatchers.IO) {
                                    testConnection(profile)
                                }
                            editorState =
                                editorState.copy(
                                    isTesting = false,
                                    testStatus = result.first,
                                )
                            onAction(MainUiAction.UpdateConnectionStatus(profile.id, result.second))
                        }
                    },
                    onDeleteRequest = { profileId ->
                        onAction(MainUiAction.RequestDeleteProfile(profileId))
                    },
                )

                // Vertical divider
                Box(
                    modifier =
                        Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(BorderSubtle),
                )

                // Editor pane
                connectionEditorPane(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    state = editorState,
                    onStateChange = { editorState = it },
                    onSave = {
                        val profileId = editorState.profileId ?: UUID.randomUUID().toString()
                        val profile = editorState.toProfile(profileId)
                        onAction(MainUiAction.UpsertProfile(profile))
                        onAction(MainUiAction.SetActiveProfile(profile.id))
                        editorState =
                            profile.toEditorState().copy(
                                testStatus =
                                    ConnectionTestStatus.Success(
                                        clusterId = null,
                                        brokerCount = 0,
                                        controllerId = null,
                                        topicCount = 0,
                                        latencyMs = 0,
                                    ),
                            )
                    },
                    onTest = {
                        scope.launch {
                            editorState =
                                editorState.copy(
                                    isTesting = true,
                                    testStatus = ConnectionTestStatus.InProgress(),
                                )
                            val profile = editorState.toProfile("test-${UUID.randomUUID()}")
                            val result =
                                withContext(Dispatchers.IO) {
                                    testConnection(profile)
                                }
                            editorState =
                                editorState.copy(
                                    isTesting = false,
                                    testStatus = result.first,
                                )
                        }
                    },
                    onClose = { onAction(MainUiAction.SetConnectionManagerOpen(false)) },
                )
            }
        }
    }

    // Delete confirmation dialog
    state.profileToDelete?.let { profileId ->
        val profileToDelete = state.profiles.firstOrNull { it.id == profileId }
        if (profileToDelete != null) {
            deleteConfirmationDialog(
                profile = profileToDelete,
                onConfirm = {
                    onAction(MainUiAction.ConfirmDeleteProfile(profileId))
                    // Reset editor to first available profile or empty
                    val nextProfile = state.profiles.firstOrNull { it.id != profileId }
                    editorState = nextProfile?.toEditorState() ?: ConnectionEditorState()
                },
                onDismiss = {
                    onAction(MainUiAction.CancelDeleteProfile)
                },
            )
        }
    }
}

@Composable
private fun deleteConfirmationDialog(
    profile: ClusterProfile,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = StatusError,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                text = "Delete Connection Profile?",
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
        },
        text = {
            Column {
                Text(
                    text = "Are you sure you want to delete this connection profile?",
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = SurfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = profile.name,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = profile.bootstrapServers.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "This action cannot be undone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusError,
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
        containerColor = SurfaceCard,
    )
}

@Composable
private fun profileListPane(
    state: MainUiState,
    editorState: ConnectionEditorState,
    onSelectProfile: (ClusterProfile) -> Unit,
    onNewProfile: () -> Unit,
    onDuplicateProfile: (ClusterProfile) -> Unit,
    onTestProfile: (ClusterProfile) -> Unit,
    onDeleteRequest: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(320.dp)
                .fillMaxHeight()
                .background(SurfaceDark)
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
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
                text = "Profiles",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.weight(1f))

            // New profile button
            Surface(
                color = AccentViolet.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.clickable(onClick = onNewProfile),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Profile",
                    tint = AccentViolet,
                    modifier = Modifier.padding(8.dp).size(18.dp),
                )
            }
        }

        // Profile list or empty state
        if (state.profiles.isEmpty()) {
            emptyProfilesState(onNewProfile = onNewProfile)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.profiles, key = { it.id }) { profile ->
                    profileCard(
                        profile = profile,
                        selected = profile.id == editorState.profileId,
                        connectionStatus = state.getConnectionStatus(profile.id),
                        onClick = { onSelectProfile(profile) },
                        onDuplicate = { onDuplicateProfile(profile) },
                        onTest = { onTestProfile(profile) },
                        onDelete = { onDeleteRequest(profile.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun emptyProfilesState(onNewProfile: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "No connections yet",
            style = MaterialTheme.typography.titleMedium,
            color = TextSecondary,
            fontWeight = FontWeight.Medium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Create your first Kafka connection",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onNewProfile,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = AccentViolet,
                    contentColor = Color.White,
                ),
            shape = RoundedCornerShape(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Connection", fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun profileCard(
    profile: ClusterProfile,
    selected: Boolean,
    connectionStatus: ConnectionStatus?,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgColor by animateColorAsState(
        targetValue =
            when {
                selected -> AccentViolet.copy(alpha = 0.15f)
                isHovered -> SurfaceHover
                else -> SurfaceElevated
            },
        animationSpec = tween(150),
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
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Selection indicator
            if (selected) {
                Box(
                    modifier =
                        Modifier
                            .size(6.dp)
                            .background(AccentViolet, RoundedCornerShape(3.dp)),
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Connection status indicator
                    Box(
                        modifier =
                            Modifier
                                .size(8.dp)
                                .background(
                                    when (connectionStatus?.state) {
                                        ConnectionState.CONNECTED -> StatusSuccess
                                        ConnectionState.ERROR -> StatusError
                                        ConnectionState.UNKNOWN, null -> TextMuted
                                    },
                                    CircleShape,
                                ),
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = profile.name,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (selected) AccentViolet else TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = profile.bootstrapServers.joinToString(","),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Status info
                connectionStatus?.let { status ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text =
                            when (status.state) {
                                ConnectionState.CONNECTED -> "${status.brokerCount} brokers • ${status.latencyMs}ms"
                                ConnectionState.ERROR -> status.error?.take(30) ?: "Connection failed"
                                ConnectionState.UNKNOWN -> "Not tested"
                            },
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            when (status.state) {
                                ConnectionState.CONNECTED -> StatusSuccess.copy(alpha = 0.8f)
                                ConnectionState.ERROR -> StatusError.copy(alpha = 0.8f)
                                ConnectionState.UNKNOWN -> TextMuted
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Quick actions on hover
            AnimatedVisibility(
                visible = isHovered,
                enter = fadeIn(tween(100)),
                exit = fadeOut(tween(100)),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    iconOnlyButton(
                        icon = Icons.Default.PlayArrow,
                        tooltip = "Test",
                        onClick = onTest,
                    )
                    iconOnlyButton(
                        icon = Icons.Default.ContentCopy,
                        tooltip = "Duplicate",
                        onClick = onDuplicate,
                    )
                    iconOnlyButton(
                        icon = Icons.Default.Delete,
                        tooltip = "Delete",
                        isDestructive = true,
                        onClick = onDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun iconOnlyButton(
    icon: ImageVector,
    tooltip: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        color = if (isDestructive) StatusError.copy(alpha = 0.1f) else Color.Transparent,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = tooltip,
            tint = if (isDestructive) StatusError else TextMuted,
            modifier = Modifier.padding(6.dp).size(16.dp),
        )
    }
}

@Composable
private fun connectionEditorPane(
    modifier: Modifier = Modifier,
    state: ConnectionEditorState,
    onStateChange: (ConnectionEditorState) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onClose: () -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier.padding(24.dp),
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
                text = if (state.isNewProfile) "New Connection" else "Edit Connection",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) {
                Text("Close", color = TextSecondary)
            }
        }

        // Scrollable content
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Basic Section
            collapsibleSection(
                title = "Basic",
                expanded = state.expandedSection == ConnectionEditorSection.BASIC,
                onToggle = { onStateChange(state.copy(expandedSection = ConnectionEditorSection.BASIC)) },
            ) {
                basicSection(state, onStateChange)
            }

            // Security Section
            collapsibleSection(
                title = "Security",
                expanded = state.expandedSection == ConnectionEditorSection.SECURITY,
                onToggle = { onStateChange(state.copy(expandedSection = ConnectionEditorSection.SECURITY)) },
            ) {
                securitySection(state, onStateChange)
            }

            // Advanced Section
            collapsibleSection(
                title = "Advanced",
                expanded = state.expandedSection == ConnectionEditorSection.ADVANCED,
                onToggle = { onStateChange(state.copy(expandedSection = ConnectionEditorSection.ADVANCED)) },
            ) {
                advancedSection(state, onStateChange)
            }

            // Test Results
            testResultsSection(state)
        }

        // Action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onSave,
                enabled = state.canSave,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = AccentViolet,
                        contentColor = Color.White,
                        disabledContainerColor = AccentViolet.copy(alpha = 0.3f),
                    ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Connection", fontWeight = FontWeight.SemiBold)
            }

            surfaceActionButton(
                icon = Icons.Default.PlayArrow,
                text = if (state.isTesting) "Testing..." else "Test Connection",
                enabled = !state.isTesting && state.bootstrapServers.isNotBlank(),
                onClick = onTest,
            )
        }
    }
}

@Composable
private fun collapsibleSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        color = SurfaceElevated,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column {
            // Header row
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggle)
                        .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(200)),
            ) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun basicSection(
    state: ConnectionEditorState,
    onStateChange: (ConnectionEditorState) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
            value = state.name,
            onValueChange = { onStateChange(state.copy(name = it)) },
            label = { Text("Profile name") },
            placeholder = { Text("e.g. Production Cluster") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = outlinedTextFieldColors(),
            shape = RoundedCornerShape(10.dp),
        )

        OutlinedTextField(
            value = state.bootstrapServers,
            onValueChange = { onStateChange(state.copy(bootstrapServers = it)) },
            label = { Text("Bootstrap servers") },
            placeholder = { Text("localhost:9092") },
            supportingText = { Text("Comma-separated list of broker addresses") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = outlinedTextFieldColors(),
            shape = RoundedCornerShape(10.dp),
        )

        OutlinedTextField(
            value = state.clientId,
            onValueChange = { onStateChange(state.copy(clientId = it)) },
            label = { Text("Client ID (optional)") },
            placeholder = { Text("light-kafka-viewer") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = outlinedTextFieldColors(),
            shape = RoundedCornerShape(10.dp),
        )
    }
}

@Composable
private fun securitySection(
    state: ConnectionEditorState,
    onStateChange: (ConnectionEditorState) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Security Protocol
        Text(
            text = "Security Protocol",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecurityProtocol.entries.forEach { protocol ->
                protocolChip(
                    label = protocol.name,
                    selected = state.securityProtocol == protocol,
                    onClick = { onStateChange(state.copy(securityProtocol = protocol)) },
                )
            }
        }

        // SASL Configuration
        if (state.needsSasl) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "SASL Configuration",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
            )

            // SASL Mechanism
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SaslMechanism.entries.forEach { mechanism ->
                    protocolChip(
                        label = mechanism.name.replace("_", "-"),
                        selected = state.saslMechanism == mechanism,
                        onClick = { onStateChange(state.copy(saslMechanism = mechanism)) },
                        isSmall = true,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = state.saslUsername,
                onValueChange = { onStateChange(state.copy(saslUsername = it)) },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedTextFieldColors(),
                shape = RoundedCornerShape(10.dp),
            )

            OutlinedTextField(
                value = state.saslPassword,
                onValueChange = { onStateChange(state.copy(saslPassword = it)) },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation =
                    if (state.showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedTextFieldColors(),
                shape = RoundedCornerShape(10.dp),
                trailingIcon = {
                    IconButton(onClick = { onStateChange(state.copy(showPassword = !state.showPassword)) }) {
                        Icon(
                            imageVector =
                                if (state.showPassword) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                            contentDescription = if (state.showPassword) "Hide" else "Show",
                            tint = TextMuted,
                        )
                    }
                },
            )
        }

        // SSL Configuration
        if (state.needsSsl) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "SSL Configuration",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
            )

            OutlinedTextField(
                value = state.truststorePath,
                onValueChange = { onStateChange(state.copy(truststorePath = it)) },
                label = { Text("Truststore path") },
                placeholder = { Text("/path/to/truststore.jks") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedTextFieldColors(),
                shape = RoundedCornerShape(10.dp),
            )

            OutlinedTextField(
                value = state.truststorePassword,
                onValueChange = { onStateChange(state.copy(truststorePassword = it)) },
                label = { Text("Truststore password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedTextFieldColors(),
                shape = RoundedCornerShape(10.dp),
            )

            OutlinedTextField(
                value = state.keystorePath,
                onValueChange = { onStateChange(state.copy(keystorePath = it)) },
                label = { Text("Keystore path (optional, for mTLS)") },
                placeholder = { Text("/path/to/keystore.jks") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedTextFieldColors(),
                shape = RoundedCornerShape(10.dp),
            )

            OutlinedTextField(
                value = state.keystorePassword,
                onValueChange = { onStateChange(state.copy(keystorePassword = it)) },
                label = { Text("Keystore password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedTextFieldColors(),
                shape = RoundedCornerShape(10.dp),
            )

            OutlinedTextField(
                value = state.keyPassword,
                onValueChange = { onStateChange(state.copy(keyPassword = it)) },
                label = { Text("Key password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedTextFieldColors(),
                shape = RoundedCornerShape(10.dp),
            )
        }
    }
}

@Composable
private fun advancedSection(
    state: ConnectionEditorState,
    onStateChange: (ConnectionEditorState) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Additional Kafka Properties",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
        )

        // Existing properties
        state.additionalProperties.forEach { (key, value) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = SurfaceDark,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = key,
                            color = AccentCyan,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = " = ",
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = value,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                IconButton(
                    onClick = {
                        onStateChange(state.copy(additionalProperties = state.additionalProperties - key))
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // Add new property
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            glassInput(
                value = state.newPropertyKey,
                onValueChange = { onStateChange(state.copy(newPropertyKey = it)) },
                placeholder = "Property key",
                modifier = Modifier.weight(1f),
            )
            glassInput(
                value = state.newPropertyValue,
                onValueChange = { onStateChange(state.copy(newPropertyValue = it)) },
                placeholder = "Value",
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    if (state.newPropertyKey.isNotBlank()) {
                        onStateChange(
                            state.copy(
                                additionalProperties =
                                    state.additionalProperties +
                                        (state.newPropertyKey to state.newPropertyValue),
                                newPropertyKey = "",
                                newPropertyValue = "",
                            ),
                        )
                    }
                },
                enabled = state.newPropertyKey.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add",
                    tint = if (state.newPropertyKey.isNotBlank()) AccentViolet else TextMuted,
                )
            }
        }
    }
}

@Composable
private fun testResultsSection(state: ConnectionEditorState) {
    val status = state.testStatus ?: return

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(200)) + expandVertically(tween(200)),
    ) {
        Surface(
            color =
                when (status) {
                    is ConnectionTestStatus.Success -> StatusSuccessBg.copy(alpha = 0.2f)
                    is ConnectionTestStatus.Failure -> StatusErrorBg.copy(alpha = 0.2f)
                    is ConnectionTestStatus.InProgress -> SurfaceElevated
                },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                when (status) {
                    is ConnectionTestStatus.InProgress -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = status.message,
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    is ConnectionTestStatus.Success -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = StatusSuccess,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Connection Successful",
                                color = StatusSuccess,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        status.clusterId?.let { clusterId ->
                            propertyRow("Cluster ID", clusterId)
                        }
                        propertyRow("Brokers", "${status.brokerCount} available")
                        status.controllerId?.let { controllerId ->
                            propertyRow("Controller", controllerId)
                        }
                        propertyRow("Topics", "${status.topicCount}")
                        propertyRow("Latency", "${status.latencyMs}ms")
                    }

                    is ConnectionTestStatus.Failure -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = StatusError,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Connection Failed",
                                color = StatusError,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = status.error,
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )

                        status.suggestion?.let { suggestion ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = StatusWarning.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = StatusWarning,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = suggestion,
                                        color = StatusWarning,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun propertyRow(
    label: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            color = TextMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(100.dp),
        )
        Text(
            text = value,
            color = TextPrimary,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun protocolChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    isSmall: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Surface(
        color =
            when {
                selected -> AccentViolet.copy(alpha = 0.2f)
                isHovered -> SurfaceHover
                else -> SurfaceDark
            },
        shape = RoundedCornerShape(if (isSmall) 6.dp else 8.dp),
        modifier =
            Modifier
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).then(
                    if (selected) {
                        Modifier.border(
                            1.dp,
                            AccentViolet,
                            RoundedCornerShape(if (isSmall) 6.dp else 8.dp),
                        )
                    } else {
                        Modifier
                    },
                ),
    ) {
        Text(
            text = label,
            color = if (selected) AccentViolet else TextSecondary,
            style = if (isSmall) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier =
                Modifier.padding(
                    horizontal = if (isSmall) 8.dp else 12.dp,
                    vertical = if (isSmall) 6.dp else 8.dp,
                ),
        )
    }
}

@Composable
private fun surfaceActionButton(
    icon: ImageVector,
    text: String,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgColor =
        when {
            !enabled -> SurfaceElevated.copy(alpha = 0.5f)
            isDestructive && isHovered -> StatusErrorBg.copy(alpha = 0.4f)
            isDestructive -> StatusErrorBg.copy(alpha = 0.2f)
            isHovered -> AccentViolet.copy(alpha = 0.2f)
            else -> SurfaceElevated
        }

    val textColor =
        when {
            !enabled -> TextMuted
            isDestructive -> StatusError
            else -> AccentViolet
        }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(10.dp),
        modifier =
            Modifier
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = text,
                color = textColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
        }
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
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceDark)
                .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                inner()
            }
        },
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
        focusedSupportingTextColor = TextMuted,
        unfocusedSupportingTextColor = TextMuted,
        cursorColor = AccentViolet,
    )

// Helper functions
private suspend fun testConnection(profile: ClusterProfile): Pair<ConnectionTestStatus, ConnectionStatus> {
    val startTime = System.currentTimeMillis()

    return try {
        val config =
            KafkaConnectionConfig(
                bootstrapServers = profile.bootstrapServers,
                securityProtocol = profile.securityProtocol.name,
                saslMechanism = profile.sasl?.mechanism?.name,
                saslUsername = profile.sasl?.username,
                saslPassword = profile.sasl?.password,
                sslTruststorePath = profile.ssl?.truststorePath,
                sslTruststorePassword = profile.ssl?.truststorePassword,
                sslKeystorePath = profile.ssl?.keystorePath,
                sslKeystorePassword = profile.ssl?.keystorePassword,
                sslKeyPassword = profile.ssl?.keyPassword,
            )

        val adminService = DefaultKafkaAdminService(config)

        try {
            val topicsResult = adminService.listTopics(includeInternal = false)
            val clusterResult = adminService.describeCluster()
            val latency = System.currentTimeMillis() - startTime

            when (topicsResult) {
                is KafkaResult.Success -> {
                    val cluster = (clusterResult as? KafkaResult.Success)?.value
                    val status =
                        ConnectionTestStatus.Success(
                            clusterId = cluster?.clusterId,
                            brokerCount = cluster?.brokers?.size ?: 0,
                            controllerId = cluster?.controllerId,
                            topicCount = topicsResult.value.size,
                            latencyMs = latency,
                        )
                    val connectionStatus =
                        ConnectionStatus(
                            profileId = profile.id,
                            state = ConnectionState.CONNECTED,
                            brokerCount = cluster?.brokers?.size ?: 0,
                            controllerId = cluster?.controllerId,
                            clusterId = cluster?.clusterId,
                            latencyMs = latency,
                            lastTestedMs = System.currentTimeMillis(),
                        )
                    Pair(status, connectionStatus)
                }

                is KafkaResult.Failure -> {
                    val error = formatConnectionError(topicsResult.error)
                    val status =
                        ConnectionTestStatus.Failure(
                            error = error,
                            suggestion = getSuggestionForError(error),
                        )
                    val connectionStatus =
                        ConnectionStatus(
                            profileId = profile.id,
                            state = ConnectionState.ERROR,
                            error = error,
                            lastTestedMs = System.currentTimeMillis(),
                        )
                    Pair(status, connectionStatus)
                }
            }
        } finally {
            adminService.close()
        }
    } catch (e: Exception) {
        val error = e.message ?: "Unknown error"
        val status =
            ConnectionTestStatus.Failure(
                error = error,
                suggestion = getSuggestionForError(error),
            )
        val connectionStatus =
            ConnectionStatus(
                profileId = profile.id,
                state = ConnectionState.ERROR,
                error = error,
                lastTestedMs = System.currentTimeMillis(),
            )
        Pair(status, connectionStatus)
    }
}

private fun getSuggestionForError(error: String): String? =
    when {
        error.contains("timeout", ignoreCase = true) -> "Check if the broker addresses are correct and reachable"
        error.contains("authentication", ignoreCase = true) -> "Verify your SASL credentials"
        error.contains("ssl", ignoreCase = true) ||
            error.contains("certificate", ignoreCase = true) -> "Check SSL truststore configuration"
        error.contains("connection refused", ignoreCase = true) -> "Ensure Kafka is running and the port is correct"
        else -> null
    }

private fun formatConnectionError(error: Throwable): String {
    val message = error.message ?: error::class.simpleName ?: "Unknown error"
    // Truncate long error messages
    return if (message.length > 200) message.take(200) + "..." else message
}
