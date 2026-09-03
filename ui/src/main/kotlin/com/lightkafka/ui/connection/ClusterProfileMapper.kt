package com.lightkafka.ui.connection

import com.lightkafka.core.kafka.KafkaConnectionConfig
import com.lightkafka.core.storage.ClusterProfile
import com.lightkafka.core.storage.SaslMechanism

/**
 * Maps a [ClusterProfile] (storage model) to a [KafkaConnectionConfig]
 * (Kafka client model), extracting SASL/SSL fields and building the
 * Kafka client properties map.
 */
fun ClusterProfile.toKafkaConnectionConfig(): KafkaConnectionConfig {
    val properties = mutableMapOf<String, String>()
    properties.putAll(additionalProperties)

    val securityProtocolStr = securityProtocol.name

    val saslMechanism = sasl?.mechanism?.kafkaName
    val saslUsername = sasl?.username
    val saslPassword = sasl?.password

    return KafkaConnectionConfig(
        bootstrapServers = bootstrapServers,
        clientId = clientId,
        properties = properties,
        securityProtocol = securityProtocolStr,
        saslMechanism = saslMechanism,
        saslUsername = saslUsername,
        saslPassword = saslPassword,
        sslTruststorePath = ssl?.truststorePath,
        sslTruststorePassword = ssl?.truststorePassword,
        sslKeystorePath = ssl?.keystorePath,
        sslKeystorePassword = ssl?.keystorePassword,
        sslKeyPassword = ssl?.keyPassword,
    )
}

private val SaslMechanism.kafkaName: String
    get() =
        when (this) {
            SaslMechanism.PLAIN -> "PLAIN"
            SaslMechanism.SCRAM_SHA_256 -> "SCRAM-SHA-256"
            SaslMechanism.SCRAM_SHA_512 -> "SCRAM-SHA-512"
            SaslMechanism.OAUTHBEARER -> "OAUTHBEARER"
        }
