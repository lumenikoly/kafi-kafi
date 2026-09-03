package com.lightkafka.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lightkafka.ui.connection.ConnectionPageContext
import com.lightkafka.ui.connection.ConnectionState
import com.lightkafka.ui.infra.AppBackgroundColor
import com.lightkafka.ui.infra.BorderSubtle
import com.lightkafka.ui.infra.KraftLauncher
import com.lightkafka.ui.infra.StatusSuccess
import com.lightkafka.ui.infra.SurfaceCard
import com.lightkafka.ui.infra.TextMuted
import com.lightkafka.ui.infra.TextPrimary

/*
 * THESIS: Kafka resources behave like an operator console, not a dashboard.
 * OWN-WORLD: graphite planes, cobalt focus, compact controls, crisp dividers.
 * STORY: select a resource, inspect live state, act without leaving the workspace.
 * FIRST VIEWPORT: icon rail, status bar, tabs, dense resource workspace.
 * FORM: resource browser + data table + inspector; operator-console-resource-browser.
 * FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, and DESIGN.md
 */

@Suppress("ktlint:standard:function-naming")
@Composable
fun AppShell(
    store: Store<ShellState, ShellAction>,
    connectionPageCtx: ConnectionPageContext,
    modifier: Modifier = Modifier,
    kraftLauncher: KraftLauncher? = null,
) {
    val state by store.state.collectAsState()
    val connection by connectionPageCtx.connectionStateFlow.collectAsState()

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
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp).background(SurfaceCard).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.size(7.dp).background(
                        if (connection.connectionStatus?.state == ConnectionState.CONNECTED) {
                            StatusSuccess
                        } else {
                            TextMuted
                        },
                        CircleShape,
                    ),
                )
                Text(
                    connection.activeProfile?.name ?: "No cluster",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                )
                connection.connectionStatus?.latencyMs?.takeIf { it > 0 }?.let {
                    Text("$it ms", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle))
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
