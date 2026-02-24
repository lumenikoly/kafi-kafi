package com.lightkafka.core.kafka

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.ListTopicsOptions
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.config.ConfigResource
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import java.time.Duration
import java.util.Properties
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

val defaultKafkaAdminClientFactory =
    KafkaAdminClientFactory { config ->
        DefaultKafkaAdminClient(admin = Admin.create(config.toProperties()))
    }

val defaultKafkaProducerClientFactory =
    KafkaProducerClientFactory { config ->
        val producerProperties =
            config.toProperties().apply {
                put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer::class.java.name)
                put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer::class.java.name)
            }

        DefaultKafkaProducerClient(producer = KafkaProducer(producerProperties))
    }

val defaultKafkaConsumerClientFactory =
    KafkaConsumerClientFactory { config, request ->
        val consumerProperties =
            config.toProperties().apply {
                put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer::class.java.name)
                put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer::class.java.name)
                put(ConsumerConfig.GROUP_ID_CONFIG, request.groupId ?: "light-kafka-viewer-${UUID.randomUUID()}")
                put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, request.autoCommit.toString())
                put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, request.maxPollRecords.toString())
                put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "none")
                putAll(request.properties)
            }

        DefaultKafkaConsumerClient(consumer = KafkaConsumer(consumerProperties))
    }

private class DefaultKafkaAdminClient(
    private val admin: Admin,
) : KafkaAdminClient {
    override suspend fun listTopics(includeInternal: Boolean): List<TopicSummary> =
        withContext(Dispatchers.IO) {
            val options = ListTopicsOptions().listInternal(includeInternal)
            val listings = admin.listTopics(options).namesToListings().get()
            val names = listings.keys.toList()
            val descriptions =
                if (names.isEmpty()) {
                    emptyMap()
                } else {
                    admin.describeTopics(names).allTopicNames().get()
                }

            names.sorted().map { topicName ->
                TopicSummary(
                    name = topicName,
                    partitions = descriptions[topicName]?.partitions()?.size ?: 0,
                    internal = listings[topicName]?.isInternal ?: false,
                )
            }
        }

    override suspend fun describeTopic(topicName: String): TopicDescription =
        withContext(Dispatchers.IO) {
            val topicDescription =
                admin.describeTopics(listOf(topicName)).allTopicNames().get()[topicName]
                    ?: error("Topic not found: $topicName")

            val configResource = ConfigResource(ConfigResource.Type.TOPIC, topicName)
            val config = admin.describeConfigs(listOf(configResource)).all().get()[configResource]

            val configs =
                config?.entries()?.associate { entry ->
                    entry.name() to entry.value()
                } ?: emptyMap()

            val partitions =
                topicDescription.partitions().map { partition ->
                    TopicPartitionDescription(
                        partition = partition.partition(),
                        leader = partition.leader()?.id()?.toString(),
                        replicas = partition.replicas().map { replica -> replica.id().toString() },
                        inSyncReplicas = partition.isr().map { replica -> replica.id().toString() },
                    )
                }

            TopicDescription(
                name = topicDescription.name(),
                internal = topicDescription.isInternal,
                partitions = partitions,
                configs = configs,
            )
        }

    override suspend fun close() {
        withContext(Dispatchers.IO) {
            admin.close(Duration.ofSeconds(5))
        }
    }
}

