package com.lightkafka.ui.infra

import androidx.compose.ui.graphics.Color

// Neutral operator workspace with one action accent.
val PrimaryDark = Color(0xFF10141B)
val Primary = Color(0xFF3F6FD8)
val PrimaryLight = Color(0xFF6F9AFF)
val PrimarySurface = Color(0xFF172238)

// Accent colors - Vibrant purple/pink gradient
val AccentViolet = Color(0xFF5B8CFF)
val AccentPink = Color(0xFFE982A9)
val AccentCyan = Color(0xFF6EC8D5)
val AccentAmber = Color(0xFFF6C177)
val AccentEmerald = Color(0xFF5CCB8A)

// Surface colors - Dark mode inspired
val SurfaceDark = Color(0xFF0F1217)
val SurfaceCard = Color(0xFF14181F)
val SurfaceElevated = Color(0xFF1B2028)
val SurfaceHover = Color(0xFF232A34)

// Background
val AppBackgroundColor = Color(0xFF0B0D10)
val HeaderBackgroundColor = Color(0xFF10141A)
val SidebarBackgroundColor = Color(0xFF0F1217)

// Text colors
val TextPrimary = Color(0xFFF4F7FA)
val TextSecondary = Color(0xFFB4BDC9)
val TextMuted = Color(0xFF7F8998)
val TextAccent = Color(0xFF91B1FF)

// Border colors
val BorderSubtle = Color(0xFF252B34)
val BorderDefault = Color(0xFF343C48)
val BorderAccent = Color(0xFF385A9F)

// Legacy compat
val AccentColor = AccentViolet
val AccentTextStrong = Color(0xFFBDD0FF)
val AccentMuted = Color(0xFF3F6FD8)
val InspectorBackgroundColor = Color(0xFF0F1217)

// Status colors
val StatusSuccess = Color(0xFF5CCB8A)
val StatusSuccessBg = Color(0xFF123523)
val StatusError = Color(0xFFFF7A7A)
val StatusErrorBg = Color(0xFF3A181C)
val StatusWarning = Color(0xFFF6C177)
val StatusInfo = Color(0xFF6F9AFF)

// Partition tints - More vibrant
fun partitionTint(partition: Int): Color =
    when (partition % 8) {
        0 -> Color(0xFF243B63)
        1 -> Color(0xFF244B5A)
        2 -> Color(0xFF384263)
        3 -> Color(0xFF2E4D45)
        4 -> Color(0xFF4A3D58)
        5 -> Color(0xFF4D4938)
        6 -> Color(0xFF304B5C)
        else -> Color(0xFF3F4654)
    }

fun partitionTextColor(partition: Int): Color =
    when (partition % 8) {
        0 -> Color(0xFFD6E4FF)
        1 -> Color(0xFFD2EEF2)
        2 -> Color(0xFFE0E5FF)
        3 -> Color(0xFFD8EFE8)
        else -> Color(0xFFE7EAF0)
    }
