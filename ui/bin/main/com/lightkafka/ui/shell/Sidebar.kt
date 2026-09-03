package com.lightkafka.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.lightkafka.ui.infra.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class NavItem(
    val section: SidebarSection,
    val label: String,
)

private val NAV_ITEMS =
    listOf(
        NavItem(SidebarSection.TOPICS, "Topics"),
        NavItem(SidebarSection.BROKERS, "Brokers"),
        NavItem(SidebarSection.CONSUMER_GROUPS, "Consumer Groups"),
        NavItem(SidebarSection.CONNECTIONS, "Connections"),
        NavItem(SidebarSection.SETTINGS, "Settings"),
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
        modifier =
            modifier
                .fillMaxHeight()
                .width(208.dp)
                .background(SidebarBackgroundColor)
                .padding(vertical = 16.dp),
    ) {
        Text(
            text = "Light Kafka",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Text(
            text = "Kafka workspace",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp),
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(NAV_ITEMS) { item ->
                val isSelected = item.section == currentSection
                val bgColor = if (isSelected) SurfaceHover else SidebarBackgroundColor
                val textColor = if (isSelected) TextPrimary else TextMuted

                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                    modifier =
                        Modifier
                            .padding(horizontal = 10.dp)
                            .fillMaxWidth()
                            .background(bgColor, MaterialTheme.shapes.small)
                            .clickable { onSectionClick(item.section) }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                )
            }
        }

        // KRaft launcher section at bottom of sidebar
        if (kraftLauncher != null) {
            KRaftLauncherSection(kraftLauncher = kraftLauncher)
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun KRaftLauncherSection(kraftLauncher: KraftLauncher) {
    var status by remember { mutableStateOf<KraftStatusResult?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Poll once on mount
    LaunchedEffect(Unit) {
        status = withContext(Dispatchers.IO) { kraftLauncher.status() }
    }

    // Re-poll every 10 seconds when RUNNING to detect manual stops
    LaunchedEffect(status?.state) {
        if (status?.state == KraftContainerState.RUNNING) {
            while (true) {
                delay(10_000L)
                val result = withContext(Dispatchers.IO) { kraftLauncher.status() }
                status = result
                if (result.state != KraftContainerState.RUNNING) break
            }
        }
    }

    HorizontalDivider(
        color = BorderSubtle,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = "Local Kafka",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        val currentStatus = status
        if (currentStatus == null) {
            Text(
                text = "Checking status…",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        } else if (!currentStatus.success && currentStatus.state == KraftContainerState.UNKNOWN) {
            Text(
                text = currentStatus.message,
                style = MaterialTheme.typography.bodySmall,
                color = StatusError,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            KRaftStatusRow(currentStatus)
            Spacer(modifier = Modifier.height(6.dp))
            KRaftActionButton(
                currentStatus = currentStatus,
                onStart = {
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) { kraftLauncher.launch() }
                        status = withContext(Dispatchers.IO) { kraftLauncher.status() }
                    }
                },
                onStop = {
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) { kraftLauncher.stop() }
                        status = withContext(Dispatchers.IO) { kraftLauncher.status() }
                    }
                },
            )
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun KRaftStatusRow(status: KraftStatusResult) {
    val dotColor =
        when (status.state) {
            KraftContainerState.RUNNING -> StatusSuccess
            KraftContainerState.STOPPED -> StatusError
            KraftContainerState.NOT_FOUND -> TextMuted
            KraftContainerState.UNKNOWN -> TextMuted
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape),
        )
        Text(
            text = status.message,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun KRaftActionButton(
    currentStatus: KraftStatusResult,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    when (currentStatus.state) {
        KraftContainerState.RUNNING -> {
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = StatusError),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                Text("Stop", style = MaterialTheme.typography.labelSmall)
            }
        }
        KraftContainerState.STOPPED, KraftContainerState.NOT_FOUND -> {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                Text("Start", style = MaterialTheme.typography.labelSmall)
            }
        }
        else -> { /* no action button for unknown states */ }
    }
}
