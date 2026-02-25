package com.lightkafka.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.lightkafka.core.storage.ClusterProfile
import java.util.UUID

private data class ConnectionEditorState(
    val name: String,
    val bootstrapServers: String,
    val testStatus: String?,
)

private data class ConnectionEditorActions(
    val onNameChange: (String) -> Unit,
    val onBootstrapServersChange: (String) -> Unit,
    val onSave: () -> Unit,
    val onNew: () -> Unit,
    val onDelete: () -> Unit,
    val onTest: () -> Unit,
    val onClose: () -> Unit,
)

@Composable
internal fun connectionManagerDialog(
    state: MainUiState,
    onAction: (MainUiAction) -> Unit,
) {
    var selectedProfileId by remember(state.isConnectionManagerOpen) {
        mutableStateOf(state.activeProfileId ?: state.profiles.firstOrNull()?.id)
    }
    val selectedProfile = state.profiles.firstOrNull { it.id == selectedProfileId }
    var name by remember(selectedProfileId, state.isConnectionManagerOpen) {
        mutableStateOf(selectedProfile?.name.orEmpty())
    }
    var bootstrapServers by remember(selectedProfileId, state.isConnectionManagerOpen) {
        mutableStateOf(selectedProfile?.bootstrapServers?.joinToString(",") ?: "localhost:9092")
    }
    var testStatus by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = { onAction(MainUiAction.SetConnectionManagerOpen(false)) }) {
        Surface(
            modifier = Modifier.width(900.dp).height(620.dp),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp,
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                profileListPane(
                    state = state,
                    selectedProfileId = selectedProfileId,
                    onSelectProfile = { profile ->
                        selectedProfileId = profile.id
                        name = profile.name
                        bootstrapServers = profile.bootstrapServers.joinToString(",")
                    },
                )

                VerticalDivider()

                connectionEditorPane(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    state =
                        ConnectionEditorState(
                            name = name,
                            bootstrapServers = bootstrapServers,
                            testStatus = testStatus,
                        ),
                    actions =
                        ConnectionEditorActions(
                            onNameChange = { name = it },
                            onBootstrapServersChange = { bootstrapServers = it },
                            onSave = {
                                val profileId = selectedProfileId ?: UUID.randomUUID().toString()
                                val profile = toProfile(profileId, name, bootstrapServers)
                                onAction(MainUiAction.UpsertProfile(profile))
                                onAction(MainUiAction.SetActiveProfile(profile.id))
                                selectedProfileId = profile.id
                                testStatus = "Saved successfully."
                            },
                            onNew = {
                                selectedProfileId = null
                                name = "New Connection"
                                bootstrapServers = "localhost:9092"
                                testStatus = "New profile created. Please save."
                            },
                            onDelete = {
                                selectedProfileId?.let { onAction(MainUiAction.DeleteProfile(it)) }
                                selectedProfileId =
                                    state.profiles
                                        .firstOrNull { it.id != selectedProfileId }
                                        ?.id
                                name = ""
                                bootstrapServers = ""
                                testStatus = "Deleted successfully."
                            },
                            onTest = {
                                testStatus =
                                    if (bootstrapServers.isBlank()) {
                                        "Connection test failed: bootstrap servers required"
                                    } else {
                                        "Connection test passed (UI stub)"
                                    }
                            },
                            onClose = { onAction(MainUiAction.SetConnectionManagerOpen(false)) },
                        ),
                )
            }
        }
    }
}

@Composable
private fun profileListPane(
    state: MainUiState,
    selectedProfileId: String?,
    onSelectProfile: (ClusterProfile) -> Unit,
) {
    Column(
        modifier = Modifier.width(300.dp).fillMaxHeight().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Profiles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(state.profiles) { profile ->
                val selected = profile.id == selectedProfileId
                val containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onSelectProfile(profile) },
                    color = containerColor,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(profile.name, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                        Text(
                            profile.bootstrapServers.joinToString(","),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun connectionEditorPane(
    modifier: Modifier = Modifier,
    state: ConnectionEditorState,
    actions: ConnectionEditorActions,
) {
    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Connection Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = state.name,
            onValueChange = actions.onNameChange,
            label = { Text("Profile name") },
            placeholder = { Text("e.g. Local Cluster") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.bootstrapServers,
            onValueChange = actions.onBootstrapServersChange,
            label = { Text("Bootstrap servers (comma separated)") },
            placeholder = { Text("localhost:9092") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = actions.onSave) { Text("Save Connection") }
            TextButton(onClick = actions.onNew) { Text("New") }
            TextButton(onClick = actions.onDelete) { Text("Delete") }
            TextButton(onClick = actions.onTest) { Text("Test") }
        }

        if (state.testStatus != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    state.testStatus,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = actions.onClose) { Text("Close") }
        }
    }
}

private fun toProfile(
    profileId: String,
    name: String,
    bootstrapServers: String,
): ClusterProfile {
    val servers =
        bootstrapServers
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .ifEmpty { listOf("localhost:9092") }
    return ClusterProfile(
        id = profileId,
        name = name.ifBlank { "New Profile" },
        bootstrapServers = servers,
    )
}
