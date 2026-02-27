package com.lightkafka.ui

import com.lightkafka.core.kafka.KafkaResult
import com.lightkafka.core.kafka.KafkaServiceError
import com.lightkafka.core.kafka.TopicSummary

internal suspend fun probeConnection(
    bootstrapServersInput: String,
    probe: suspend (List<String>) -> KafkaResult<List<TopicSummary>>,
): String {
    val servers = parseBootstrapServers(bootstrapServersInput)
    if (servers.isEmpty()) {
        return "Connection test failed: bootstrap servers required"
    }

    return when (val result = probe(servers)) {
        is KafkaResult.Success -> "Connection test passed: broker reachable"
        is KafkaResult.Failure -> "Connection test failed: ${formatConnectionError(result.error)}"
    }
}

internal fun nextSelectedProfileIdAfterDelete(
    profiles: List<com.lightkafka.core.storage.ClusterProfile>,
    deletedProfileId: String,
): String? = profiles.firstOrNull { it.id != deletedProfileId }?.id

internal fun formatConnectionError(error: KafkaServiceError): String =
    when (error) {
        is KafkaServiceError.OperationFailed -> error.reason
        is KafkaServiceError.Timeout -> "Operation timed out after ${error.timeout.seconds}s while ${error.operation}"
    }

private fun parseBootstrapServers(rawValue: String): List<String> =
    rawValue
        .split(',')
        .map(String::trim)
        .filter(String::isNotBlank)
