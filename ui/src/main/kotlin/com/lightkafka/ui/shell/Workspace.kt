package com.lightkafka.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lightkafka.ui.connection.ConnectionPageContext
import com.lightkafka.ui.infra.AppBackgroundColor

@Suppress("ktlint:standard:function-naming")
@Composable
fun Workspace(
    activeTab: TabInstance?,
    connectionPageCtx: ConnectionPageContext,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().background(AppBackgroundColor),
    ) {
        TabContentRegistry.Content(
            tab = activeTab,
            modifier = Modifier.weight(1f),
            connectionPageCtx = connectionPageCtx,
        )
    }
}
