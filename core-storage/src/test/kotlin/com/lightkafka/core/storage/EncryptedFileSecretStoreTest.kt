package com.lightkafka.core.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readText

class EncryptedFileSecretStoreTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `put and get roundtrip secret`() {
        val secretStore =
            EncryptedFileSecretStore(
                secretFile = tempDir.resolve("secrets.json"),
                keyProvider =
                    Pbkdf2AesKeyProvider(
                        saltFile = tempDir.resolve("salt.bin"),
                        machineFingerprintProvider = { "machine-fingerprint" },
                    ),
            )

        secretStore.put("profile:local:saslPassword", "top-secret")

        assertEquals("top-secret", secretStore.get("profile:local:saslPassword"))
    }

    @Test
    fun `stored file never contains plaintext secrets`() {
        val secretPath = tempDir.resolve("secrets.json")
        val secretStore =
            EncryptedFileSecretStore(
                secretFile = secretPath,
                keyProvider =
                    Pbkdf2AesKeyProvider(
                        saltFile = tempDir.resolve("salt.bin"),
                        machineFingerprintProvider = { "machine-fingerprint" },
                    ),
            )

        secretStore.put("profile:local:saslPassword", "top-secret")

        val rawFile = secretPath.readText()
        assertFalse(rawFile.contains("top-secret"))
    }

    @Test
    fun `delete removes secret`() {
        val secretStore =
            EncryptedFileSecretStore(
                secretFile = tempDir.resolve("secrets.json"),
                keyProvider =
                    Pbkdf2AesKeyProvider(
                        saltFile = tempDir.resolve("salt.bin"),
                        machineFingerprintProvider = { "machine-fingerprint" },
                    ),
            )

        secretStore.put("profile:local:saslPassword", "top-secret")
        secretStore.delete("profile:local:saslPassword")

        assertNull(secretStore.get("profile:local:saslPassword"))
    }
}
