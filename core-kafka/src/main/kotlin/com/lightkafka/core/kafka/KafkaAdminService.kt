package com.lightkafka.core.kafka

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration

interface KafkaAdminService {
    suspend fun listTopics(includeInternal: Boolean = false): KafkaResult<List<TopicSummary>>

    suspend fun describeTopic(topicName: String): KafkaResult<TopicDescription>

    suspend fun describeCluster(): KafkaResult<ClusterDescription?>

    suspend fun createTopic(request: CreateTopicRequest): KafkaResult<Unit>

    suspend fun deleteTopic(topicName: String): KafkaResult<Unit>

    suspend fun getTopicConfig(topicName: String): KafkaResult<TopicConfig>

    suspend fun updateTopicConfig(
        topicName: String,
        configs: Map<String, String>,
    ): KafkaResult<Unit>

    suspend fun addPartitions(
        topicName: String,
        newPartitionCount: Int,
    ): KafkaResult<Unit>

    suspend fun getPartitionDetails(topicName: String): KafkaResult<List<PartitionDetail>>

    suspend fun close()
}

class DefaultKafkaAdminService(
    private val connectionConfig: KafkaConnectionConfig,
    private val clientFactory: KafkaAdminClientFactory = defaultKafkaAdminClientFactory,
    private val operationTimeout: Duration = Duration.ofSeconds(10),
) : KafkaAdminService {
    private val clientMutex = Mutex()
    private var client: KafkaAdminClient? = null

    override suspend fun listTopics(includeInternal: Boolean): KafkaResult<List<TopicSummary>> =
        runWithKafkaResult(operation = "list topics", timeout = operationTimeout) {
            val adminClient = client()
            adminClient.listTopics(includeInternal)
        }

    override suspend fun describeTopic(topicName: String): KafkaResult<TopicDescription> =
        runWithKafkaResult(operation = "describe topic", timeout = operationTimeout) {
            val adminClient = client()
            adminClient.describeTopic(topicName)
        }

    override suspend fun describeCluster(): KafkaResult<ClusterDescription?> =
        runWithKafkaResult(operation = "describe cluster", timeout = operationTimeout) {
            val adminClient = client()
            adminClient.describeCluster()
        }

    override suspend fun createTopic(request: CreateTopicRequest): KafkaResult<Unit> =
        runWithKafkaResult(operation = "create topic", timeout = operationTimeout) {
            val adminClient = client()
            adminClient.createTopic(request)
        }

    override suspend fun deleteTopic(topicName: String): KafkaResult<Unit> =
        runWithKafkaResult(operation = "delete topic", timeout = operationTimeout) {
            val adminClient = client()
            adminClient.deleteTopic(topicName)
        }

    override suspend fun getTopicConfig(topicName: String): KafkaResult<TopicConfig> =
        runWithKafkaResult(operation = "get topic config", timeout = operationTimeout) {
            val adminClient = client()
            adminClient.getTopicConfig(topicName)
        }

    override suspend fun updateTopicConfig(
        topicName: String,
        configs: Map<String, String>,
    ): KafkaResult<Unit> =
        runWithKafkaResult(operation = "update topic config", timeout = operationTimeout) {
            val adminClient = client()
            adminClient.updateTopicConfig(topicName, configs)
        }

    override suspend fun addPartitions(
        topicName: String,
        newPartitionCount: Int,
    ): KafkaResult<Unit> =
        runWithKafkaResult(operation = "add partitions", timeout = operationTimeout) {
            val adminClient = client()
            adminClient.addPartitions(topicName, newPartitionCount)
        }

    override suspend fun getPartitionDetails(topicName: String): KafkaResult<List<PartitionDetail>> =
        runWithKafkaResult(operation = "get partition details", timeout = operationTimeout) {
            val adminClient = client()
            adminClient.getPartitionDetails(topicName)
        }

    override suspend fun close() {
        val adminClient =
            clientMutex.withLock {
                val current = client
                client = null
                current
            }
        adminClient?.close()
    }

    private suspend fun client(): KafkaAdminClient =
        clientMutex.withLock {
            client ?: clientFactory.create(connectionConfig).also { createdClient ->
                client = createdClient
            }
        }
}
