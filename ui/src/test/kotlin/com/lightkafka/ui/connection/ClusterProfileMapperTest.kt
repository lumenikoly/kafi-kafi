package com.lightkafka.ui.connection

import com.lightkafka.core.storage.ClusterProfile
import com.lightkafka.core.storage.SaslConfig
import com.lightkafka.core.storage.SaslMechanism
import com.lightkafka.core.storage.SecurityProtocol
import com.lightkafka.core.storage.SslConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ClusterProfileMapperTest {
    @Test
    fun `PLAINTEXT profile maps correctly`() {
        val profile =
            ClusterProfile(
                id = "p1",
                name = "Local",
                bootstrapServers = listOf("localhost:9092"),
            )

        val config = profile.toKafkaConnectionConfig()

        assertEquals(listOf("localhost:9092"), config.bootstrapServers)
        assertEquals("PLAINTEXT", config.securityProtocol)
        assertNull(config.saslMechanism)
        assertNull(config.saslUsername)
        assertNull(config.saslPassword)
        assertNull(config.sslTruststorePath)
        assertNull(config.sslKeystorePath)
    }

    @Test
    fun `SASL_PLAINTEXT with SCRAM_SHA_256 maps with correct mechanism and JAAS config`() {
        val profile =
            ClusterProfile(
                id = "p2",
                name = "SASL Cluster",
                bootstrapServers = listOf("kafka1:9092", "kafka2:9092"),
                securityProtocol = SecurityProtocol.SASL_PLAINTEXT,
                sasl =
                    SaslConfig(
                        mechanism = SaslMechanism.SCRAM_SHA_256,
                        username = "admin",
                        password = "secret123",
                    ),
            )

        val config = profile.toKafkaConnectionConfig()

        assertEquals("SASL_PLAINTEXT", config.securityProtocol)
        assertEquals("SCRAM-SHA-256", config.saslMechanism)
        assertEquals("admin", config.saslUsername)
        assertEquals("secret123", config.saslPassword)
    }

    @Test
    fun `SASL_SSL with all fields maps correctly`() {
        val profile =
            ClusterProfile(
                id = "p3",
                name = "Secure Cluster",
                bootstrapServers = listOf("kafka.example.com:9093"),
                securityProtocol = SecurityProtocol.SASL_SSL,
                sasl =
                    SaslConfig(
                        mechanism = SaslMechanism.PLAIN,
                        username = "user",
                        password = "pass",
                    ),
                ssl =
                    SslConfig(
                        truststorePath = "/truststore.jks",
                        truststorePassword = "ts-pass",
                        keystorePath = "/keystore.jks",
                        keystorePassword = "ks-pass",
                        keyPassword = "key-pass",
                    ),
            )

        val config = profile.toKafkaConnectionConfig()

        assertEquals("SASL_SSL", config.securityProtocol)
        assertEquals("PLAIN", config.saslMechanism)
        assertEquals("user", config.saslUsername)
        assertEquals("pass", config.saslPassword)
        assertEquals("/truststore.jks", config.sslTruststorePath)
        assertEquals("ts-pass", config.sslTruststorePassword)
        assertEquals("/keystore.jks", config.sslKeystorePath)
        assertEquals("ks-pass", config.sslKeystorePassword)
        assertEquals("key-pass", config.sslKeyPassword)
    }

    @Test
    fun `SSL with truststore and keystore maps correctly`() {
        val profile =
            ClusterProfile(
                id = "p4",
                name = "SSL Cluster",
                bootstrapServers = listOf("ssl-kafka:9093"),
                securityProtocol = SecurityProtocol.SSL,
                ssl =
                    SslConfig(
                        truststorePath = "/ssl/truststore.jks",
                        truststorePassword = "trust-pass",
                        keystorePath = "/ssl/keystore.jks",
                        keystorePassword = "key-pass",
                    ),
            )

        val config = profile.toKafkaConnectionConfig()

        assertEquals("SSL", config.securityProtocol)
        assertNull(config.saslMechanism)
        assertEquals("/ssl/truststore.jks", config.sslTruststorePath)
        assertEquals("trust-pass", config.sslTruststorePassword)
        assertEquals("/ssl/keystore.jks", config.sslKeystorePath)
        assertEquals("key-pass", config.sslKeystorePassword)
    }

    @Test
    fun `additional properties are included in properties map`() {
        val profile =
            ClusterProfile(
                id = "p5",
                name = "Custom",
                bootstrapServers = listOf("localhost:9092"),
                clientId = "my-client",
                additionalProperties =
                    mapOf(
                        "acks" to "all",
                        "retry.backoff.ms" to "500",
                    ),
            )

        val config = profile.toKafkaConnectionConfig()

        assertEquals("my-client", config.clientId)
        assertEquals("all", config.properties["acks"])
        assertEquals("500", config.properties["retry.backoff.ms"])
    }
}
