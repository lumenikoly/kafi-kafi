package com.lightkafka.ui

import com.lightkafka.core.storage.ClusterProfile
import com.lightkafka.core.storage.SaslConfig
import com.lightkafka.core.storage.SaslMechanism
import com.lightkafka.core.storage.SecurityProtocol
import com.lightkafka.core.storage.SslConfig

data class ConnectionEditorState(
    // Basic
    val profileId: String? = null,
    val name: String = "",
    val bootstrapServers: String = "localhost:9092",
    val clientId: String = "",
    // Security
    val securityProtocol: SecurityProtocol = SecurityProtocol.PLAINTEXT,
    val saslMechanism: SaslMechanism = SaslMechanism.PLAIN,
    val saslUsername: String = "",
    val saslPassword: String = "",
    // SSL
    val truststorePath: String = "",
    val truststorePassword: String = "",
    val keystorePath: String = "",
    val keystorePassword: String = "",
    val keyPassword: String = "",
    // Advanced
    val additionalProperties: Map<String, String> = emptyMap(),
    val newPropertyKey: String = "",
    val newPropertyValue: String = "",
    // UI State
    val testStatus: ConnectionTestStatus? = null,
    val isTesting: Boolean = false,
    val showPassword: Boolean = false,
    val expandedSection: ConnectionEditorSection = ConnectionEditorSection.BASIC,
) {
    val isNewProfile: Boolean
        get() = profileId == null

    val canSave: Boolean
        get() = name.isNotBlank() && bootstrapServers.isNotBlank()

    val needsSasl: Boolean
        get() =
            securityProtocol == SecurityProtocol.SASL_PLAINTEXT ||
                securityProtocol == SecurityProtocol.SASL_SSL

    val needsSsl: Boolean
        get() =
            securityProtocol == SecurityProtocol.SSL ||
                securityProtocol == SecurityProtocol.SASL_SSL
}

enum class ConnectionEditorSection {
    BASIC,
    SECURITY,
    ADVANCED,
}

sealed class ConnectionTestStatus {
    data class Success(
        val clusterId: String?,
        val brokerCount: Int,
        val controllerId: String?,
        val topicCount: Int,
        val latencyMs: Long,
    ) : ConnectionTestStatus()

    data class Failure(
        val error: String,
        val suggestion: String? = null,
    ) : ConnectionTestStatus()

    data class InProgress(
        val message: String = "Testing connection...",
    ) : ConnectionTestStatus()
}

fun ClusterProfile.toEditorState(): ConnectionEditorState =
    ConnectionEditorState(
        profileId = id,
        name = name,
        bootstrapServers = bootstrapServers.joinToString(","),
        clientId = clientId.orEmpty(),
        securityProtocol = securityProtocol,
        saslMechanism = sasl?.mechanism ?: SaslMechanism.PLAIN,
        saslUsername = sasl?.username.orEmpty(),
        saslPassword = sasl?.password.orEmpty(),
        truststorePath = ssl?.truststorePath.orEmpty(),
        truststorePassword = ssl?.truststorePassword.orEmpty(),
        keystorePath = ssl?.keystorePath.orEmpty(),
        keystorePassword = ssl?.keystorePassword.orEmpty(),
        keyPassword = ssl?.keyPassword.orEmpty(),
        additionalProperties = additionalProperties,
    )

fun ConnectionEditorState.toProfile(id: String): ClusterProfile {
    val servers =
        bootstrapServers
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .ifEmpty { listOf("localhost:9092") }

    val saslConfig =
        if (needsSasl && (saslUsername.isNotBlank() || saslPassword.isNotBlank())) {
            SaslConfig(
                mechanism = saslMechanism,
                username = saslUsername.ifBlank { null },
                password = saslPassword.ifBlank { null },
            )
        } else {
            null
        }

    val sslConfig =
        if (needsSsl && (truststorePath.isNotBlank() || keystorePath.isNotBlank())) {
            SslConfig(
                truststorePath = truststorePath.ifBlank { null },
                truststorePassword = truststorePassword.ifBlank { null },
                keystorePath = keystorePath.ifBlank { null },
                keystorePassword = keystorePassword.ifBlank { null },
                keyPassword = keyPassword.ifBlank { null },
            )
        } else {
            null
        }

    return ClusterProfile(
        id = id,
        name = name.ifBlank { "New Connection" },
        bootstrapServers = servers,
        securityProtocol = securityProtocol,
        sasl = saslConfig,
        ssl = sslConfig,
        clientId = clientId.ifBlank { null },
        additionalProperties = additionalProperties.filter { it.key.isNotBlank() },
    )
}
