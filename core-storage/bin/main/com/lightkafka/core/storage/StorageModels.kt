package com.lightkafka.core.storage

import kotlinx.serialization.Serializable

@Serializable
enum class DefaultConsumerStartPosition {
    LATEST,
    EARLIEST,
}

@Serializable
data class AppSettings(
    val defaultConsumerStartPosition: DefaultConsumerStartPosition = DefaultConsumerStartPosition.LATEST,
    val messageBufferLimit: Int = DEFAULT_MESSAGE_BUFFER_LIMIT,
) {
    fun normalized(): AppSettings = copy(messageBufferLimit = messageBufferLimit.coerceIn(MIN_MESSAGE_BUFFER_LIMIT, MAX_MESSAGE_BUFFER_LIMIT))

    companion object {
        const val DEFAULT_MESSAGE_BUFFER_LIMIT = 10_000
        const val MIN_MESSAGE_BUFFER_LIMIT = 100
        const val MAX_MESSAGE_BUFFER_LIMIT = 100_000
    }
}

@Serializable
enum class SecurityProtocol {
    PLAINTEXT,
    SSL,
    SASL_SSL,
    SASL_PLAINTEXT,
}

@Serializable
enum class SaslMechanism {
    PLAIN,
    SCRAM_SHA_256,
    SCRAM_SHA_512,
    OAUTHBEARER,
}

data class ClusterProfile(
    val id: String,
    val name: String,
    val bootstrapServers: List<String>,
    val securityProtocol: SecurityProtocol = SecurityProtocol.PLAINTEXT,
    val sasl: SaslConfig? = null,
    val ssl: SslConfig? = null,
    val clientId: String? = null,
    val additionalProperties: Map<String, String> = emptyMap(),
)

data class SaslConfig(
    val mechanism: SaslMechanism = SaslMechanism.PLAIN,
    val username: String? = null,
    val password: String? = null,
)

data class SslConfig(
    val truststorePath: String? = null,
    val truststorePassword: String? = null,
    val keystorePath: String? = null,
    val keystorePassword: String? = null,
    val keyPassword: String? = null,
)

@Serializable
data class ProducerTemplate(
    val id: String,
    val name: String,
    val topic: String,
    val partition: Int? = null,
    val key: String? = null,
    val value: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val updatedAtEpochMillis: Long = 0,
)

@Serializable
enum class SendStatus {
    SUCCESS,
    FAILURE,
}

@Serializable
data class SendHistoryEntry(
    val id: String,
    val profileId: String,
    val topic: String,
    val partition: Int? = null,
    val status: SendStatus,
    val timestampEpochMillis: Long,
    val errorMessage: String? = null,
)

@Serializable
internal data class StoredStorageSnapshot(
    val profiles: List<StoredClusterProfile> = emptyList(),
    val templates: List<ProducerTemplate> = emptyList(),
    val history: List<SendHistoryEntry> = emptyList(),
    val settings: AppSettings = AppSettings(),
)

@Serializable
internal data class StoredClusterProfile(
    val id: String,
    val name: String,
    val bootstrapServers: List<String>,
    val securityProtocol: SecurityProtocol = SecurityProtocol.PLAINTEXT,
    val sasl: StoredSaslConfig? = null,
    val ssl: StoredSslConfig? = null,
    val clientId: String? = null,
    val additionalProperties: Map<String, String> = emptyMap(),
)

@Serializable
internal data class StoredSaslConfig(
    val mechanism: SaslMechanism = SaslMechanism.PLAIN,
    val username: String? = null,
    val passwordSecretRef: String? = null,
)

@Serializable
internal data class StoredSslConfig(
    val truststorePath: String? = null,
    val truststorePasswordSecretRef: String? = null,
    val keystorePath: String? = null,
    val keystorePasswordSecretRef: String? = null,
    val keyPasswordSecretRef: String? = null,
)
