package com.lightkafka.ui.connection

data class ConnectionStatus(
    val profileId: String,
    val state: ConnectionState,
    val brokerCount: Int = 0,
    val controllerId: String? = null,
    val clusterId: String? = null,
    val latencyMs: Long = 0,
    val lastTestedMs: Long = 0,
    val error: String? = null,
)

enum class ConnectionState {
    UNKNOWN,
    CONNECTED,
    ERROR,
}
