package com.lightkafka.ui

import androidx.compose.ui.graphics.Color

// Primary palette - Deep indigo with purple accents
val PrimaryDark = Color(0xFF1E1B4B)
val Primary = Color(0xFF312E81)
val PrimaryLight = Color(0xFF4338CA)
val PrimarySurface = Color(0xFF1a1744)

// Accent colors - Vibrant purple/pink gradient
val AccentViolet = Color(0xFF8B5CF6)
val AccentPink = Color(0xFFEC4899)
val AccentCyan = Color(0xFF06B6D4)
val AccentAmber = Color(0xFFF59E0B)
val AccentEmerald = Color(0xFF10B981)

// Surface colors - Dark mode inspired
val SurfaceDark = Color(0xFF0F0E17)
val SurfaceCard = Color(0xFF1A1826)
val SurfaceElevated = Color(0xFF252233)
val SurfaceHover = Color(0xFF2D2A3E)

// Background
val AppBackgroundColor = Color(0xFF0C0A14)
val HeaderBackgroundColor = Color(0xFF12101C)
val SidebarBackgroundColor = Color(0xFF0F0E17)

// Text colors
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)
val TextAccent = Color(0xFFA78BFA)

// Border colors
val BorderSubtle = Color(0xFF2D2A3E)
val BorderDefault = Color(0xFF3D3A4E)
val BorderAccent = Color(0xFF4C1D95)

// Legacy compat
val AccentColor = AccentViolet
val AccentTextStrong = Color(0xFFC4B5FD)
val AccentMuted = Color(0xFF7C3AED)
val InspectorBackgroundColor = Color(0xFF0F0E17)

// Status colors
val StatusSuccess = Color(0xFF10B981)
val StatusSuccessBg = Color(0xFF064E3B)
val StatusError = Color(0xFFEF4444)
val StatusErrorBg = Color(0xFF7F1D1D)
val StatusWarning = Color(0xFFF59E0B)
val StatusInfo = Color(0xFF3B82F6)

// Partition tints - More vibrant
fun partitionTint(partition: Int): Color =
    when (partition % 8) {
        0 -> Color(0xFF312E81) // Indigo
        1 -> Color(0xFF4C1D95) // Violet
        2 -> Color(0xFF6D28D9) // Purple
        3 -> Color(0xFF7C3AED) // Light purple
        4 -> Color(0xFF8B5CF6) // Lighter purple
        5 -> Color(0xFFA855F7) // Pink-purple
        6 -> Color(0xFFD946EF) // Fuchsia
        else -> Color(0xFFEC4899) // Pink
    }

fun partitionTextColor(partition: Int): Color =
    when (partition % 8) {
        0 -> Color(0xFFE0E7FF)
        1 -> Color(0xFFF3E8FF)
        2 -> Color(0xFFF5F3FF)
        3 -> Color(0xFFFAF5FF)
        else -> Color(0xFFFDF4FF)
    }
