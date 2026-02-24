package com.lightkafka.ui

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UiTokensTest {
    @Test
    fun `partition tints follow reference palette`() {
        assertEquals(Color(0xFFF3E8FF), partitionTint(1))
        assertEquals(Color(0xFFE0E7FF), partitionTint(2))
        assertEquals(Color(0xFFFCE7F3), partitionTint(4))
        assertEquals(Color(0xFFFEF3C7), partitionTint(5))
        assertEquals(Color(0xFFF3F4F6), partitionTint(8))
    }
}
