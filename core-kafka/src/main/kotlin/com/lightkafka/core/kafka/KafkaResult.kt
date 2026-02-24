package com.lightkafka.core.kafka

sealed interface KafkaResult<out T> {
    data class Success<T>(val value: T) : KafkaResult<T>

    data class Failure(val error: KafkaServiceError) : KafkaResult<Nothing>
}
