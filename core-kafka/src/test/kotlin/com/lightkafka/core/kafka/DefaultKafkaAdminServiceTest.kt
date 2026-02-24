package com.lightkafka.core.kafka

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.time.Duration

class DefaultKafkaAdminServiceTest {
    @Test
    fun `listTopics returns topic summaries from client`() =
        runTest {
            val fakeClient =
                FakeAdminClient(
                    topics =
                        listOf(
                            TopicSummary(name = "orders", partitions = 6, internal = false),
                            TopicSummary(name = "__consumer_offsets", partitions = 50, internal = true),
                        ),
                    descriptions = emptyMap(),
                )

            val service =
                DefaultKafkaAdminService(
                    connectionConfig = testConnectionConfig(),
                    clientFactory = { fakeClient },
                    operationTimeout = Duration.ofSeconds(1),
                )

            val result = service.listTopics(includeInternal = false)

            assertEquals(
                KafkaResult.Success(listOf(TopicSummary(name = "orders", partitions = 6, internal = false))),
                result,
            )
        }

    @Test
    fun `describeTopic returns topic details from client`() =
        runTest {
            val details =
                TopicDescription(
                    name = "orders",
                    internal = false,
                    partitions =
                        listOf(
                            TopicPartitionDescription(
                                partition = 0,
                                leader = "1",
                                replicas = listOf("1", "2"),
                                inSyncReplicas = listOf("1", "2"),
                            ),
                        ),
                    configs = mapOf("cleanup.policy" to "delete"),
                )

            val fakeClient =
                FakeAdminClient(
                    topics = emptyList(),
                    descriptions = mapOf("orders" to details),
                )

            val service =
                DefaultKafkaAdminService(
                    connectionConfig = testConnectionConfig(),
                    clientFactory = { fakeClient },
                    operationTimeout = Duration.ofSeconds(1),
                )

            val result = service.describeTopic("orders")

            assertEquals(KafkaResult.Success(details), result)
        }

    @Test
    fun `listTopics returns timeout error when operation exceeds timeout`() =
        runTest {
            val fakeClient =
                FakeAdminClient(
                    topics = listOf(TopicSummary(name = "orders", partitions = 1, internal = false)),
                    descriptions = emptyMap(),
                    delayBeforeResponse = Duration.ofMillis(200),
                )

            val service =
                DefaultKafkaAdminService(
                    connectionConfig = testConnectionConfig(),
                    clientFactory = { fakeClient },
                    operationTimeout = Duration.ofMillis(50),
                )

            val result = service.listTopics(includeInternal = true)

            val failure = assertInstanceOf(KafkaResult.Failure::class.java, result)
            assertInstanceOf(KafkaServiceError.Timeout::class.java, failure.error)
        }

    private fun testConnectionConfig(): KafkaConnectionConfig =
        KafkaConnectionConfig(
            bootstrapServers = listOf("localhost:9092"),
            clientId = "admin-test",
        )

    private class FakeAdminClient(
        private val topics: List<TopicSummary>,
        private val descriptions: Map<String, TopicDescription>,
        private val delayBeforeResponse: Duration = Duration.ZERO,
    ) : KafkaAdminClient {
        override suspend fun listTopics(includeInternal: Boolean): List<TopicSummary> {
            if (!delayBeforeResponse.isZero) {
                delay(delayBeforeResponse.toMillis())
            }
            return if (includeInternal) topics else topics.filterNot(TopicSummary::internal)
        }

        override suspend fun describeTopic(topicName: String): TopicDescription =
            descriptions[topicName] ?: error("Missing description for $topicName")

        override suspend fun close() = Unit
    }
}
