package com.lightkafka.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lightkafka.ui.infra.AccentViolet
import com.lightkafka.ui.infra.BorderSubtle
import com.lightkafka.ui.infra.KraftContainerState
import com.lightkafka.ui.infra.KraftLauncher
import com.lightkafka.ui.infra.KraftStatusResult
import com.lightkafka.ui.infra.SidebarBackgroundColor
import com.lightkafka.ui.infra.StatusError
import com.lightkafka.ui.infra.StatusSuccess
import com.lightkafka.ui.infra.SurfaceHover
import com.lightkafka.ui.infra.TextMuted
import com.lightkafka.ui.infra.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class NavItem(val section: SidebarSection, val label: String, val icon: ImageVector)

private val NAV_ITEMS =
    listOf(
        NavItem(SidebarSection.TOPICS, "Topics", Icons.Outlined.AccountTree),
        NavItem(SidebarSection.BROKERS, "Brokers", Icons.Outlined.Storage),
        NavItem(SidebarSection.CONSUMER_GROUPS, "Consumer Groups", Icons.Outlined.Groups),
        NavItem(SidebarSection.CONNECTIONS, "Connections", Icons.Outlined.Cable),
        NavItem(SidebarSection.SETTINGS, "Settings", Icons.Outlined.Settings),
    )

@Suppress("ktlint:standard:function-naming")
@Composable
fun Sidebar(
    currentSection: SidebarSection,
    onSectionClick: (SidebarSection) -> Unit,
    modifier: Modifier = Modifier,
    kraftLauncher: KraftLauncher? = null,
) {
    Column(
        modifier = modifier.fillMaxHeight().width(64.dp).background(SidebarBackgroundColor).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(AccentViolet, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            Text("K", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        }
        Column(
            modifier = Modifier.padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            NAV_ITEMS.forEach { item ->
                RailButton(
                    label = item.label,
                    icon = item.icon,
                    selected = item.section == currentSection,
                    onClick = { onSectionClick(item.section) },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (kraftLauncher != null) KRaftControl(kraftLauncher)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("ktlint:standard:function-naming")
@Composable
private fun RailButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Right),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(44.dp),
            colors =
                IconButtonDefaults.iconButtonColors(
                    containerColor = if (selected) SurfaceHover else Color.Transparent,
                    contentColor = if (selected) AccentViolet else TextMuted,
                ),
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun KRaftControl(kraftLauncher: KraftLauncher) {
    var status by remember { mutableStateOf<KraftStatusResult?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { status = withContext(Dispatchers.IO) { kraftLauncher.status() } }

    Box(modifier = Modifier.padding(bottom = 6.dp).size(8.dp).background(status.statusColor(), CircleShape))
    RailButton(
        label = if (status?.state == KraftContainerState.RUNNING) "Stop local Kafka" else "Start local Kafka",
        icon = if (status?.state == KraftContainerState.RUNNING) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
        selected = false,
        onClick = {
            scope.launch {
                status =
                    withContext(Dispatchers.IO) {
                        if (status?.state == KraftContainerState.RUNNING) {
                            kraftLauncher.stop()
                        } else {
                            kraftLauncher.launch()
                        }
                        kraftLauncher.status()
                    }
            }
        },
    )
}

private fun KraftStatusResult?.statusColor(): Color =
    when (this?.state) {
        KraftContainerState.RUNNING -> StatusSuccess
        KraftContainerState.STOPPED -> StatusError
        else -> BorderSubtle
    }
