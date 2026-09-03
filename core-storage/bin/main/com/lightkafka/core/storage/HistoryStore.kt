package com.lightkafka.core.storage

interface HistoryStore {
    fun loadHistory(): List<SendHistoryEntry>

    fun saveHistory(entries: List<SendHistoryEntry>)
}
