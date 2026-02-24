package com.lightkafka.core.kafka

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration

interface KafkaAdminService {
    suspend fun listTopics(includeInternal: Boolean = false): KafkaResult<List<TopicSummary>>

    suspend fun describeTopic(topicName: String): KafkaResult<TopicDescription>

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
