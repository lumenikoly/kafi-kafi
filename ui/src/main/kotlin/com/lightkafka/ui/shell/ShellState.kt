package com.lightkafka.ui.shell

/**
 * Immutable state for the application shell.
 *
 * Owned by a single [Store] and evolved exclusively through [ShellReducer].
 */
data class ShellState(
    val tabs: List<TabInstance> = emptyList(),
    val activeTabId: String? = null,
    val sidebarSection: SidebarSection = SidebarSection.TOPICS,
)

/** Sidebar navigation sections. */
enum class SidebarSection {
    TOPICS,
    BROKERS,
    CONSUMER_GROUPS,
    CONNECTIONS,
    SETTINGS,
}
