package com.lightkafka.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MainUiInitialStateTest {
    @Test
    fun `initial state does not contain seeded demo data`() {
        val state = initialMainUiState()

        assertEquals(emptyList<String>(), state.topics)
        assertEquals(emptyList<String>(), state.messages.map { it.topic })
        assertEquals(emptyList<String>(), state.profiles.map { it.name })
        assertNull(state.selectedTopic)
        assertEquals("", state.producerDraft.topic)
    }
}
