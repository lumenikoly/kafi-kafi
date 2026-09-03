package com.lightkafka.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.lightkafka.ui.broker.BrokersPage
import com.lightkafka.ui.cluster.ClusterOverviewPage
import com.lightkafka.ui.connection.ConnectionPage
import com.lightkafka.ui.connection.ConnectionPageContext
import com.lightkafka.ui.topic.CreateTopicPage
import com.lightkafka.ui.topic.TopicDetailPage
import com.lightkafka.ui.topic.TopicListPage

object TabContentRegistry {
    @Suppress("ktlint:standard:function-naming", "CyclomaticComplexMethod")
    @Composable
    fun Content(
        tab: TabInstance?,
        modifier: Modifier = Modifier,
        connectionPageCtx: ConnectionPageContext? = null,
    ) {
        if (tab == null) {
            EmptyWorkspace(modifier)
            return
        }
        when (tab.type) {
            TabType.TOPICS -> renderTopicsTab(connectionPageCtx, modifier)
            TabType.BROKERS -> renderBrokersTab(connectionPageCtx, modifier)
            TabType.CONSUMER_GROUPS -> PlaceholderTab("Consumer Groups", modifier)
            TabType.SETTINGS -> PlaceholderTab("Settings", modifier)
            TabType.CONNECTIONS -> renderConnectionsTab(connectionPageCtx, modifier)
            TabType.CLUSTER_OVERVIEW -> renderClusterOverviewTab(connectionPageCtx, modifier)
            TabType.TOPIC_DETAIL -> renderTopicDetailTab(tab.title, connectionPageCtx, modifier)
            TabType.CREATE_TOPIC -> renderCreateTopicTab(connectionPageCtx, modifier)
        }
    }
}

// ── Per-type renderers ─────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun renderTopicsTab(
    ctx: ConnectionPageContext?,
    modifier: Modifier,
) {
    if (ctx != null) {
        TopicListPage(
            connectionStateFlow = ctx.connectionStateFlow,
            shellStore = ctx.shellStore,
            modifier = modifier,
        )
    } else {
        PlaceholderTab("Topics", modifier)
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun renderBrokersTab(
    ctx: ConnectionPageContext?,
    modifier: Modifier,
) {
    if (ctx != null) {
        BrokersPage(
            connectionStateFlow = ctx.connectionStateFlow,
            shellStore = ctx.shellStore,
            modifier = modifier,
        )
    } else {
        PlaceholderTab("Brokers", modifier)
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun renderConnectionsTab(
    ctx: ConnectionPageContext?,
    modifier: Modifier,
) {
    if (ctx != null) {
        ConnectionPage(ctx = ctx, modifier = modifier)
    } else {
        PlaceholderTab("Connections", modifier)
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun renderClusterOverviewTab(
    ctx: ConnectionPageContext?,
    modifier: Modifier,
) {
    if (ctx != null) {
        ClusterOverviewPage(
            connectionStateFlow = ctx.connectionStateFlow,
            shellStore = ctx.shellStore,
            modifier = modifier,
        )
    } else {
        PlaceholderTab("Cluster Overview", modifier)
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun renderTopicDetailTab(
    topicName: String,
    ctx: ConnectionPageContext?,
    modifier: Modifier,
) {
    if (ctx != null) {
        TopicDetailPage(
            connectionStateFlow = ctx.connectionStateFlow,
            shellStore = ctx.shellStore,
            topicName = topicName,
            modifier = modifier,
        )
    } else {
        PlaceholderTab("Topic: $topicName", modifier)
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun renderCreateTopicTab(
    ctx: ConnectionPageContext?,
    modifier: Modifier,
) {
    if (ctx != null) {
        CreateTopicPage(
            connectionStateFlow = ctx.connectionStateFlow,
            shellStore = ctx.shellStore,
            modifier = modifier,
        )
    } else {
        PlaceholderTab("Create Topic", modifier)
    }
}

// ── Shared placeholders ────────────────────────────────────────────────────

@Suppress("ktlint:standard:function-naming")
@Composable
private fun PlaceholderTab(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            label,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun EmptyWorkspace(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "Open a section from the sidebar to get started",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
