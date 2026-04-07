package com.lightkafka.core.kafka

fun interface KafkaAdminClientFactory {
    fun create(connectionConfig: KafkaConnectionConfig): KafkaAdminClient
}

interface KafkaAdminClient {
    suspend fun listTopics(includeInternal: Boolean): List<TopicSummary>

    suspend fun describeTopic(topicName: String): TopicDescription

    suspend fun describeCluster(): ClusterDescription?

    suspend fun createTopic(request: CreateTopicRequest)

    suspend fun deleteTopic(topicName: String)

    suspend fun getTopicConfig(topicName: String): TopicConfig

    suspend fun updateTopicConfig(
        topicName: String,
        configs: Map<String, String>,
    )

    suspend fun addPartitions(
        topicName: String,
        newPartitionCount: Int,
    )

    suspend fun getPartitionDetails(topicName: String): List<PartitionDetail>

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

fun interface KafkaConsumerGroupClientFactory {
    fun create(connectionConfig: KafkaConnectionConfig): KafkaConsumerGroupClient
}

interface KafkaConsumerGroupClient {
    suspend fun listGroups(): List<ConsumerGroupSummary>

    suspend fun describeGroup(groupId: String): ConsumerGroupDetail?

    suspend fun resetOffsets(
        groupId: String,
        topic: String,
        spec: OffsetResetSpec,
    )

    suspend fun deleteGroup(groupId: String)

    suspend fun close()
}
