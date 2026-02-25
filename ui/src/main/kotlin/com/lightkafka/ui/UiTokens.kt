package com.lightkafka.ui

import androidx.compose.ui.graphics.Color

val AppBackgroundColor = Color(0xFFF9FAFB)
val HeaderBackgroundColor = Color.White
val AccentColor = Color(0xFF6D28D9)
val AccentTextStrong = Color(0xFF4C1D95)
val AccentMuted = Color(0xFF8B5CF6)
val InspectorBackgroundColor = Color(0xFFF3F4F6)

fun partitionTint(partition: Int): Color =
    when (partition) {
        1 -> Color(0xFFF3E8FF)
        2 -> Color(0xFFE0E7FF)
        4 -> Color(0xFFFCE7F3)
        5 -> Color(0xFFFEF3C7)
        else -> Color(0xFFF3F4F6)
    }
