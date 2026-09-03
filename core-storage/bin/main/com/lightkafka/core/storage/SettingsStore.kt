package com.lightkafka.core.storage

interface SettingsStore {
    fun loadSettings(): AppSettings

    fun saveSettings(settings: AppSettings)
}
