package com.lightkafka.ui

import com.lightkafka.core.kafka.ConsumedMessage
import com.lightkafka.core.storage.ClusterProfile
import com.lightkafka.core.storage.ProducerTemplate
import com.lightkafka.core.storage.SendHistoryEntry
import com.lightkafka.core.storage.SendStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MainUiStateReducerTest {
    @Test
    fun `filters visible messages by topic query and partition`() {
        val state =
            sampleMainUiState().copy(
                selectedTopic = "orders",
                messages =
                    listOf(
                        message(topic = "orders", partition = 0, offset = 10, key = "order-1", value = "created"),
                        message(topic = "orders", partition = 1, offset = 11, key = "order-2", value = "updated"),
                        message(topic = "orders", partition = 1, offset = 12, key = "order-3", value = "created"),
                        message(topic = "payments", partition = 1, offset = 13, key = "payment-1", value = "captured"),
                    ),
            )

        val withGlobalQuery = reduceMainUiState(state, MainUiAction.SetGlobalSearch("created"))
        val withPartition = reduceMainUiState(withGlobalQuery, MainUiAction.SetPartitionFilter("1"))

        val offsets = withPartition.filteredMessages().map(ConsumedMessage::offset)

        assertEquals(listOf(12L), offsets)
    }

    @Test
    fun `toggle pause flips consumer state`() {
        val state = sampleMainUiState().copy(isConsumerPaused = false)

        val paused = reduceMainUiState(state, MainUiAction.TogglePause)
        val resumed = reduceMainUiState(paused, MainUiAction.TogglePause)

        assertTrue(paused.isConsumerPaused)
        assertFalse(resumed.isConsumerPaused)
    }

    @Test
    fun `apply template copies payload into producer draft`() {
        val template =
            ProducerTemplate(
                id = "tpl-1",
                name = "Order Created",
                topic = "orders",
                partition = 2,
                key = "order-9",
                value = "{\"event\":\"created\"}",
                headers = mapOf("source" to "test"),
            )
        val state =
            sampleMainUiState().copy(
                templates = listOf(template),
                producerDraft = ProducerDraft(topic = "", partitionText = "", key = "", value = "", headersText = ""),
            )

        val updated = reduceMainUiState(state, MainUiAction.ApplyTemplate(template.id))

        assertEquals("orders", updated.producerDraft.topic)
        assertEquals("2", updated.producerDraft.partitionText)
        assertEquals("order-9", updated.producerDraft.key)
        assertEquals("{\"event\":\"created\"}", updated.producerDraft.value)
        assertTrue(updated.producerDraft.headersText.contains("source=test"))
    }

    @Test
    fun `add history entry prepends and trims list`() {
        val existing =
            (1..MAX_HISTORY_ENTRIES).map { index ->
                SendHistoryEntry(
                    id = "old-$index",
                    profileId = "local",
                    topic = "orders",
                    status = SendStatus.SUCCESS,
                    timestampEpochMillis = index.toLong(),
                )
            }
        val state = sampleMainUiState().copy(history = existing)
        val newEntry =
            SendHistoryEntry(
                id = "new-1",
                profileId = "local",
                topic = "orders",
                status = SendStatus.FAILURE,
                timestampEpochMillis = 999,
                errorMessage = "boom",
            )

        val updated = reduceMainUiState(state, MainUiAction.AddHistoryEntry(newEntry))

        assertEquals(MAX_HISTORY_ENTRIES, updated.history.size)
        assertEquals("new-1", updated.history.first().id)
        assertFalse(updated.history.any { it.id == "old-${MAX_HISTORY_ENTRIES}" })
    }

    @Test
    fun `upsert and delete profile keeps active selection valid`() {
        val local = profile(id = "local", name = "Local")
        val stage = profile(id = "stage", name = "Stage")
        val state = sampleMainUiState().copy(profiles = listOf(local, stage), activeProfileId = local.id)

        val renamed = reduceMainUiState(state, MainUiAction.UpsertProfile(local.copy(name = "Local Dev")))
        val withoutActive = reduceMainUiState(renamed, MainUiAction.DeleteProfile(local.id))

        assertEquals("Local Dev", renamed.profiles.first { it.id == local.id }.name)
        assertEquals("stage", withoutActive.activeProfileId)
    }

    @Test
    fun `set diagnostics and export status updates stage E ui state`() {
        val state = sampleMainUiState()

        val diagnosticsOpen = reduceMainUiState(state, MainUiAction.SetDiagnosticsOpen(true))
        val withStatus = reduceMainUiState(diagnosticsOpen, MainUiAction.SetExportStatus("Exported 42 messages"))

        assertTrue(diagnosticsOpen.isDiagnosticsOpen)
        assertEquals("Exported 42 messages", withStatus.exportStatus)
    }

    private fun profile(
        id: String,
        name: String,
    ): ClusterProfile =
        ClusterProfile(
            id = id,
            name = name,
            bootstrapServers = listOf("localhost:9092"),
        )

    private fun message(
        topic: String,
        partition: Int,
        offset: Long,
        key: String,
        value: String,
    ): ConsumedMessage =
        ConsumedMessage(
            topic = topic,
            partition = partition,
            offset = offset,
            timestamp = 1_700_000_000_000,
            key = key.encodeToByteArray(),
            value = value.encodeToByteArray(),
        )
}
