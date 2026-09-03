package com.lightkafka.ui.topic

import com.lightkafka.core.kafka.ConsumerStartPosition
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TopicMessagesTabTest {
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
}
