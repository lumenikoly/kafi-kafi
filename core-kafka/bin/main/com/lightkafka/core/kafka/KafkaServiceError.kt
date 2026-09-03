package com.lightkafka.core.kafka

import java.time.Duration

sealed interface KafkaServiceError {
    val operation: String

    data class Timeout(
        override val operation: String,
        val timeout: Duration,
    ) : KafkaServiceError

    data class OperationFailed(
        override val operation: String,
        val reason: String,
        val cause: Throwable? = null,
    ) : KafkaServiceError
}
