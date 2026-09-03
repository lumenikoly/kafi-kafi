package com.lightkafka.core.kafka

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class DefaultKafkaConsumerGroupServiceTest {
    @Test
    fun `timestamp reset falls back to end when no later record exists`() {
        assertEquals(73L, resolveTimestampResetOffset(timestampOffset = -1, endOffset = 73))
    }

    @Test
    fun `delegates consumer group operations`() =
        runTest {
            val summary = ConsumerGroupSummary("orders-reader", "STABLE", 2, 1)
            val detail = ConsumerGroupDetail("orders-reader", "STABLE", emptyList(), emptyList())
            val client = FakeConsumerGroupClient(listOf(summary), detail)
            val service = service(client)

            assertEquals(KafkaResult.Success(listOf(summary)), service.listGroups())
            assertEquals(KafkaResult.Success(detail), service.describeGroup("orders-reader"))
            assertEquals(
                KafkaResult.Success(Unit),
                service.resetOffsets("orders-reader", "orders", OffsetResetSpec.Earliest),
            )
            assertEquals(KafkaResult.Success(Unit), service.deleteGroup("orders-reader"))
            assertEquals("orders-reader" to "orders", client.resetRequest)
            assertEquals("orders-reader", client.deletedGroupId)
        }

    @Test
    fun `rejects invalid reset before creating client`() =
        runTest {
            var clientCreated = false
            val service =
                DefaultKafkaConsumerGroupService(
                    connectionConfig = testConnectionConfig(),
                    clientFactory = {
                        clientCreated = true
                        FakeConsumerGroupClient()
                    },
                )

            val result = service.resetOffsets("group", "", OffsetResetSpec.Latest)

            assertTrue(result is KafkaResult.Failure)
            assertEquals(false, clientCreated)
        }

    private fun service(client: KafkaConsumerGroupClient) =
        DefaultKafkaConsumerGroupService(
            connectionConfig = testConnectionConfig(),
            clientFactory = { client },
            operationTimeout = Duration.ofSeconds(1),
        )

    private fun testConnectionConfig() = KafkaConnectionConfig(bootstrapServers = listOf("localhost:9092"))

    private class FakeConsumerGroupClient(
        private val groups: List<ConsumerGroupSummary> = emptyList(),
        private val detail: ConsumerGroupDetail? = null,
    ) : KafkaConsumerGroupClient {
        var resetRequest: Pair<String, String>? = null
        var deletedGroupId: String? = null

        override suspend fun listGroups(): List<ConsumerGroupSummary> = groups

        override suspend fun describeGroup(groupId: String): ConsumerGroupDetail? = detail

        override suspend fun resetOffsets(
            groupId: String,
            topic: String,
            spec: OffsetResetSpec,
        ) {
            resetRequest = groupId to topic
        }

        override suspend fun deleteGroup(groupId: String) {
            deletedGroupId = groupId
        }

        override suspend fun close() = Unit
    }
}
