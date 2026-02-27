package com.lightkafka.ui

import com.lightkafka.core.kafka.KafkaResult
import com.lightkafka.core.kafka.KafkaServiceError
import com.lightkafka.core.kafka.TopicSummary
import com.lightkafka.core.storage.ClusterProfile
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

class ConnectionManagerLogicTest {
    @Test
    fun `probeConnection rejects blank bootstrap servers`() {
        var called = false

        val message =
            runBlocking {
                probeConnection("   ") {
                    called = true
                    KafkaResult.Success(emptyList<TopicSummary>())
                }
            }

        assertEquals("Connection test failed: bootstrap servers required", message)
        assertEquals(false, called)
    }

    @Test
    fun `probeConnection returns success message with topic count`() {
        val message =
            runBlocking {
                probeConnection("localhost:9092") {
                    KafkaResult.Success(emptyList<TopicSummary>())
                }
            }

        assertEquals("Connection test passed: broker reachable", message)
    }

    @Test
    fun `probeConnection returns failure reason from kafka error`() {
        val message =
            runBlocking {
                probeConnection("localhost:9092") {
                    KafkaResult.Failure(
                        KafkaServiceError.OperationFailed(
                            operation = "list topics",
                            reason = "Connection refused",
                        ),
                    )
                }
            }

        assertEquals("Connection test failed: Connection refused", message)
    }

    @Test
    fun `nextSelectedProfileIdAfterDelete picks remaining profile`() {
        val profiles =
            listOf(
                ClusterProfile(id = "local", name = "Local", bootstrapServers = listOf("localhost:9092")),
                ClusterProfile(id = "stage", name = "Stage", bootstrapServers = listOf("stage:9092")),
            )

        val nextId = nextSelectedProfileIdAfterDelete(profiles, "local")

        assertEquals("stage", nextId)
    }

    @Test
    fun `nextSelectedProfileIdAfterDelete returns null when list becomes empty`() {
        val profiles =
            listOf(
                ClusterProfile(id = "local", name = "Local", bootstrapServers = listOf("localhost:9092")),
            )

        val nextId = nextSelectedProfileIdAfterDelete(profiles, "local")

        assertEquals(null, nextId)
    }

    @Test
    fun `formatConnectionError formats timeout errors`() {
        val message =
            formatConnectionError(
                KafkaServiceError.Timeout(
                    operation = "list topics",
                    timeout = Duration.ofSeconds(5),
                ),
            )

        assertEquals("Operation timed out after 5s while list topics", message)
    }
}
