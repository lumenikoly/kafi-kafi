package com.lightkafka.core.kafka

import java.time.Duration

data class KafkaConnectionConfig(
    val bootstrapServers: List<String>,
    val clientId: String? = null,
    val properties: Map<String, String> = emptyMap(),
    // Security configuration (as strings to avoid module dependency)
    val securityProtocol: String? = null,
    val saslMechanism: String? = null,
    val saslUsername: String? = null,
    val saslPassword: String? = null,
    // SSL configuration
    val sslTruststorePath: String? = null,
    val sslTruststorePassword: String? = null,
    val sslKeystorePath: String? = null,
    val sslKeystorePassword: String? = null,
    val sslKeyPassword: String? = null,
)

data class TopicSummary(
    val name: String,
    val partitions: Int,
    val internal: Boolean,
)

data class CreateTopicRequest(
    val name: String,
    val partitions: Int = 1,
    val replicationFactor: Short = 1,
    val configs: Map<String, String> = emptyMap(),
)

data class TopicConfigEntry(
    val name: String,
    val value: String,
    val isDefault: Boolean,
    val isReadOnly: Boolean,
    val isSensitive: Boolean,
)

data class TopicConfig(
    val topicName: String,
    val entries: List<TopicConfigEntry>,
)

data class PartitionDetail(
    val partition: Int,
    val leader: Int?,
    val replicas: List<Int>,
    val inSyncReplicas: List<Int>,
    val beginningOffset: Long?,
    val endOffset: Long?,
)

data class TopicStats(
    val topicName: String,
    val partitionCount: Int,
    val replicationFactor: Int,
    val totalMessages: Long?,
    val messageRatePerSecond: Double?,
)

// Consumer Group models
data class ConsumerGroupSummary(
    val groupId: String,
    val state: String?,
    val memberCount: Int,
    val topicCount: Int,
)

data class ConsumerGroupMemberInfo(
    val memberId: String,
    val clientId: String?,
    val clientHost: String?,
    val assignments: List<TopicPartitionInfo>,
)

data class TopicPartitionInfo(
    val topic: String,
    val partition: Int,
)

data class PartitionLagInfo(
    val topic: String,
    val partition: Int,
    val currentOffset: Long?,
    val endOffset: Long?,
    val lag: Long?,
    val memberId: String?,
)

data class ConsumerGroupDetail(
    val groupId: String,
    val state: String,
    val members: List<ConsumerGroupMemberInfo>,
    val partitionLags: List<PartitionLagInfo>,
)

sealed interface OffsetResetSpec {
    data object Earliest : OffsetResetSpec

    data object Latest : OffsetResetSpec

    data class Timestamp(val timestampEpochMillis: Long) : OffsetResetSpec

    data class Offset(val offset: Long) : OffsetResetSpec
}

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

data class ClusterDescription(
    val clusterId: String?,
    val brokers: List<BrokerInfo>,
    val controllerId: String?,
)

data class BrokerInfo(
    val id: String,
    val host: String?,
    val port: Int?,
    val rack: String?,
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

    data class SpecificOffsets(
        val offsets: Map<Int, Long>,
    ) : ConsumerStartPosition

    data class Timestamp(
        val timestampEpochMillis: Long,
    ) : ConsumerStartPosition
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
    data class MessageReceived(
        val message: ConsumedMessage,
    ) : ConsumerEvent

    data class Stats(
        val polledRecords: Int,
        val emittedAtEpochMillis: Long = System.currentTimeMillis(),
    ) : ConsumerEvent

    data class Error(
        val error: KafkaServiceError,
    ) : ConsumerEvent
}
