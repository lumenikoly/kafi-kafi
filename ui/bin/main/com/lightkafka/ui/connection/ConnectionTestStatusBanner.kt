package com.lightkafka.ui.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lightkafka.ui.infra.StatusError
import com.lightkafka.ui.infra.StatusErrorBg
import com.lightkafka.ui.infra.StatusSuccess
import com.lightkafka.ui.infra.StatusSuccessBg
import com.lightkafka.ui.infra.SurfaceHover
import com.lightkafka.ui.infra.TextMuted
import com.lightkafka.ui.infra.TextSecondary

@Suppress("ktlint:standard:function-naming")
@Composable
internal fun TestStatusBanner(status: ConnectionTestStatus) {
    when (status) {
        is ConnectionTestStatus.InProgress -> TestStatusInProgress(status)
        is ConnectionTestStatus.Success -> TestStatusSuccess(status)
        is ConnectionTestStatus.Failure -> TestStatusFailure(status)
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun TestStatusInProgress(status: ConnectionTestStatus.InProgress) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(SurfaceHover, MaterialTheme.shapes.small)
                .padding(12.dp),
    ) { Text(status.message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun TestStatusSuccess(status: ConnectionTestStatus.Success) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(StatusSuccessBg, MaterialTheme.shapes.small)
                .padding(12.dp),
    ) {
        Column {
            Text("Connection successful", style = MaterialTheme.typography.bodyMedium, color = StatusSuccess)
            Text(
                "${status.topicCount} topics found • ${status.latencyMs}ms",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun TestStatusFailure(status: ConnectionTestStatus.Failure) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(StatusErrorBg, MaterialTheme.shapes.small)
                .padding(12.dp),
    ) {
        Column {
            Text("Connection failed", style = MaterialTheme.typography.bodyMedium, color = StatusError)
            Text(status.error, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            if (status.suggestion != null) {
                Text(
                    "Suggestion: ${status.suggestion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
        }
    }
}
