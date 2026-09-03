package com.lightkafka.core.kafka

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration

interface KafkaProducerService {
    suspend fun send(message: ProducerMessage): KafkaResult<ProducerSendResult>

    suspend fun close()
}

class DefaultKafkaProducerService(
    private val connectionConfig: KafkaConnectionConfig,
    private val clientFactory: KafkaProducerClientFactory = defaultKafkaProducerClientFactory,
    private val operationTimeout: Duration = Duration.ofSeconds(10),
) : KafkaProducerService {
    private val clientMutex = Mutex()
    private var client: KafkaProducerClient? = null

    override suspend fun send(message: ProducerMessage): KafkaResult<ProducerSendResult> =
        runWithKafkaResult(operation = "send message", timeout = operationTimeout) {
            val producerClient = client()
            producerClient.send(message)
        }

    override suspend fun close() {
        val producerClient =
            clientMutex.withLock {
                val current = client
                client = null
                current
            }
        producerClient?.close()
    }

    private suspend fun client(): KafkaProducerClient =
        clientMutex.withLock {
            client ?: clientFactory.create(connectionConfig).also { createdClient ->
                client = createdClient
            }
        }
}
