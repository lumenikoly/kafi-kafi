package com.lightkafka.core.storage

import java.nio.file.Path

class JsonStorageStore(
    storageFile: Path,
    private val secretStore: SecretStore,
) : ProfileStore, TemplateStore, HistoryStore {
    private val snapshotStore =
        VersionedJsonFileStore(
            file = storageFile,
            serializer = StoredStorageSnapshot.serializer(),
            currentVersion = CURRENT_STORAGE_VERSION,
            migrations = mapOf(0 to { it }),
        )

    override fun loadProfiles(): List<ClusterProfile> =
        loadSnapshot().profiles.map { profile -> profile.toDomainProfile(secretStore) }

    override fun saveProfiles(profiles: List<ClusterProfile>) {
        val currentSnapshot = loadSnapshot()
        val storedProfiles = profiles.map { profile -> profile.toStoredProfile(secretStore) }

        removeSecretsForDeletedProfiles(
            oldProfiles = currentSnapshot.profiles,
            newProfiles = storedProfiles,
        )

        saveSnapshot(currentSnapshot.copy(profiles = storedProfiles))
    }

    override fun upsertProfile(profile: ClusterProfile) {
        val current = loadProfiles().associateBy(ClusterProfile::id).toMutableMap()
        current[profile.id] = profile
        saveProfiles(current.values.toList())
    }

    override fun deleteProfile(profileId: String) {
        val currentSnapshot = loadSnapshot()
        val toDelete = currentSnapshot.profiles.firstOrNull { it.id == profileId } ?: return
        deleteSecretsForProfile(toDelete)

        saveSnapshot(
            currentSnapshot.copy(
                profiles = currentSnapshot.profiles.filterNot { profile -> profile.id == profileId },
            ),
        )
    }

    override fun loadTemplates(): List<ProducerTemplate> = loadSnapshot().templates

    override fun saveTemplates(templates: List<ProducerTemplate>) {
        val currentSnapshot = loadSnapshot()
        saveSnapshot(currentSnapshot.copy(templates = templates))
    }

    override fun loadHistory(): List<SendHistoryEntry> = loadSnapshot().history

    override fun saveHistory(entries: List<SendHistoryEntry>) {
        val currentSnapshot = loadSnapshot()
        saveSnapshot(currentSnapshot.copy(history = entries))
    }

    private fun loadSnapshot(): StoredStorageSnapshot = snapshotStore.load(StoredStorageSnapshot())

    private fun saveSnapshot(snapshot: StoredStorageSnapshot) {
        snapshotStore.save(snapshot)
    }

    private fun removeSecretsForDeletedProfiles(
        oldProfiles: List<StoredClusterProfile>,
        newProfiles: List<StoredClusterProfile>,
    ) {
        val newIds = newProfiles.map(StoredClusterProfile::id).toSet()
        oldProfiles
            .filterNot { profile -> profile.id in newIds }
            .forEach(::deleteSecretsForProfile)
    }

    private fun deleteSecretsForProfile(profile: StoredClusterProfile) {
        profile.sasl?.passwordSecretRef?.let(secretStore::delete)
        profile.ssl?.truststorePasswordSecretRef?.let(secretStore::delete)
        profile.ssl?.keystorePasswordSecretRef?.let(secretStore::delete)
        profile.ssl?.keyPasswordSecretRef?.let(secretStore::delete)
    }

    private companion object {
        const val CURRENT_STORAGE_VERSION = 1
    }
}

private fun ClusterProfile.toStoredProfile(secretStore: SecretStore): StoredClusterProfile {
    val saslPasswordSecretRef = buildSecretId(id, "sasl-password")
    val truststorePasswordSecretRef = buildSecretId(id, "truststore-password")
    val keystorePasswordSecretRef = buildSecretId(id, "keystore-password")
    val keyPasswordSecretRef = buildSecretId(id, "key-password")

    val storedSasl =
        sasl?.let { saslConfig ->
            saslConfig.password?.let { secret -> secretStore.put(saslPasswordSecretRef, secret) }
            StoredSaslConfig(
                mechanism = saslConfig.mechanism,
                username = saslConfig.username,
                passwordSecretRef = if (saslConfig.password == null) null else saslPasswordSecretRef,
            )
        }

    val storedSsl =
        ssl?.let { sslConfig ->
            sslConfig.truststorePassword?.let { secret -> secretStore.put(truststorePasswordSecretRef, secret) }
            sslConfig.keystorePassword?.let { secret -> secretStore.put(keystorePasswordSecretRef, secret) }
            sslConfig.keyPassword?.let { secret -> secretStore.put(keyPasswordSecretRef, secret) }

            StoredSslConfig(
                truststorePath = sslConfig.truststorePath,
                truststorePasswordSecretRef =
                    if (sslConfig.truststorePassword == null) {
                        null
                    } else {
                        truststorePasswordSecretRef
                    },
                keystorePath = sslConfig.keystorePath,
                keystorePasswordSecretRef =
                    if (sslConfig.keystorePassword == null) {
                        null
                    } else {
                        keystorePasswordSecretRef
                    },
                keyPasswordSecretRef =
                    if (sslConfig.keyPassword == null) {
                        null
                    } else {
                        keyPasswordSecretRef
                    },
            )
        }

    return StoredClusterProfile(
        id = id,
        name = name,
        bootstrapServers = bootstrapServers,
        securityProtocol = securityProtocol,
        sasl = storedSasl,
        ssl = storedSsl,
        clientId = clientId,
        additionalProperties = additionalProperties,
    )
}

private fun StoredClusterProfile.toDomainProfile(secretStore: SecretStore): ClusterProfile {
    val domainSasl =
        sasl?.let { storedSasl ->
            SaslConfig(
                mechanism = storedSasl.mechanism,
                username = storedSasl.username,
                password = storedSasl.passwordSecretRef?.let(secretStore::get),
            )
        }

    val domainSsl =
        ssl?.let { storedSsl ->
            SslConfig(
                truststorePath = storedSsl.truststorePath,
                truststorePassword = storedSsl.truststorePasswordSecretRef?.let(secretStore::get),
                keystorePath = storedSsl.keystorePath,
                keystorePassword = storedSsl.keystorePasswordSecretRef?.let(secretStore::get),
                keyPassword = storedSsl.keyPasswordSecretRef?.let(secretStore::get),
            )
        }

    return ClusterProfile(
        id = id,
        name = name,
        bootstrapServers = bootstrapServers,
        securityProtocol = securityProtocol,
        sasl = domainSasl,
        ssl = domainSsl,
        clientId = clientId,
        additionalProperties = additionalProperties,
    )
}

private fun buildSecretId(
    profileId: String,
    field: String,
): String = "profile:$profileId:$field"
