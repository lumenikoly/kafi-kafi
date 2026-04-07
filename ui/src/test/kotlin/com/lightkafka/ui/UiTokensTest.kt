package com.lightkafka.ui

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UiTokensTest {
    @Test
    fun `partition tints follow reference palette`() {
        assertEquals(Color(0xFF4C1D95), partitionTint(1))
        assertEquals(Color(0xFF6D28D9), partitionTint(2))
        assertEquals(Color(0xFF8B5CF6), partitionTint(4))
        assertEquals(Color(0xFFA855F7), partitionTint(5))
        assertEquals(Color(0xFF312E81), partitionTint(8))
    }
}
