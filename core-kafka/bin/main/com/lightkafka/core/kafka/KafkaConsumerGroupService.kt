package com.lightkafka.core.kafka

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration

interface KafkaConsumerGroupService {
    suspend fun listGroups(): KafkaResult<List<ConsumerGroupSummary>>

    suspend fun describeGroup(groupId: String): KafkaResult<ConsumerGroupDetail?>

    suspend fun resetOffsets(
        groupId: String,
        topic: String,
        spec: OffsetResetSpec,
    ): KafkaResult<Unit>

    suspend fun deleteGroup(groupId: String): KafkaResult<Unit>

    suspend fun close()
}

class DefaultKafkaConsumerGroupService(
    private val connectionConfig: KafkaConnectionConfig,
    private val clientFactory: KafkaConsumerGroupClientFactory = defaultKafkaConsumerGroupClientFactory,
    private val operationTimeout: Duration = Duration.ofSeconds(15),
) : KafkaConsumerGroupService {
    private val clientMutex = Mutex()
    private var client: KafkaConsumerGroupClient? = null

    override suspend fun listGroups(): KafkaResult<List<ConsumerGroupSummary>> =
        runWithKafkaResult(operation = "list consumer groups", timeout = operationTimeout) {
            client().listGroups()
        }

    override suspend fun describeGroup(groupId: String): KafkaResult<ConsumerGroupDetail?> =
        runWithKafkaResult(operation = "describe consumer group", timeout = operationTimeout) {
            require(groupId.isNotBlank()) { "Consumer group ID is required" }
            client().describeGroup(groupId)
        }

    override suspend fun resetOffsets(
        groupId: String,
        topic: String,
        spec: OffsetResetSpec,
    ): KafkaResult<Unit> =
        runWithKafkaResult(operation = "reset consumer group offsets", timeout = operationTimeout) {
            require(groupId.isNotBlank()) { "Consumer group ID is required" }
            require(topic.isNotBlank()) { "Topic is required" }
            if (spec is OffsetResetSpec.Timestamp) {
                require(spec.timestampEpochMillis >= 0) { "Timestamp must be zero or greater" }
            }
            client().resetOffsets(groupId, topic, spec)
        }

    override suspend fun deleteGroup(groupId: String): KafkaResult<Unit> =
        runWithKafkaResult(operation = "delete consumer group", timeout = operationTimeout) {
            require(groupId.isNotBlank()) { "Consumer group ID is required" }
            client().deleteGroup(groupId)
        }

    override suspend fun close() {
        val groupClient =
            clientMutex.withLock {
                val current = client
                client = null
                current
            }
        groupClient?.close()
    }

    private suspend fun client(): KafkaConsumerGroupClient =
        clientMutex.withLock {
            client ?: clientFactory.create(connectionConfig).also { createdClient ->
                client = createdClient
            }
        }
}
