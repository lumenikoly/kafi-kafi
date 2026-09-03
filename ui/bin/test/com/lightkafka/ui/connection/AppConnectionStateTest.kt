package com.lightkafka.ui.connection

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AppConnectionStateTest {
    @Test
    fun `initial state has null activeProfile and adminService`() {
        val state = AppConnectionState()

        assertNull(state.activeProfile)
        assertNull(state.activeAdminService)
        assertNull(state.connectionStatus)
        assertFalse(state.isConnecting)
    }

    @Test
    fun `data class copy produces correct updated state`() {
        val initial = AppConnectionState()
        val profile =
            com.lightkafka.core.storage.ClusterProfile(
                id = "test-id",
                name = "Test Cluster",
                bootstrapServers = listOf("localhost:9092"),
            )
        val status =
            ConnectionStatus(
                profileId = "test-id",
                state = ConnectionState.CONNECTED,
                brokerCount = 3,
            )

        val updated =
            initial.copy(
                activeProfile = profile,
                connectionStatus = status,
                isConnecting = true,
            )

        assertEquals(profile, updated.activeProfile)
        assertEquals(status, updated.connectionStatus)
        assertEquals(true, updated.isConnecting)
        assertNull(updated.activeAdminService)
    }
}
