package com.lightkafka.core.storage

import kotlinx.serialization.Serializable
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

interface SecretStore {
    fun put(
        secretId: String,
        secretValue: String,
    )

    fun get(secretId: String): String?

    fun delete(secretId: String)
}

interface SecretKeyProvider {
    fun loadKey(): SecretKey
}

class Pbkdf2AesKeyProvider(
    private val saltFile: Path,
    private val machineFingerprintProvider: () -> String = { defaultMachineFingerprint() },
    private val iterations: Int = 120_000,
    private val keyLengthBits: Int = 256,
) : SecretKeyProvider {
    override fun loadKey(): SecretKey {
        val salt = loadOrCreateSalt()
        val passwordChars = machineFingerprintProvider().toCharArray()

        val spec = PBEKeySpec(passwordChars, salt, iterations, keyLengthBits)
        val derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()

        return SecretKeySpec(derived, AES)
    }

    private fun loadOrCreateSalt(): ByteArray {
        if (saltFile.exists()) {
            return saltFile.readBytes()
        }

        val salt = ByteArray(SALT_SIZE_BYTES)
        SecureRandom().nextBytes(salt)
        saltFile.parent?.createDirectories()
        saltFile.writeBytes(salt)
        return salt
    }

    private companion object {
        const val SALT_SIZE_BYTES = 16
        const val AES = "AES"
    }
}

class EncryptedFileSecretStore(
    secretFile: Path,
    private val keyProvider: SecretKeyProvider,
) : SecretStore {
    private val key by lazy { keyProvider.loadKey() }
    private val store =
        VersionedJsonFileStore(
            file = secretFile,
            serializer = SecretSnapshot.serializer(),
            currentVersion = CURRENT_SECRETS_VERSION,
            migrations = mapOf(0 to { it }),
        )

    override fun put(
        secretId: String,
        secretValue: String,
    ) {
        val currentState = store.load(SecretSnapshot())
        val nextEntries = currentState.entries.toMutableMap()
        nextEntries[secretId] = encrypt(secretValue)
        store.save(currentState.copy(entries = nextEntries))
    }

    override fun get(secretId: String): String? {
        val snapshot = store.load(SecretSnapshot())
        val entry = snapshot.entries[secretId] ?: return null
        return decrypt(entry)
    }

    override fun delete(secretId: String) {
        val currentState = store.load(SecretSnapshot())
        if (!currentState.entries.containsKey(secretId)) {
            return
        }

        val nextEntries = currentState.entries.toMutableMap()
        nextEntries.remove(secretId)
        store.save(currentState.copy(entries = nextEntries))
    }

    private fun encrypt(value: String): EncryptedSecret {
        val iv = ByteArray(GCM_IV_SIZE_BYTES)
        random.nextBytes(iv)

        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))

        return EncryptedSecret(
            iv = base64Encoder.encodeToString(iv),
            cipherText = base64Encoder.encodeToString(encrypted),
        )
    }

    private fun decrypt(secret: EncryptedSecret): String {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, base64Decoder.decode(secret.iv)),
        )

        val decryptedBytes = cipher.doFinal(base64Decoder.decode(secret.cipherText))
        return String(decryptedBytes, StandardCharsets.UTF_8)
    }

    private companion object {
        const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_SIZE_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
        const val CURRENT_SECRETS_VERSION = 1

        val random = SecureRandom()
        val base64Encoder: Base64.Encoder = Base64.getEncoder()
        val base64Decoder: Base64.Decoder = Base64.getDecoder()
    }
}

private fun defaultMachineFingerprint(): String {
    val user = System.getProperty("user.name").orEmpty()
    val os = System.getProperty("os.name").orEmpty()
    val home = System.getProperty("user.home").orEmpty()
    return "$user|$os|$home"
}

@Serializable
private data class SecretSnapshot(
    val entries: Map<String, EncryptedSecret> = emptyMap(),
)

@Serializable
private data class EncryptedSecret(
    val iv: String,
    val cipherText: String,
)
