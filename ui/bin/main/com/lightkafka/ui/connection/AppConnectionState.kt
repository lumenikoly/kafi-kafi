package com.lightkafka.ui.connection

import com.lightkafka.core.kafka.KafkaAdminService
import com.lightkafka.core.storage.ClusterProfile

/**
 * Tracks the active connection lifecycle: which profile is connected,
 * the running [KafkaAdminService], and the current connection status.
 *
 * Managed via [MutableStateFlow] in AppContent rather than a Store,
 * because connection lifecycle involves coroutine-based network calls
 * that are inherently side-effect-heavy.
 */
data class AppConnectionState(
    val activeProfile: ClusterProfile? = null,
    val activeAdminService: KafkaAdminService? = null,
    val connectionStatus: ConnectionStatus? = null,
    val isConnecting: Boolean = false,
)
