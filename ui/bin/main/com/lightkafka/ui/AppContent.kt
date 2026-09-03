package com.lightkafka.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.lightkafka.ui.connection.AppConnectionState
import com.lightkafka.ui.connection.ConnectionPageContext
import com.lightkafka.ui.connection.createProfileStore
import com.lightkafka.ui.infra.KraftLauncher
import com.lightkafka.ui.shell.AppShell
import com.lightkafka.ui.shell.KafiTheme
import com.lightkafka.ui.shell.ShellReducer
import com.lightkafka.ui.shell.ShellState
import com.lightkafka.ui.shell.Store
import kotlinx.coroutines.flow.MutableStateFlow

@Suppress("ktlint:standard:function-naming")
@Composable
fun appContent() {
    val store = remember { Store(initialState = ShellState(), reducer = ShellReducer) }
    val profileStore = remember { createProfileStore() }
    val kraftLauncher = remember { KraftLauncher() }
    val coroutineScope = rememberCoroutineScope()
    val connectionStateFlow =
        remember {
            MutableStateFlow<AppConnectionState>(AppConnectionState())
        }
    val ctx =
        remember {
            ConnectionPageContext(
                profileStore = profileStore,
                connectionStateFlow = connectionStateFlow,
                shellStore = store,
                coroutineScope = coroutineScope,
            )
        }

    KafiTheme {
        AppShell(store = store, connectionPageCtx = ctx, kraftLauncher = kraftLauncher)
    }
}
