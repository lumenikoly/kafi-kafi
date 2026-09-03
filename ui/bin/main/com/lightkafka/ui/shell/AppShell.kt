package com.lightkafka.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.lightkafka.ui.connection.ConnectionPageContext
import com.lightkafka.ui.infra.AppBackgroundColor
import com.lightkafka.ui.infra.KraftLauncher

@Suppress("ktlint:standard:function-naming")
@Composable
fun AppShell(
    store: Store<ShellState, ShellAction>,
    modifier: Modifier = Modifier,
    connectionPageCtx: ConnectionPageContext? = null,
    kraftLauncher: KraftLauncher? = null,
) {
    val state by store.state.collectAsState()

    Row(modifier = modifier.fillMaxSize()) {
        Sidebar(
            currentSection = state.sidebarSection,
            onSectionClick = { section ->
                store.dispatch(ShellAction.NavigateSidebar(section))
                store.dispatch(ShellAction.OpenTab(TabType.valueOf(section.name)))
            },
            kraftLauncher = kraftLauncher,
        )
        Column(modifier = Modifier.fillMaxSize().background(AppBackgroundColor)) {
            if (state.tabs.isNotEmpty()) {
                TabBar(
                    tabs = state.tabs,
                    activeTabId = state.activeTabId,
                    onTabClick = { id -> store.dispatch(ShellAction.SwitchTab(id)) },
                    onTabClose = { id -> store.dispatch(ShellAction.CloseTab(id)) },
                )
            }
            Workspace(
                activeTab = state.tabs.find { it.id == state.activeTabId },
                connectionPageCtx = connectionPageCtx,
            )
        }
    }
}
