package com.lightkafka.core.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readText

class JsonStorageStoreTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `stores profiles templates and history in versioned json`() {
        val storageFile = tempDir.resolve("storage.json")
        val secretStore =
            EncryptedFileSecretStore(
                secretFile = tempDir.resolve("secrets.json"),
                keyProvider =
                    Pbkdf2AesKeyProvider(
                        saltFile = tempDir.resolve("salt.bin"),
                        machineFingerprintProvider = { "machine-fingerprint" },
                    ),
            )
        val store = JsonStorageStore(storageFile = storageFile, secretStore = secretStore)

        val profile =
            ClusterProfile(
                id = "local",
                name = "Local",
                bootstrapServers = listOf("localhost:9092"),
                securityProtocol = SecurityProtocol.SASL_SSL,
                sasl =
                    SaslConfig(
                        mechanism = SaslMechanism.SCRAM_SHA_256,
                        username = "alice",
                        password = "top-secret",
                    ),
            )
        val template =
            ProducerTemplate(
                id = "tpl-1",
                name = "Order Created",
                topic = "orders",
                key = "order-1",
                value = "{\"event\":\"created\"}",
                headers = mapOf("source" to "test"),
            )
        val historyEntry =
            SendHistoryEntry(
                id = "hist-1",
                profileId = "local",
                topic = "orders",
                partition = 1,
                status = SendStatus.SUCCESS,
                timestampEpochMillis = 1700000000000,
            )
        val settings =
            AppSettings(
                defaultConsumerStartPosition = DefaultConsumerStartPosition.EARLIEST,
                messageBufferLimit = 25_000,
            )

        store.saveProfiles(listOf(profile))
        store.saveTemplates(listOf(template))
        store.saveHistory(listOf(historyEntry))
        store.saveSettings(settings)

        assertEquals(listOf(profile), store.loadProfiles())
        assertEquals(listOf(template), store.loadTemplates())
        assertEquals(listOf(historyEntry), store.loadHistory())
        assertEquals(settings, store.loadSettings())
        assertEquals(listOf("Local"), store.loadProfileNames())

        val rawStorage = storageFile.readText()
        assertFalse(rawStorage.contains("top-secret"))
        assertFalse(rawStorage.contains("\"version\":0"))
        assertTrue(rawStorage.contains("\"version\""))
    }

    @Test
    fun `normalizes message buffer limit at storage boundary`() {
        val store =
            JsonStorageStore(
                storageFile = tempDir.resolve("settings.json"),
                secretStore =
                    EncryptedFileSecretStore(
                        secretFile = tempDir.resolve("settings-secrets.json"),
                        keyProvider =
                            Pbkdf2AesKeyProvider(
                                saltFile = tempDir.resolve("settings-salt.bin"),
                                machineFingerprintProvider = { "machine-fingerprint" },
                            ),
                    ),
            )

        store.saveSettings(AppSettings(messageBufferLimit = 1))

        assertEquals(AppSettings.MIN_MESSAGE_BUFFER_LIMIT, store.loadSettings().messageBufferLimit)
    }
}
