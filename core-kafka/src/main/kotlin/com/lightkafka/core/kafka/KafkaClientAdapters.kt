package com.lightkafka.core.kafka

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AlterConfigOp
import org.apache.kafka.clients.admin.Config
import org.apache.kafka.clients.admin.ListTopicsOptions
import org.apache.kafka.clients.admin.NewPartitions
import org.apache.kafka.clients.admin.NewTopic
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

val defaultKafkaConsumerGroupClientFactory =
    KafkaConsumerGroupClientFactory { config ->
        DefaultKafkaConsumerGroupClient(admin = Admin.create(config.toProperties()))
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

    override suspend fun describeCluster(): ClusterDescription? =
        withContext(Dispatchers.IO) {
            try {
                val clusterDescription = admin.describeCluster()
                val clusterId = clusterDescription.clusterId().get()
                val nodes = clusterDescription.nodes().get()
                val controller = clusterDescription.controller().get()

                ClusterDescription(
                    clusterId = clusterId,
                    brokers =
                        nodes.map { node ->
                            BrokerInfo(
                                id = node.id().toString(),
                                host = node.host(),
                                port = node.port(),
                                rack = node.rack(),
                            )
                        },
                    controllerId = controller?.id()?.toString(),
                )
            } catch (e: Exception) {
                null
            }
        }

    override suspend fun createTopic(request: CreateTopicRequest) {
        withContext(Dispatchers.IO) {
            val newTopic =
                NewTopic(
                    request.name,
                    request.partitions,
                    request.replicationFactor,
                ).configs(request.configs)
            admin.createTopics(listOf(newTopic)).all().get()
        }
    }

    override suspend fun deleteTopic(topicName: String) {
        withContext(Dispatchers.IO) {
            admin.deleteTopics(listOf(topicName)).all().get()
        }
    }

    override suspend fun getTopicConfig(topicName: String): TopicConfig =
        withContext(Dispatchers.IO) {
            val configResource = ConfigResource(ConfigResource.Type.TOPIC, topicName)
            val config = admin.describeConfigs(listOf(configResource)).all().get()[configResource]

            val entries =
                config?.entries()?.map { entry ->
                    TopicConfigEntry(
                        name = entry.name(),
                        value = entry.value(),
                        isDefault = entry.isDefault,
                        isReadOnly = entry.isReadOnly,
                        isSensitive = entry.isSensitive,
                    )
                } ?: emptyList()

            TopicConfig(topicName = topicName, entries = entries)
        }

    override suspend fun updateTopicConfig(
        topicName: String,
        configs: Map<String, String>,
    ) {
        withContext(Dispatchers.IO) {
            val configResource = ConfigResource(ConfigResource.Type.TOPIC, topicName)
            val alterOps =
                configs.map { (key, value) ->
                    AlterConfigOp(
                        org.apache.kafka.clients.admin.ConfigEntry(key, value),
                        AlterConfigOp.OpType.SET,
                    )
                }
            admin.incrementalAlterConfigs(mapOf(configResource to alterOps)).all().get()
        }
    }

    override suspend fun addPartitions(
        topicName: String,
        newPartitionCount: Int,
    ) {
        withContext(Dispatchers.IO) {
            admin.createPartitions(mapOf(topicName to NewPartitions.increaseTo(newPartitionCount))).all().get()
        }
    }

    override suspend fun getPartitionDetails(topicName: String): List<PartitionDetail> =
        withContext(Dispatchers.IO) {
            val topicDescription =
                admin.describeTopics(listOf(topicName)).allTopicNames().get()[topicName]
                    ?: return@withContext emptyList()

            val partitions = topicDescription.partitions()
            val topicPartitions = partitions.map { TopicPartition(topicName, it.partition()) }.toSet()

            // Use admin client's listOffsets for beginning and end offsets
            val beginningOffsets =
                try {
                    admin.listOffsets(
                        topicPartitions.associateWith {
                            org.apache.kafka.clients.admin.OffsetSpec.earliest()
                        },
                    ).all().get().mapValues { it.value.offset() }
                } catch (e: Exception) {
                    emptyMap()
                }

            val endOffsets =
                try {
                    admin.listOffsets(
                        topicPartitions.associateWith {
                            org.apache.kafka.clients.admin.OffsetSpec.latest()
                        },
                    ).all().get().mapValues { it.value.offset() }
                } catch (e: Exception) {
                    emptyMap()
                }

            partitions.map { partition ->
                val tp = TopicPartition(topicName, partition.partition())
                PartitionDetail(
                    partition = partition.partition(),
                    leader = partition.leader()?.id(),
                    replicas = partition.replicas().map { it.id() },
                    inSyncReplicas = partition.isr().map { it.id() },
                    beginningOffset = beginningOffsets[tp],
                    endOffset = endOffsets[tp],
                )
            }
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
            consumer
                .partitionsFor(topic)
                .map { partitionInfo ->
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

private class DefaultKafkaConsumerGroupClient(
    private val admin: Admin,
) : KafkaConsumerGroupClient {
    override suspend fun listGroups(): List<ConsumerGroupSummary> =
        withContext(Dispatchers.IO) {
            val listings = admin.listConsumerGroups().all().get()
            listings.map { listing ->
                ConsumerGroupSummary(
                    groupId = listing.groupId(),
                    state = null, // State is not available in ConsumerGroupListing
                    memberCount = 0,
                    topicCount = 0,
                )
            }.sortedBy { it.groupId }
        }

    override suspend fun describeGroup(groupId: String): ConsumerGroupDetail? =
        withContext(Dispatchers.IO) {
            try {
                val descriptions = admin.describeConsumerGroups(listOf(groupId)).all().get()
                val description = descriptions[groupId] ?: return@withContext null

                val members =
                    description.members().map { member ->
                        ConsumerGroupMemberInfo(
                            memberId = member.consumerId(),
                            clientId = member.clientId(),
                            clientHost = member.host(),
                            assignments =
                                member.assignment().topicPartitions().map { tp ->
                                    TopicPartitionInfo(
                                        topic = tp.topic(),
                                        partition = tp.partition(),
                                    )
                                },
                        )
                    }

                // Get offsets for lag calculation
                val offsetsResult = admin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata().get()
                val topicPartitions = offsetsResult.keys.toList()

                val endOffsets =
                    if (topicPartitions.isNotEmpty()) {
                        try {
                            admin.listOffsets(
                                topicPartitions.associateWith {
                                    org.apache.kafka.clients.admin.OffsetSpec.latest()
                                },
                            ).all().get()
                        } catch (e: Exception) {
                            emptyMap()
                        }
                    } else {
                        emptyMap()
                    }

                val partitionLags =
                    offsetsResult.map { (tp, offsetMeta) ->
                        val endOffset = endOffsets[tp]?.offset()
                        val assignedMember = members.find { m ->
                            m.assignments.any { it.topic == tp.topic() && it.partition == tp.partition() }
                        }
                        PartitionLagInfo(
                            topic = tp.topic(),
                            partition = tp.partition(),
                            currentOffset = offsetMeta.offset(),
                            endOffset = endOffset,
                            lag = if (endOffset != null && offsetMeta.offset() >= 0) endOffset - offsetMeta.offset() else null,
                            memberId = assignedMember?.memberId,
                        )
                    }

                ConsumerGroupDetail(
                    groupId = description.groupId(),
                    state = description.state().name,
                    members = members,
                    partitionLags = partitionLags,
                )
            } catch (e: Exception) {
                null
            }
        }

    override suspend fun resetOffsets(
        groupId: String,
        topic: String,
        spec: OffsetResetSpec,
    ) {
        withContext(Dispatchers.IO) {
            // Get partitions for the topic
            val topicDescription = admin.describeTopics(listOf(topic)).allTopicNames().get()[topic]
                ?: error("Topic not found: $topic")

            val partitions = topicDescription.partitions().map { it.partition() }
            val topicPartitions = partitions.map { TopicPartition(topic, it) }

            val offsetsMap =
                when (spec) {
                    is OffsetResetSpec.Earliest -> {
                        val beginningOffsets =
                            admin.listOffsets(
                                topicPartitions.associateWith {
                                    org.apache.kafka.clients.admin.OffsetSpec.earliest()
                                },
                            ).all().get()
                        topicPartitions.associateWith { tp ->
                            org.apache.kafka.clients.consumer.OffsetAndMetadata(beginningOffsets[tp]?.offset() ?: 0L)
                        }
                    }
                    is OffsetResetSpec.Latest -> {
                        val endOffsets =
                            admin.listOffsets(
                                topicPartitions.associateWith {
                                    org.apache.kafka.clients.admin.OffsetSpec.latest()
                                },
                            ).all().get()
                        topicPartitions.associateWith { tp ->
                            org.apache.kafka.clients.consumer.OffsetAndMetadata(endOffsets[tp]?.offset() ?: 0L)
                        }
                    }
                    is OffsetResetSpec.Timestamp -> {
                        val timestampOffsets =
                            admin.listOffsets(
                                topicPartitions.associateWith {
                                    org.apache.kafka.clients.admin.OffsetSpec.forTimestamp(spec.timestampEpochMillis)
                                },
                            ).all().get()
                        val endOffsets =
                            admin.listOffsets(
                                topicPartitions.associateWith {
                                    org.apache.kafka.clients.admin.OffsetSpec.latest()
                                },
                            ).all().get()
                        topicPartitions.associateWith { tp ->
                            val offset = timestampOffsets[tp]?.offset() ?: endOffsets[tp]?.offset() ?: 0L
                            org.apache.kafka.clients.consumer.OffsetAndMetadata(offset)
                        }
                    }
                }

            admin.alterConsumerGroupOffsets(groupId, offsetsMap).all().get()
        }
    }

    override suspend fun deleteGroup(groupId: String) {
        withContext(Dispatchers.IO) {
            admin.deleteConsumerGroups(listOf(groupId)).all().get()
        }
    }

    override suspend fun close() {
        withContext(Dispatchers.IO) {
            admin.close(Duration.ofSeconds(5))
        }
    }
}

private fun KafkaConnectionConfig.toProperties(): Properties =
    Properties().apply {
        put("bootstrap.servers", bootstrapServers.joinToString(","))
        clientId?.let { value -> put("client.id", value) }

        // Security protocol
        securityProtocol?.let { protocol ->
            put("security.protocol", protocol)
        }

        // SASL configuration
        saslMechanism?.let { mechanism ->
            put("sasl.mechanism", mechanism)
        }
        saslUsername?.let { username ->
            put("sasl.jaas.config", buildSaslJaasConfig(username, saslPassword))
        }

        // SSL configuration
        sslTruststorePath?.let { path ->
            put("ssl.truststore.location", path)
        }
        sslTruststorePassword?.let { password ->
            put("ssl.truststore.password", password)
        }
        sslKeystorePath?.let { path ->
            put("ssl.keystore.location", path)
        }
        sslKeystorePassword?.let { password ->
            put("ssl.keystore.password", password)
        }
        sslKeyPassword?.let { password ->
            put("ssl.key.password", password)
        }

        putAll(properties)
    }

private fun buildSaslJaasConfig(
    username: String,
    password: String?,
): String {
    val passwordPart = password?.let { " password=\"$it\";" } ?: ""
    return "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"$username\"$passwordPart;"
}
