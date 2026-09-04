package com.lightkafka.ui.shell

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ShellReducerTest {
    private val reducer = ShellReducer

    @Test
    fun `OpenTab activates existing tab instead of duplicating`() {
        val topics = reducer(ShellState(), ShellAction.OpenTab(TabType.TOPICS))
        val brokers = reducer(topics, ShellAction.OpenTab(TabType.BROKERS))
        val reopened = reducer(brokers, ShellAction.OpenTab(TabType.TOPICS))

        assertEquals(2, reopened.tabs.size)
        assertEquals(topics.tabs[0].id, reopened.activeTabId)
    }

    @Test
    fun `CloseTab auto-switches to previous tab when closing active`() {
        val topics = reducer(ShellState(), ShellAction.OpenTab(TabType.TOPICS))
        val brokers = reducer(topics, ShellAction.OpenTab(TabType.BROKERS))
        val closed = reducer(brokers, ShellAction.CloseTab(brokers.tabs[1].id))

        assertEquals(1, closed.tabs.size)
        assertEquals(topics.tabs[0].id, closed.activeTabId)
    }

    @Test
    fun `CloseTab falls forward when closing first tab`() {
        val topics = reducer(ShellState(), ShellAction.OpenTab(TabType.TOPICS))
        val brokers = reducer(topics, ShellAction.OpenTab(TabType.BROKERS))
        val selected = reducer(brokers, ShellAction.SwitchTab(topics.tabs[0].id))
        val closed = reducer(selected, ShellAction.CloseTab(topics.tabs[0].id))

        assertEquals(brokers.tabs[1].id, closed.activeTabId)
    }

    @Test
    fun `CloseTab on nonexistent id is no-op`() {
        val state = reducer(ShellState(), ShellAction.OpenTab(TabType.TOPICS))

        assertEquals(state, reducer(state, ShellAction.CloseTab("nonexistent")))
    }

    @Test
    fun `CloseOtherTabs keeps only the referenced tab`() {
        val state = openThreeTabs()
        val keepId = state.tabs[1].id
        val result = reducer(state, ShellAction.CloseOtherTabs(keepId))

        assertEquals(listOf(keepId), result.tabs.map { it.id })
        assertEquals(keepId, result.activeTabId)
    }

    @Test
    fun `open all tab types`() {
        val state =
            TabType.entries.fold(ShellState()) { current, type ->
                reducer(current, ShellAction.OpenTab(type))
            }

        assertEquals(TabType.entries.map { it.name.lowercase() }, state.tabs.map { it.id })
    }

    @Test
    fun `NavigateSidebar changes section without changing tabs`() {
        val state = reducer(ShellState(), ShellAction.OpenTab(TabType.TOPICS))
        val navigated = reducer(state, ShellAction.NavigateSidebar(SidebarSection.SETTINGS))

        assertEquals(state.tabs, navigated.tabs)
        assertEquals(SidebarSection.SETTINGS, navigated.sidebarSection)
    }

    @Test
    fun `OpenEntityTab keeps different entities and reactivates an existing one`() {
        val orders = reducer(ShellState(), ShellAction.OpenEntityTab(TabType.TOPIC_DETAIL, "orders", "orders"))
        val payments =
            reducer(orders, ShellAction.OpenEntityTab(TabType.TOPIC_DETAIL, "payments", "payments"))
        val reopened = reducer(payments, ShellAction.OpenEntityTab(TabType.TOPIC_DETAIL, "orders", "orders"))

        assertEquals(2, reopened.tabs.size)
        assertEquals(orders.tabs[0].id, reopened.activeTabId)
    }

    private fun openThreeTabs(): ShellState =
        listOf(TabType.TOPICS, TabType.BROKERS, TabType.CONSUMER_GROUPS).fold(ShellState()) { state, type ->
            reducer(state, ShellAction.OpenTab(type))
        }
}
