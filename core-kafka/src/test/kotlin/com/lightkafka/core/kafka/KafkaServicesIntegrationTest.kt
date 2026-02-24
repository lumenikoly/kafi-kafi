package com.lightkafka.core.kafka

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
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
    fun `admin service lists and describes created topics`() =
        runTest {
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
        runTest {
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
                        consumerService.startSession(
                            ConsumerSessionRequest(
                                topic = topicName,
                                startPosition = ConsumerStartPosition.Earliest,
                                pollTimeout = Duration.ofMillis(200),
                            ),
                        ).filterIsInstance<ConsumerEvent.MessageReceived>().first()
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
        runTest {
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
                    backgroundScope.launch {
                        consumerService.startSession(
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
            adminClient.createTopics(
                listOf(NewTopic(topicName, partitions, 1.toShort())),
            ).all().get(30, TimeUnit.SECONDS)
        }
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
