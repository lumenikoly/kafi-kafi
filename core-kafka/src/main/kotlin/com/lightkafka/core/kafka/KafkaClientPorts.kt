package com.lightkafka.core.kafka

fun interface KafkaAdminClientFactory {
    fun create(connectionConfig: KafkaConnectionConfig): KafkaAdminClient
}

interface KafkaAdminClient {
    suspend fun listTopics(includeInternal: Boolean): List<TopicSummary>

    suspend fun describeTopic(topicName: String): TopicDescription

    suspend fun close()
}

fun interface KafkaProducerClientFactory {
    fun create(connectionConfig: KafkaConnectionConfig): KafkaProducerClient
}

interface KafkaProducerClient {
    suspend fun send(message: ProducerMessage): ProducerSendResult

    suspend fun close()
}

fun interface KafkaConsumerClientFactory {
    fun create(
        connectionConfig: KafkaConnectionConfig,
        request: ConsumerSessionRequest,
    ): KafkaConsumerClient
}

interface KafkaConsumerClient {
    suspend fun resolvePartitions(topic: String): Set<Int>

    suspend fun assign(
        topic: String,
        partitions: Set<Int>,
    )

    suspend fun seekToBeginning(partitions: Set<Int>)

    suspend fun seekToEnd(partitions: Set<Int>)

    suspend fun seekToOffsets(offsets: Map<Int, Long>)

    suspend fun seekToTimestamp(
        timestampEpochMillis: Long,
        partitions: Set<Int>,
    )

    suspend fun poll(timeout: java.time.Duration): List<ConsumedMessage>

    suspend fun pause(partitions: Set<Int>)

    suspend fun resume(partitions: Set<Int>)

    suspend fun commit()

    suspend fun close()
}
