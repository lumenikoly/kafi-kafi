package com.lightkafka.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter: DateTimeFormatter =
    DateTimeFormatter
        .ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())

private val dateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

internal fun formatTimestamp(
    epochMillis: Long,
    includeDate: Boolean = false,
): String {
    val instant = Instant.ofEpochMilli(epochMillis)
    return if (includeDate) dateTimeFormatter.format(instant) else timeFormatter.format(instant)
}
