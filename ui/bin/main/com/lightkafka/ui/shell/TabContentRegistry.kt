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
import com.lightkafka.ui.consumer.ConsumerGroupsPage
import com.lightkafka.ui.settings.SettingsPage
import com.lightkafka.ui.topic.CreateTopicPage
import com.lightkafka.ui.topic.TopicDetailPage
import com.lightkafka.ui.topic.TopicListPage

object TabContentRegistry {
    @Suppress("ktlint:standard:function-naming", "CyclomaticComplexMethod")
    @Composable
    fun Content(
        tab: TabInstance?,
        connectionPageCtx: ConnectionPageContext,
        modifier: Modifier = Modifier,
    ) {
        if (tab == null) {
            EmptyWorkspace(modifier)
            return
        }
        when (tab.type) {
            TabType.TOPICS ->
                TopicListPage(
                    connectionStateFlow = connectionPageCtx.connectionStateFlow,
                    shellStore = connectionPageCtx.shellStore,
                    modifier = modifier,
                )
            TabType.BROKERS ->
                BrokersPage(
                    connectionStateFlow = connectionPageCtx.connectionStateFlow,
                    shellStore = connectionPageCtx.shellStore,
                    modifier = modifier,
                )
            TabType.CONSUMER_GROUPS ->
                ConsumerGroupsPage(
                    connectionStateFlow = connectionPageCtx.connectionStateFlow,
                    shellStore = connectionPageCtx.shellStore,
                    modifier = modifier,
                )
            TabType.SETTINGS ->
                SettingsPage(
                    settingsStore = connectionPageCtx.settingsStore,
                    settingsStateFlow = connectionPageCtx.settingsStateFlow,
                    modifier = modifier,
                )
            TabType.CONNECTIONS -> ConnectionPage(ctx = connectionPageCtx, modifier = modifier)
            TabType.CLUSTER_OVERVIEW ->
                ClusterOverviewPage(
                    connectionStateFlow = connectionPageCtx.connectionStateFlow,
                    shellStore = connectionPageCtx.shellStore,
                    modifier = modifier,
                )
            TabType.TOPIC_DETAIL ->
                TopicDetailPage(
                    connectionStateFlow = connectionPageCtx.connectionStateFlow,
                    settingsStateFlow = connectionPageCtx.settingsStateFlow,
                    shellStore = connectionPageCtx.shellStore,
                    topicName = tab.title,
                    modifier = modifier,
                )
            TabType.CREATE_TOPIC ->
                CreateTopicPage(
                    connectionStateFlow = connectionPageCtx.connectionStateFlow,
                    shellStore = connectionPageCtx.shellStore,
                    modifier = modifier,
                )
        }
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
