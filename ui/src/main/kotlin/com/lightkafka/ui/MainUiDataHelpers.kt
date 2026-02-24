package com.lightkafka.ui

import com.lightkafka.core.kafka.ConsumedMessage
import com.lightkafka.core.storage.ClusterProfile
import com.lightkafka.core.storage.ProducerTemplate
import com.lightkafka.core.storage.SendHistoryEntry
import com.lightkafka.core.storage.SendStatus

fun messageId(message: ConsumedMessage): String = "${message.topic}:${message.partition}:${message.offset}"

fun initialMainUiState(): MainUiState =
    MainUiState(
        profiles = emptyList(),
        activeProfileId = null,
        topics = emptyList(),
    )

fun selectedMessageOrNull(state: MainUiState): ConsumedMessage? {
    val selectedMessageId = state.selectedMessageId ?: return null
    return state.messages.firstOrNull { messageId(it) == selectedMessageId }
}

fun previewBytes(
    value: ByteArray?,
    limit: Int = MESSAGE_PREVIEW_LIMIT,
): String {
    if (value == null) {
        return ""
    }
    val decoded = value.toString(Charsets.UTF_8)
    return if (decoded.length <= limit) decoded else decoded.take(limit) + "..."
}

fun sampleMainUiState(): MainUiState {
    val profiles =
        listOf(
            ClusterProfile(
                id = "local",
                name = "Local Kafka",
                bootstrapServers = listOf("localhost:9092"),
            ),
            ClusterProfile(
                id = "stage",
                name = "Staging Cluster",
                bootstrapServers = listOf("stage-broker-1:9092", "stage-broker-2:9092"),
            ),
        )
    val topics = listOf("orders", "payments", "audit.events", "inventory")
    val messages =
        (1..400).map { index ->
            val topic = if (index % 3 == 0) "payments" else "orders"
            val partition = index % 6
            val eventType = if (index % 2 == 0) "updated" else "created"
            val payload = "{\"event\":\"$eventType\",\"id\":$index}"
            ConsumedMessage(
                topic = topic,
                partition = partition,
                offset = index.toLong(),
                timestamp = 1_700_000_000_000 + index * 1_000L,
                key = "key-$index".encodeToByteArray(),
                value = payload.encodeToByteArray(),
                headers = mapOf("source" to "sample".encodeToByteArray()),
            )
        }
    val templates =
        listOf(
            ProducerTemplate(
                id = "tpl-order-created",
                name = "Order Created",
                topic = "orders",
                key = "order-1",
                value = "{\"event\":\"created\"}",
                headers = mapOf("source" to "ui"),
                updatedAtEpochMillis = 1_700_000_000_000,
            ),
            ProducerTemplate(
                id = "tpl-payment-captured",
                name = "Payment Captured",
                topic = "payments",
                key = "payment-1",
                value = "{\"event\":\"captured\"}",
                headers = mapOf("source" to "ui"),
                updatedAtEpochMillis = 1_700_000_010_000,
            ),
        )
    val history =
        listOf(
            SendHistoryEntry(
                id = "send-1",
                profileId = "local",
                topic = "orders",
                partition = 1,
                status = SendStatus.SUCCESS,
                timestampEpochMillis = 1_700_000_020_000,
            ),
            SendHistoryEntry(
                id = "send-2",
                profileId = "local",
                topic = "payments",
                partition = 0,
                status = SendStatus.FAILURE,
                timestampEpochMillis = 1_700_000_021_000,
                errorMessage = "Timeout",
            ),
        )

    return MainUiState(
        profiles = profiles,
        activeProfileId = profiles.first().id,
        topics = topics,
        selectedTopic = "orders",
        messages = messages,
        selectedMessageId = messageId(messages.first()),
        producerDraft = ProducerDraft(topic = "orders"),
        templates = templates,
        history = history,
    )
}
