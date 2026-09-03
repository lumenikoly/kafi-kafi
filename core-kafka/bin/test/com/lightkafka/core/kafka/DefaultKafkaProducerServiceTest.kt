package com.lightkafka.core.kafka

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.time.Duration

class DefaultKafkaProducerServiceTest {
    @Test
    fun `send returns metadata from producer client`() =
        runTest {
            val fakeClient =
                FakeProducerClient(
                    sendResult =
                        ProducerSendResult(
                            topic = "orders",
                            partition = 2,
                            offset = 42,
                            timestamp = 1700000000000,
                        ),
                )

            val service =
                DefaultKafkaProducerService(
                    connectionConfig = testConnectionConfig(),
                    clientFactory = { fakeClient },
                    operationTimeout = Duration.ofSeconds(1),
                )

            val result =
                service.send(
                    ProducerMessage(
                        topic = "orders",
                        key = "k1".encodeToByteArray(),
                        value = "v1".encodeToByteArray(),
                        headers = mapOf("source" to "test".encodeToByteArray()),
                    ),
                )

            assertEquals(KafkaResult.Success(fakeClient.sendResult), result)
        }

    @Test
    fun `send returns operation error when client throws`() =
        runTest {
            val fakeClient =
                FakeProducerClient(
                    sendResult = ProducerSendResult("orders", 0, 1, 1),
                    throwOnSend = IllegalStateException("broker unavailable"),
                )

            val service =
                DefaultKafkaProducerService(
                    connectionConfig = testConnectionConfig(),
                    clientFactory = { fakeClient },
                    operationTimeout = Duration.ofSeconds(1),
                )

            val result = service.send(ProducerMessage(topic = "orders", value = "v".encodeToByteArray()))

            val failure = assertInstanceOf(KafkaResult.Failure::class.java, result)
            assertInstanceOf(KafkaServiceError.OperationFailed::class.java, failure.error)
        }

    @Test
    fun `send returns timeout error when client is too slow`() =
        runTest {
            val fakeClient =
                FakeProducerClient(
                    sendResult = ProducerSendResult("orders", 0, 1, 1),
                    delayBeforeResponse = Duration.ofMillis(200),
                )

            val service =
                DefaultKafkaProducerService(
                    connectionConfig = testConnectionConfig(),
                    clientFactory = { fakeClient },
                    operationTimeout = Duration.ofMillis(50),
                )

            val result = service.send(ProducerMessage(topic = "orders", value = "v".encodeToByteArray()))

            val failure = assertInstanceOf(KafkaResult.Failure::class.java, result)
            assertInstanceOf(KafkaServiceError.Timeout::class.java, failure.error)
        }

    private fun testConnectionConfig(): KafkaConnectionConfig =
        KafkaConnectionConfig(
            bootstrapServers = listOf("localhost:9092"),
            clientId = "producer-test",
        )

    private class FakeProducerClient(
        val sendResult: ProducerSendResult,
        private val throwOnSend: Throwable? = null,
        private val delayBeforeResponse: Duration = Duration.ZERO,
    ) : KafkaProducerClient {
        override suspend fun send(message: ProducerMessage): ProducerSendResult {
            throwOnSend?.let { throw it }
            if (!delayBeforeResponse.isZero) {
                delay(delayBeforeResponse.toMillis())
            }
            return sendResult
        }

        override suspend fun close() = Unit
    }
}
