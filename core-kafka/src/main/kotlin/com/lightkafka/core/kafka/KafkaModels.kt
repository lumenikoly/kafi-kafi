package com.lightkafka.core.kafka

import java.time.Duration

data class KafkaConnectionConfig(
    val bootstrapServers: List<String>,
    val clientId: String? = null,
    val properties: Map<String, String> = emptyMap(),
)

data class TopicSummary(
    val name: String,
    val partitions: Int,
    val internal: Boolean,
)

data class TopicPartitionDescription(
    val partition: Int,
    val leader: String?,
    val replicas: List<String>,
    val inSyncReplicas: List<String>,
)

data class TopicDescription(
    val name: String,
    val internal: Boolean,
    val partitions: List<TopicPartitionDescription>,
    val configs: Map<String, String>,
)

data class ProducerMessage(
    val topic: String,
    val key: ByteArray? = null,
    val value: ByteArray? = null,
    val headers: Map<String, ByteArray?> = emptyMap(),
    val partition: Int? = null,
    val timestamp: Long? = null,
)

data class ProducerSendResult(
    val topic: String,
    val partition: Int,
    val offset: Long,
    val timestamp: Long,
)

sealed interface ConsumerStartPosition {
    data object Earliest : ConsumerStartPosition

    data object Latest : ConsumerStartPosition

    data class SpecificOffsets(val offsets: Map<Int, Long>) : ConsumerStartPosition

    data class Timestamp(val timestampEpochMillis: Long) : ConsumerStartPosition
}

data class ConsumerSessionRequest(
    val topic: String,
    val partitions: Set<Int>? = null,
    val startPosition: ConsumerStartPosition = ConsumerStartPosition.Latest,
    val groupId: String? = null,
    val autoCommit: Boolean = false,
    val maxPollRecords: Int = 500,
    val pollTimeout: Duration = Duration.ofMillis(500),
    val properties: Map<String, String> = emptyMap(),
)

data class ConsumedMessage(
    val topic: String,
    val partition: Int,
    val offset: Long,
    val timestamp: Long,
    val key: ByteArray?,
    val value: ByteArray?,
    val headers: Map<String, ByteArray?> = emptyMap(),
)

sealed interface ConsumerEvent {
    data class MessageReceived(val message: ConsumedMessage) : ConsumerEvent

    data class Stats(
        val polledRecords: Int,
        val emittedAtEpochMillis: Long = System.currentTimeMillis(),
    ) : ConsumerEvent

    data class Error(val error: KafkaServiceError) : ConsumerEvent
}
