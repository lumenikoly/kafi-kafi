package com.lightkafka.ui.topic

import com.lightkafka.core.kafka.ConsumedMessage
import com.lightkafka.core.kafka.ConsumerStartPosition
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TopicMessagesTabTest {
    @Test
    fun `message buffer keeps newest messages within configured limit`() {
        val messages = mutableListOf<ConsumedMessage>()

        repeat(105) { offset -> appendMessage(messages, consumedMessage(offset.toLong()), limit = 100) }

        assertEquals(95, messages.size)
        assertEquals(10L, messages.first().offset)
        assertEquals(104L, messages.last().offset)
    }

    @Test
    fun `specific offset covers every selected partition`() {
        val request =
            buildSessionRequest(
                topicName = "orders",
                state = StartPositionState(StartPositionOption.OFFSET, offsetText = "42"),
                partitionFilter = -1,
                partitionCount = 3,
            )

        assertEquals(setOf(0, 1, 2), request.partitions)
        assertEquals(
            mapOf(0 to 42L, 1 to 42L, 2 to 42L),
            (request.startPosition as ConsumerStartPosition.SpecificOffsets).offsets,
        )
    }

    @Test
    fun `latest position lets Kafka resolve all partitions`() {
        val request = buildSessionRequest("orders", StartPositionState(), -1, 3)

        assertNull(request.partitions)
        assertEquals(ConsumerStartPosition.Latest, request.startPosition)
    }

    @Test
    fun `producer message keeps optional key and partition`() {
        val message =
            buildProducerMessage(
                "orders",
                MessageComposerState(key = "customer-7", value = "{\"paid\":true}", partitionText = "2"),
            )

        assertEquals("orders", message.topic)
        assertArrayEquals("customer-7".encodeToByteArray(), message.key)
        assertArrayEquals("{\"paid\":true}".encodeToByteArray(), message.value)
        assertEquals(2, message.partition)
    }

    private fun consumedMessage(offset: Long) =
        ConsumedMessage(
            topic = "orders",
            partition = 0,
            offset = offset,
            timestamp = 0,
            key = null,
            value = byteArrayOf(),
            headers = emptyMap(),
        )
}