private class DefaultKafkaProducerClient(
    private val producer: Producer<ByteArray, ByteArray>,
) : KafkaProducerClient {
    override suspend fun send(message: ProducerMessage): ProducerSendResult =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                val headers =
                    message.headers.entries.map { (key, value) ->
                        RecordHeader(key, value)
                    }

                val record =
                    ProducerRecord(
                        message.topic,
                        message.partition,
                        message.timestamp,
                        message.key,
                        message.value,
                        headers,
                    )

                producer.send(record) { metadata, exception ->
                    when {
                        exception != null -> {
                            if (continuation.isActive) {
                                continuation.resumeWithException(exception)
                            }
                        }

                        metadata != null -> {
                            if (continuation.isActive) {
                                continuation.resume(
                                    ProducerSendResult(
                                        topic = metadata.topic(),
                                        partition = metadata.partition(),
                                        offset = metadata.offset(),
                                        timestamp = metadata.timestamp(),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }

    override suspend fun close() {
        withContext(Dispatchers.IO) {
            producer.flush()
            producer.close(Duration.ofSeconds(5))
        }
    }
}

private class DefaultKafkaConsumerClient(
    private val consumer: Consumer<ByteArray, ByteArray>,
) : KafkaConsumerClient {
    private var assignedTopic: String? = null

    override suspend fun resolvePartitions(topic: String): Set<Int> =
        withContext(Dispatchers.IO) {
            consumer.partitionsFor(topic).map { partitionInfo ->
                partitionInfo.partition()
            }.toSet()
        }

    override suspend fun assign(
        topic: String,
        partitions: Set<Int>,
    ) {
        withContext(Dispatchers.IO) {
            assignedTopic = topic
            consumer.assign(partitions.map { partition -> TopicPartition(topic, partition) })
        }
    }

    override suspend fun seekToBeginning(partitions: Set<Int>) {
        withContext(Dispatchers.IO) {
            consumer.seekToBeginning(toTopicPartitions(partitions))
        }
    }

    override suspend fun seekToEnd(partitions: Set<Int>) {
        withContext(Dispatchers.IO) {
            consumer.seekToEnd(toTopicPartitions(partitions))
        }
    }

    override suspend fun seekToOffsets(offsets: Map<Int, Long>) {
        withContext(Dispatchers.IO) {
            offsets.forEach { (partition, offset) ->
                consumer.seek(toTopicPartition(partition), offset)
            }
        }
    }

    override suspend fun seekToTimestamp(
        timestampEpochMillis: Long,
        partitions: Set<Int>,
    ) {
        withContext(Dispatchers.IO) {
            val topicPartitions = toTopicPartitions(partitions)
            val lookup = topicPartitions.associateWith { timestampEpochMillis }
            val endOffsets = consumer.endOffsets(topicPartitions)

            consumer.offsetsForTimes(lookup).forEach { (topicPartition, offsetForTime) ->
                if (offsetForTime != null) {
                    consumer.seek(topicPartition, offsetForTime.offset())
                } else {
                    consumer.seek(topicPartition, endOffsets[topicPartition] ?: 0L)
                }
            }
        }
    }

    override suspend fun poll(timeout: Duration): List<ConsumedMessage> =
        withContext(Dispatchers.IO) {
            consumer.poll(timeout).map { record ->
                ConsumedMessage(
                    topic = record.topic(),
                    partition = record.partition(),
                    offset = record.offset(),
                    timestamp = record.timestamp(),
                    key = record.key(),
                    value = record.value(),
                    headers =
                        record.headers().associate { header ->
                            header.key() to header.value()
                        },
                )
            }
        }

    override suspend fun pause(partitions: Set<Int>) {
        withContext(Dispatchers.IO) {
            consumer.pause(toTopicPartitions(partitions))
        }
    }

    override suspend fun resume(partitions: Set<Int>) {
        withContext(Dispatchers.IO) {
            consumer.resume(toTopicPartitions(partitions))
        }
    }

    override suspend fun commit() {
        withContext(Dispatchers.IO) {
            consumer.commitSync()
        }
    }

    override suspend fun close() {
        withContext(Dispatchers.IO) {
            consumer.close(Duration.ofSeconds(5))
        }
    }

    private fun toTopicPartitions(partitions: Set<Int>): Set<TopicPartition> =
        partitions.mapTo(linkedSetOf()) { partition ->
            toTopicPartition(partition)
        }

    private fun toTopicPartition(partition: Int): TopicPartition {
        val topic = assignedTopic ?: error("Consumer partitions are not assigned")
        return TopicPartition(topic, partition)
    }
}

private fun KafkaConnectionConfig.toProperties(): Properties =
    Properties().apply {
        put("bootstrap.servers", bootstrapServers.joinToString(","))
        clientId?.let { value -> put("client.id", value) }
        putAll(properties)
    }
