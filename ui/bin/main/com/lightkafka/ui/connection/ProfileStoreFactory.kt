package com.lightkafka.ui.connection

import com.lightkafka.core.storage.EncryptedFileSecretStore
import com.lightkafka.core.storage.JsonStorageStore
import com.lightkafka.core.storage.Pbkdf2AesKeyProvider
import com.lightkafka.core.storage.ProfileStore
import java.nio.file.Path
import kotlin.io.path.createDirectories

/**
 * Creates a [ProfileStore] backed by encrypted file storage at
 * the OS-appropriate data directory (`~/.lightkafka/`).
 */
fun createProfileStore(): ProfileStore {
    val baseDir = Path.of(System.getProperty("user.home"), ".lightkafka")
    baseDir.createDirectories()

    val secretsDir = baseDir.resolve("secrets")
    secretsDir.createDirectories()

    val keyProvider = Pbkdf2AesKeyProvider(saltFile = secretsDir.resolve(".salt"))
    val secretStore =
        EncryptedFileSecretStore(
            secretFile = secretsDir.resolve("secrets.json"),
            keyProvider = keyProvider,
        )

    return JsonStorageStore(
        storageFile = baseDir.resolve("storage.json"),
        secretStore = secretStore,
    )
}
