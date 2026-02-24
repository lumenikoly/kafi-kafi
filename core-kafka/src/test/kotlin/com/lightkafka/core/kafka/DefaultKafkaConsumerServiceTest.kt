package com.lightkafka.core.kafka

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.ArrayDeque

class DefaultKafkaConsumerServiceTest {
    @Test
    fun `startSession emits consumed messages`() =
        runTest {
            val fakeClient =
                FakeConsumerClient(
                    availablePartitions = setOf(0, 1),
                    polledBatches =
                        ArrayDeque(
                            listOf(
                                listOf(
                                    ConsumedMessage(
                                        topic = "orders",
                                        partition = 0,
                                        offset = 10,
                                        timestamp = 1700000000000,
                                        key = "k1".encodeToByteArray(),
                                        value = "v1".encodeToByteArray(),
                                        headers = mapOf("h1" to "x".encodeToByteArray()),
                                    ),
                                ),
                            ),
                        ),
                )

            val service =
                DefaultKafkaConsumerService(
                    connectionConfig = testConnectionConfig(),
                    clientFactory = { _, _ -> fakeClient },
                    operationTimeout = Duration.ofSeconds(1),
                )

            val messageEvent =
                service.startSession(
                    ConsumerSessionRequest(
                        topic = "orders",
                        startPosition = ConsumerStartPosition.Earliest,
                        pollTimeout = Duration.ofMillis(25),
                    ),
                ).filterIsInstance<ConsumerEvent.MessageReceived>().first()

            assertEquals(10, messageEvent.message.offset)
            assertEquals("orders", messageEvent.message.topic)
        }

    @Test
    fun `startSession emits poll stats`() =
        runTest {
            val fakeClient =
                FakeConsumerClient(
                    availablePartitions = setOf(0),
                    polledBatches =
                        ArrayDeque(
                            listOf(
                                listOf(
                                    ConsumedMessage(
                                        topic = "orders",
                                        partition = 0,
                                        offset = 1,
                                        timestamp = 1,
                                        key = null,
                                        value = null,
                                    ),
                                    ConsumedMessage(
                                        topic = "orders",
                                        partition = 0,
                                        offset = 2,
                                        timestamp = 2,
                                        key = null,
                                        value = null,
                                    ),
                                ),
                            ),
                        ),
                )

            val service =
                DefaultKafkaConsumerService(
                    connectionConfig = testConnectionConfig(),
                    clientFactory = { _, _ -> fakeClient },
                    operationTimeout = Duration.ofSeconds(1),
                )

            val statsEvent =
                service.startSession(
                    ConsumerSessionRequest(topic = "orders", pollTimeout = Duration.ofMillis(25)),
                ).filterIsInstance<ConsumerEvent.Stats>().first()

            assertEquals(2, statsEvent.polledRecords)
        }

    @Test
    fun `startSession applies specific offsets before polling`() =
        runTest {
            val fakeClient =
                FakeConsumerClient(
                    availablePartitions = setOf(1, 2),
                    polledBatches =
                        ArrayDeque(
                            listOf(
                                listOf(
                                    ConsumedMessage(
                                        topic = "orders",
                                        partition = 1,
                                        offset = 10,
                                        timestamp = 1,
                                        key = null,
                                        value = null,
                                    ),
                                ),
                            ),
                        ),
                )

            val service =
                DefaultKafkaConsumerService(
                    connectionConfig = testConnectionConfig(),
                    clientFactory = { _, _ -> fakeClient },
                    operationTimeout = Duration.ofSeconds(1),
                )

            service.startSession(
                ConsumerSessionRequest(
                    topic = "orders",
                    partitions = setOf(1, 2),
                    startPosition = ConsumerStartPosition.SpecificOffsets(mapOf(1 to 10, 2 to 20)),
                    pollTimeout = Duration.ofMillis(25),
                ),
            ).filterIsInstance<ConsumerEvent.MessageReceived>().first()

            assertEquals(mapOf(1 to 10L, 2 to 20L), fakeClient.seekOffsets)
        }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `pause resume commit and stop control the active session`() =
        runTest {
            val fakeClient =
                FakeConsumerClient(
                    availablePartitions = setOf(0),
                    polledBatches = ArrayDeque(),
                )

            val service =
                DefaultKafkaConsumerService(
                    connectionConfig = testConnectionConfig(),
                    clientFactory = { _, _ -> fakeClient },
                    operationTimeout = Duration.ofSeconds(1),
                )

            val collector =
                backgroundScope.launch {
                    service.startSession(
                        ConsumerSessionRequest(
                            topic = "orders",
                            pollTimeout = Duration.ofMillis(10),
                        ),
                    ).collect {}
                }
            runCurrent()

            service.pause()
            service.resume()
            val commitResult = service.commit()
            service.stop()
            collector.cancelAndJoin()

            assertTrue(fakeClient.pauseCount > 0)
            assertTrue(fakeClient.resumeCount > 0)
            assertEquals(1, fakeClient.commitCount)
            assertEquals(1, fakeClient.closeCount)
            assertEquals(KafkaResult.Success(Unit), commitResult)
        }

    @Test
    fun `commit returns failure when no active session exists`() =
        runTest {
            val service =
                DefaultKafkaConsumerService(
                    connectionConfig = testConnectionConfig(),
                    clientFactory = { _, _ -> error("not used") },
                    operationTimeout = Duration.ofSeconds(1),
                )

            val result = service.commit()

            val failure = assertInstanceOf(KafkaResult.Failure::class.java, result)
            assertInstanceOf(KafkaServiceError.OperationFailed::class.java, failure.error)
        }

    private fun testConnectionConfig(): KafkaConnectionConfig =
        KafkaConnectionConfig(
            bootstrapServers = listOf("localhost:9092"),
            clientId = "consumer-test",
        )

    private class FakeConsumerClient(
        private val availablePartitions: Set<Int>,
        private val polledBatches: ArrayDeque<List<ConsumedMessage>>,
    ) : KafkaConsumerClient {
        var pauseCount: Int = 0
        var resumeCount: Int = 0
        var commitCount: Int = 0
        var closeCount: Int = 0
        var seekOffsets: Map<Int, Long> = emptyMap()
        private var assignedPartitions: Set<Int> = emptySet()

        override suspend fun resolvePartitions(topic: String): Set<Int> = availablePartitions

        override suspend fun assign(
            topic: String,
            partitions: Set<Int>,
        ) {
            assignedPartitions = partitions
        }

        override suspend fun seekToBeginning(partitions: Set<Int>) = Unit

        override suspend fun seekToEnd(partitions: Set<Int>) = Unit

        override suspend fun seekToOffsets(offsets: Map<Int, Long>) {
            seekOffsets = offsets
        }

        override suspend fun seekToTimestamp(
            timestampEpochMillis: Long,
            partitions: Set<Int>,
        ) = Unit

        override suspend fun poll(timeout: Duration): List<ConsumedMessage> =
            if (polledBatches.isEmpty()) {
                emptyList()
            } else {
                polledBatches.removeFirst()
            }

        override suspend fun pause(partitions: Set<Int>) {
            pauseCount += 1
        }

        override suspend fun resume(partitions: Set<Int>) {
            resumeCount += 1
        }

        override suspend fun commit() {
            commitCount += 1
        }

        override suspend fun close() {
            closeCount += 1
        }
    }
}
