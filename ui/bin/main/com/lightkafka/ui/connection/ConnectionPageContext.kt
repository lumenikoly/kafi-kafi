package com.lightkafka.ui.connection

import com.lightkafka.core.storage.AppSettings
import com.lightkafka.core.storage.ProfileStore
import com.lightkafka.core.storage.SettingsStore
import com.lightkafka.ui.shell.ShellAction
import com.lightkafka.ui.shell.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Callbacks for connection editor actions.
 * Grouped to keep composable parameter counts under detekt limits.
 */
data class EditorActions(
    val onStateChange: (ConnectionEditorState) -> Unit,
    val onTest: () -> Unit,
    val onSave: () -> Unit,
    val onConnect: () -> Unit,
    val onDelete: () -> Unit,
)

/**
 * Context object holding the dependencies needed by the connection page.
 * Reduces parameter count for composable functions.
 */
data class ConnectionPageContext(
    val profileStore: ProfileStore,
    val settingsStore: SettingsStore,
    val settingsStateFlow: MutableStateFlow<AppSettings>,
    val connectionStateFlow: MutableStateFlow<AppConnectionState>,
    val shellStore: Store<*, ShellAction>,
    val coroutineScope: CoroutineScope,
)
