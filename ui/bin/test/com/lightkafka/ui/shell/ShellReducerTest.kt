package com.lightkafka.ui.shell

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ShellReducerTest {
    private val reducer = ShellReducer

    // ── OpenTab ────────────────────────────────────────────────────────

    @Test
    fun `OpenTab adds tab when none exists`() {
        val state = reducer(ShellState(), ShellAction.OpenTab(TabType.TOPICS))

        assertEquals(1, state.tabs.size)
        assertEquals(TabType.TOPICS, state.tabs[0].type)
        assertEquals(state.tabs[0].id, state.activeTabId)
    }

    @Test
    fun `OpenTab deduplicates by type`() {
        val s1 = reducer(ShellState(), ShellAction.OpenTab(TabType.TOPICS))
        val s2 = reducer(s1, ShellAction.OpenTab(TabType.TOPICS))

        assertEquals(1, s2.tabs.size)
        assertEquals(s1.tabs[0].id, s2.tabs[0].id)
    }

    @Test
    fun `OpenTab activates existing tab instead of duplicating`() {
        val s1 = reducer(ShellState(), ShellAction.OpenTab(TabType.TOPICS))
        val s2 = reducer(s1, ShellAction.OpenTab(TabType.BROKERS))
        // Activate brokers, then re-open topics — should just switch back
        val s3 = reducer(s2, ShellAction.OpenTab(TabType.TOPICS))

        assertEquals(2, s3.tabs.size)
        assertEquals(s1.tabs[0].id, s3.activeTabId)
    }

    @Test
    fun `OpenTab appends multiple different types`() {
        val s1 = reducer(ShellState(), ShellAction.OpenTab(TabType.TOPICS))
        val s2 = reducer(s1, ShellAction.OpenTab(TabType.BROKERS))
        val s3 = reducer(s2, ShellAction.OpenTab(TabType.CONSUMER_GROUPS))

        assertEquals(3, s3.tabs.size)
        assertEquals(TabType.CONSUMER_GROUPS, s3.tabs[2].type)
    }

    // ── CloseTab ───────────────────────────────────────────────────────

    @Test
    fun `CloseTab removes the tab`() {
        val s1 = reducer(ShellState(), ShellAction.OpenTab(TabType.TOPICS))
        val s2 = reducer(s1, ShellAction.CloseTab(s1.tabs[0].id))

        assertEquals(0, s2.tabs.size)
        assertNull(s2.activeTabId)
    }

    @Test
    fun `CloseTab auto-switches to previous tab when closing active`() {
        val s1 = reducer(ShellState(), ShellAction.OpenTab(TabType.TOPICS))
        val s2 = reducer(s1, ShellAction.OpenTab(TabType.BROKERS))
        // Active is BROKERS; close it → should fall back to TOPICS
        val s3 = reducer(s2, ShellAction.CloseTab(s2.tabs[1].id))

        assertEquals(1, s3.tabs.size)
        assertEquals(s1.tabs[0].id, s3.activeTabId)
    }

    @Test
    fun `CloseTab falls forward when closing first tab`() {
        val s1 = reducer(ShellState(), ShellAction.OpenTab(TabType.TOPICS))
        val s2 = reducer(s1, ShellAction.OpenTab(TabType.BROKERS))
        // Switch to first tab, close it → should fall forward to BROKERS
        val s3 = reducer(s2, ShellAction.SwitchTab(s1.tabs[0].id))
        val s4 = reducer(s3, ShellAction.CloseTab(s1.tabs[0].id))

        assertEquals(1, s4.tabs.size)
        assertEquals(s2.tabs[1].id, s4.activeTabId)
    }

    @Test
    fun `CloseTab on nonexistent id is no-op`() {
        val s1 = reducer(ShellState(), ShellAction.OpenTab(TabType.TOPICS))
        val s2 = reducer(s1, ShellAction.CloseTab("nonexistent"))

        assertEquals(s1, s2)
    }

    @Test
    fun `CloseTab on inactive tab keeps activeTabId`() {
        val s1 = reducer(ShellState(), ShellAction.OpenTab(TabType.TOPICS))
        val s2 = reducer(s1, ShellAction.OpenTab(TabType.BROKERS))
        // Active is BROKERS; close TOPICS (inactive)
        val s3 = reducer(s2, ShellAction.CloseTab(s1.tabs[0].id))

        assertEquals(1, s3.tabs.size)
        assertEquals(s2.tabs[1].id, s3.activeTabId)
    }

    // ── CloseOtherTabs ─────────────────────────────────────────────────

    @Test
    fun `CloseOtherTabs keeps only the referenced tab`() {
        val s1 = openThreeTabs()
        val keepId = s1.tabs[1].id
        val s2 = reducer(s1, ShellAction.CloseOtherTabs(keepId))

        assertEquals(1, s2.tabs.size)
        assertEquals(keepId, s2.tabs[0].id)
        assertEquals(keepId, s2.activeTabId)
    }

    @Test
    fun `CloseOtherTabs with nonexistent id is no-op`() {
        val s1 = openThreeTabs()
        val s2 = reducer(s1, ShellAction.CloseOtherTabs("nonexistent"))

        assertEquals(s1, s2)
    }

    // ── SwitchTab ──────────────────────────────────────────────────────

    @Test
    fun `SwitchTab changes activeTabId`() {
        val s1 = reducer(ShellState(), ShellAction.OpenTab(TabType.TOPICS))
        val s2 = reducer(s1, ShellAction.OpenTab(TabType.BROKERS))
        val s3 = reducer(s2, ShellAction.SwitchTab(s1.tabs[0].id))

        assertEquals(s1.tabs[0].id, s3.activeTabId)
    }

    @Test
    fun `SwitchTab with nonexistent id is no-op`() {
        val s1 = reducer(ShellState(), ShellAction.OpenTab(TabType.TOPICS))
        val s2 = reducer(s1, ShellAction.SwitchTab("nonexistent"))

        assertEquals(s1, s2)
    }

    // ── NavigateSidebar ────────────────────────────────────────────────

    @Test
    fun `NavigateSidebar changes section`() {
        val state = reducer(ShellState(), ShellAction.NavigateSidebar(SidebarSection.BROKERS))
        assertEquals(SidebarSection.BROKERS, state.sidebarSection)
    }

    @Test
    fun `NavigateSidebar does not affect tabs`() {
        val s1 = reducer(ShellState(), ShellAction.OpenTab(TabType.TOPICS))
        val s2 = reducer(s1, ShellAction.NavigateSidebar(SidebarSection.SETTINGS))

        assertEquals(s1.tabs, s2.tabs)
        assertEquals(s1.activeTabId, s2.activeTabId)
        assertEquals(SidebarSection.SETTINGS, s2.sidebarSection)
    }

    // ── Edge cases ─────────────────────────────────────────────────────

    @Test
    fun `close last tab leaves empty workspace`() {
        val s1 = reducer(ShellState(), ShellAction.OpenTab(TabType.SETTINGS))
        val s2 = reducer(s1, ShellAction.CloseTab(s1.tabs[0].id))

        assertEquals(0, s2.tabs.size)
        assertNull(s2.activeTabId)
    }

    @Test
    fun `open all tab types`() {
        var state = ShellState()
        for (type in TabType.entries) {
            state = reducer(state, ShellAction.OpenTab(type))
        }

        assertEquals(TabType.entries.size, state.tabs.size)
        assertEquals(TabType.entries.map { it.name.lowercase() }, state.tabs.map { it.id })
    }

    @Test
    fun `OpenTab with CONNECTIONS type`() {
        val state = reducer(ShellState(), ShellAction.OpenTab(TabType.CONNECTIONS))

        assertEquals(1, state.tabs.size)
        assertEquals(TabType.CONNECTIONS, state.tabs[0].type)
        assertEquals("connections", state.tabs[0].id)
        assertEquals(state.tabs[0].id, state.activeTabId)
    }

    @Test
    fun `OpenTab with CLUSTER_OVERVIEW type`() {
        val state = reducer(ShellState(), ShellAction.OpenTab(TabType.CLUSTER_OVERVIEW))

        assertEquals(1, state.tabs.size)
        assertEquals(TabType.CLUSTER_OVERVIEW, state.tabs[0].type)
        assertEquals("cluster_overview", state.tabs[0].id)
        assertEquals(state.tabs[0].id, state.activeTabId)
    }

    @Test
    fun `close middle tab auto-switches to previous`() {
        val s1 = openThreeTabs()
        val middleId = s1.tabs[1].id
        // Activate middle
        val s2 = reducer(s1, ShellAction.SwitchTab(middleId))
        // Close it → should activate first tab
        val s3 = reducer(s2, ShellAction.CloseTab(middleId))

        assertEquals(2, s3.tabs.size)
        assertEquals(s1.tabs[0].id, s3.activeTabId)
    }

    @Test
    fun `initial state has no tabs and topics sidebar`() {
        val state = ShellState()
        assertEquals(0, state.tabs.size)
        assertNull(state.activeTabId)
        assertEquals(SidebarSection.TOPICS, state.sidebarSection)
    }

    // ── OpenEntityTab ────────────────────────────────────────────────

    @Test
    fun `OpenEntityTab creates topic detail tab`() {
        val state = reducer(ShellState(), ShellAction.OpenEntityTab(TabType.TOPIC_DETAIL, "orders", "orders"))

        assertEquals(1, state.tabs.size)
        assertEquals(TabType.TOPIC_DETAIL, state.tabs[0].type)
        assertEquals("topic-detail-orders", state.tabs[0].id)
        assertEquals("orders", state.tabs[0].title)
        assertEquals(state.tabs[0].id, state.activeTabId)
    }

    @Test
    fun `OpenEntityTab deduplicates by type and entityId`() {
        val s1 = reducer(ShellState(), ShellAction.OpenEntityTab(TabType.TOPIC_DETAIL, "orders", "orders"))
        val s2 = reducer(s1, ShellAction.OpenEntityTab(TabType.TOPIC_DETAIL, "orders", "orders"))

        assertEquals(1, s2.tabs.size)
        assertEquals(s1.tabs[0].id, s2.tabs[0].id)
    }

    @Test
    fun `OpenEntityTab allows multiple different entities`() {
        val s1 = reducer(ShellState(), ShellAction.OpenEntityTab(TabType.TOPIC_DETAIL, "orders", "orders"))
        val s2 = reducer(s1, ShellAction.OpenEntityTab(TabType.TOPIC_DETAIL, "payments", "payments"))

        assertEquals(2, s2.tabs.size)
        assertEquals("topic-detail-orders", s2.tabs[0].id)
        assertEquals("topic-detail-payments", s2.tabs[1].id)
        assertEquals(s2.tabs[1].id, s2.activeTabId)
    }

    @Test
    fun `OpenEntityTab activates existing tab for same entity`() {
        val s1 = reducer(ShellState(), ShellAction.OpenEntityTab(TabType.TOPIC_DETAIL, "orders", "orders"))
        val s2 = reducer(s1, ShellAction.OpenEntityTab(TabType.TOPIC_DETAIL, "payments", "payments"))
        // Re-open orders — should activate existing, not add new
        val s3 = reducer(s2, ShellAction.OpenEntityTab(TabType.TOPIC_DETAIL, "orders", "orders"))

        assertEquals(2, s3.tabs.size)
        assertEquals(s1.tabs[0].id, s3.activeTabId)
    }

    @Test
    fun `OpenEntityTab mixes with regular OpenTab`() {
        val s1 = reducer(ShellState(), ShellAction.OpenTab(TabType.TOPICS))
        val s2 = reducer(s1, ShellAction.OpenEntityTab(TabType.TOPIC_DETAIL, "orders", "orders"))
        val s3 = reducer(s2, ShellAction.OpenTab(TabType.BROKERS))

        assertEquals(3, s3.tabs.size)
        assertEquals(TabType.TOPICS, s3.tabs[0].type)
        assertEquals(TabType.TOPIC_DETAIL, s3.tabs[1].type)
        assertEquals(TabType.BROKERS, s3.tabs[2].type)
    }

    @Test
    fun `OpenEntityTab re-opening entity activates existing after other tab opened`() {
        val s1 = reducer(ShellState(), ShellAction.OpenEntityTab(TabType.TOPIC_DETAIL, "orders", "orders"))
        val s2 = reducer(s1, ShellAction.OpenTab(TabType.BROKERS))
        assertEquals(s2.tabs[1].id, s2.activeTabId) // BROKERS active

        val s3 = reducer(s2, ShellAction.OpenEntityTab(TabType.TOPIC_DETAIL, "orders", "orders"))
        assertEquals(2, s3.tabs.size) // No new tab added
        assertEquals(s1.tabs[0].id, s3.activeTabId) // orders re-activated
    }

    // ── helpers ────────────────────────────────────────────────────────

    private fun openThreeTabs(): ShellState {
        val s1 = reducer(ShellState(), ShellAction.OpenTab(TabType.TOPICS))
        val s2 = reducer(s1, ShellAction.OpenTab(TabType.BROKERS))
        return reducer(s2, ShellAction.OpenTab(TabType.CONSUMER_GROUPS))
    }
}
