package com.lightkafka.core.kafka

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class KafkaServicesIntegrationTest {
    @Test
    fun `consumer group service lists resets and deletes an inactive group`() =
        runBlocking {
            val topicName = uniqueTopicName("groups")
            val groupId = uniqueTopicName("reader")
            createTopic(topicName)
            createConsumerGroupOffset(groupId, topicName, offset = 0)
            val service =
                DefaultKafkaConsumerGroupService(
                    connectionConfig = testConnectionConfig(),
                    operationTimeout = Duration.ofSeconds(20),
                )
            val producer =
                DefaultKafkaProducerService(
                    connectionConfig = testConnectionConfig(),
                    operationTimeout = Duration.ofSeconds(20),
                )

            try {
                val groups = service.listGroups().valueOrFail()
                assertTrue(groups.any { it.groupId == groupId && it.topicCount == 1 })

                producer.send(ProducerMessage(topic = topicName, value = "created".encodeToByteArray())).valueOrFail()
                service.resetOffsets(groupId, topicName, OffsetResetSpec.Latest).valueOrFail()
                val latestLag = checkNotNull(service.describeGroup(groupId).valueOrFail()).partitionLags.single()
                assertEquals(latestLag.endOffset, latestLag.currentOffset)

                service.resetOffsets(groupId, topicName, OffsetResetSpec.Offset(0)).valueOrFail()
                val resetLag = checkNotNull(service.describeGroup(groupId).valueOrFail()).partitionLags.single()
                assertEquals(1L, resetLag.lag)

                service.deleteGroup(groupId).valueOrFail()
                assertTrue(service.listGroups().valueOrFail().none { it.groupId == groupId })
            } finally {
                producer.close()
                service.close()
            }
        }

    @Test
    fun `admin service lists and describes created topics`() =
        runBlocking {
            val topicName = uniqueTopicName("admin")
            createTopic(topicName, partitions = 2)

            val adminService =
                DefaultKafkaAdminService(
                    connectionConfig = testConnectionConfig(),
                    operationTimeout = Duration.ofSeconds(20),
                )

            try {
                val listResult = adminService.listTopics(includeInternal = false)
                val topics =
                    when (listResult) {
                        is KafkaResult.Success -> listResult.value
                        is KafkaResult.Failure -> fail("listTopics failed: ${listResult.error}")
                    }
                val listedTopic = topics.first { topic -> topic.name == topicName }
                assertEquals(2, listedTopic.partitions)

                val describeResult = adminService.describeTopic(topicName)
                val description =
                    when (describeResult) {
                        is KafkaResult.Success -> describeResult.value
                        is KafkaResult.Failure -> fail("describeTopic failed: ${describeResult.error}")
                    }
                assertEquals(topicName, description.name)
                assertEquals(2, description.partitions.size)
            } finally {
                adminService.close()
            }
        }

    @Test
    fun `producer sends and consumer reads from earliest`() =
        runBlocking {
            val topicName = uniqueTopicName("earliest")
            createTopic(topicName)

            val producerService =
                DefaultKafkaProducerService(
                    connectionConfig = testConnectionConfig(),
                    operationTimeout = Duration.ofSeconds(20),
                )
            val consumerService =
                DefaultKafkaConsumerService(
                    connectionConfig = testConnectionConfig(),
                    operationTimeout = Duration.ofSeconds(20),
                )

            try {
                val expectedKey = "order-1".encodeToByteArray()
                val expectedValue = "created".encodeToByteArray()

                val sendResult =
                    producerService.send(
                        ProducerMessage(
                            topic = topicName,
                            key = expectedKey,
                            value = expectedValue,
                            headers = mapOf("source" to "integration-test".encodeToByteArray()),
                        ),
                    )
                assertInstanceOf(KafkaResult.Success::class.java, sendResult)

                val messageEvent =
                    withTimeout(20_000L) {
                        consumerService
                            .startSession(
                                ConsumerSessionRequest(
                                    topic = topicName,
                                    startPosition = ConsumerStartPosition.Earliest,
                                    pollTimeout = Duration.ofMillis(200),
                                ),
                            ).filterIsInstance<ConsumerEvent.MessageReceived>()
                            .first()
                    }

                assertEquals(topicName, messageEvent.message.topic)
                assertArrayEquals(expectedKey, messageEvent.message.key)
                assertArrayEquals(expectedValue, messageEvent.message.value)
                val sourceHeader = messageEvent.message.headers["source"] ?: fail("Missing source header")
                assertArrayEquals("integration-test".encodeToByteArray(), sourceHeader)
            } finally {
                consumerService.close()
                producerService.close()
            }
        }

    @Test
    fun `consumer latest only receives records produced after session start`() =
        runBlocking {
            val topicName = uniqueTopicName("latest")
            createTopic(topicName)

            val producerService =
                DefaultKafkaProducerService(
                    connectionConfig = testConnectionConfig(),
                    operationTimeout = Duration.ofSeconds(20),
                )
            val consumerService =
                DefaultKafkaConsumerService(
                    connectionConfig = testConnectionConfig(),
                    operationTimeout = Duration.ofSeconds(20),
                )

            try {
                val beforeSendResult =
                    producerService.send(
                        ProducerMessage(topic = topicName, value = "before".encodeToByteArray()),
                    )
                assertInstanceOf(KafkaResult.Success::class.java, beforeSendResult)

                val events = Channel<ConsumerEvent>(capacity = Channel.UNLIMITED)
                val collector =
                    launch {
                        consumerService
                            .startSession(
                                ConsumerSessionRequest(
                                    topic = topicName,
                                    startPosition = ConsumerStartPosition.Latest,
                                    pollTimeout = Duration.ofMillis(200),
                                ),
                            ).collect { event ->
                                events.send(event)
                            }
                    }

                awaitEvent<ConsumerEvent.Stats>(events)

                val afterSendResult =
                    producerService.send(
                        ProducerMessage(topic = topicName, value = "after".encodeToByteArray()),
                    )
                assertInstanceOf(KafkaResult.Success::class.java, afterSendResult)

                val messageEvent = awaitEvent<ConsumerEvent.MessageReceived>(events)
                assertEquals("after", messageEvent.message.value?.decodeToString())

                collector.cancelAndJoin()
                events.close()
            } finally {
                consumerService.close()
                producerService.close()
            }
            Unit
        }

    private fun testConnectionConfig(): KafkaConnectionConfig =
        KafkaConnectionConfig(
            bootstrapServers = listOf(kafkaContainer.bootstrapServers),
            clientId = "integration-tests",
        )

    private fun uniqueTopicName(prefix: String): String = "$prefix-${UUID.randomUUID()}"

    private fun createTopic(
        topicName: String,
        partitions: Int = 1,
    ) {
        val properties =
            Properties().apply {
                put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.bootstrapServers)
            }

        Admin.create(properties).use { adminClient ->
            adminClient
                .createTopics(
                    listOf(NewTopic(topicName, partitions, 1.toShort())),
                ).all()
                .get(30, TimeUnit.SECONDS)
        }
    }

    private fun createConsumerGroupOffset(
        groupId: String,
        topicName: String,
        offset: Long,
    ) {
        val properties =
            Properties().apply {
                put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.bootstrapServers)
            }
        Admin.create(properties).use { adminClient ->
            adminClient
                .alterConsumerGroupOffsets(
                    groupId,
                    mapOf(TopicPartition(topicName, 0) to OffsetAndMetadata(offset)),
                ).all()
                .get(30, TimeUnit.SECONDS)
        }
    }

    private fun <T> KafkaResult<T>.valueOrFail(): T =
        when (this) {
            is KafkaResult.Success -> value
            is KafkaResult.Failure -> fail("Kafka operation failed: $error")
        }

    private suspend inline fun <reified T : ConsumerEvent> awaitEvent(events: Channel<ConsumerEvent>): T =
        withTimeout(20_000L) {
            while (true) {
                val event = events.receive()
                if (event is T) {
                    return@withTimeout event
                }
            }
            error("Unreachable")
        }

    private companion object {
        @Container
        @JvmStatic
        val kafkaContainer: KafkaContainer =
            KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.1"))
    }
}
