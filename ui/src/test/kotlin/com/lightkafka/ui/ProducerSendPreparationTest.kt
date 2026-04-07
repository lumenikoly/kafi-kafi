package com.lightkafka.ui

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class ProducerSendPreparationTest {
    @Test
    fun `uses selected topic when draft topic is blank`() {
        val result =
            prepareProducerSend(
                draft = ProducerDraft(topic = "", partitionText = "", value = "hello", headersText = ""),
                selectedTopic = "orders",
            )

        val ready = assertInstanceOf(ProducerSendPreparation.Ready::class.java, result)
        assertEquals("orders", ready.topic)
        assertEquals(null, ready.partition)
        assertEquals("orders", ready.producerMessage.topic)
        assertArrayEquals("hello".encodeToByteArray(), ready.producerMessage.value)
    }

    @Test
    fun `rejects non numeric partition text`() {
        val result =
            prepareProducerSend(
                draft = ProducerDraft(topic = "orders", partitionText = "abc", value = "hello", headersText = ""),
                selectedTopic = null,
            )

        val error = assertInstanceOf(ProducerSendPreparation.ValidationError::class.java, result)
        assertEquals("orders", error.topic)
        assertEquals(null, error.partition)
        assertEquals("Partition must be a non-negative integer", error.errorMessage)
    }

    @Test
    fun `rejects negative partition text`() {
        val result =
            prepareProducerSend(
                draft = ProducerDraft(topic = "orders", partitionText = "-1", value = "hello", headersText = ""),
                selectedTopic = null,
            )

        val error = assertInstanceOf(ProducerSendPreparation.ValidationError::class.java, result)
        assertEquals("orders", error.topic)
        assertEquals(-1, error.partition)
        assertEquals("Partition must be a non-negative integer", error.errorMessage)
    }

    @Test
    fun `builds producer message with parsed headers`() {
        val result =
            prepareProducerSend(
                draft =
                    ProducerDraft(
                        topic = "orders",
                        partitionText = "2",
                        key = "order-1",
                        value = "created",
                        headersText = "source=ui\ntrace-id=123",
                    ),
                selectedTopic = null,
            )

        val ready = assertInstanceOf(ProducerSendPreparation.Ready::class.java, result)
        assertEquals("orders", ready.topic)
        assertEquals(2, ready.partition)
        assertEquals("orders", ready.producerMessage.topic)
        assertEquals(2, ready.producerMessage.partition)
        assertArrayEquals("order-1".encodeToByteArray(), ready.producerMessage.key)
        assertArrayEquals("created".encodeToByteArray(), ready.producerMessage.value)
        assertArrayEquals("ui".encodeToByteArray(), ready.producerMessage.headers["source"])
        assertArrayEquals("123".encodeToByteArray(), ready.producerMessage.headers["trace-id"])
    }
}
