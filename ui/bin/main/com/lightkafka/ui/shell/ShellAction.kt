package com.lightkafka.ui.shell

/**
 * All intents the shell Store can process.
 * Every action is a pure data object — no behaviour, no side-effects.
 */
sealed interface ShellAction {
    /** Open a tab of the given type. If it already exists, just activate it. */
    data class OpenTab(
        val type: TabType,
    ) : ShellAction

    /** Close the tab with the given id. */
    data class CloseTab(
        val tabId: String,
    ) : ShellAction

    /** Close all tabs except the one with [keepTabId]. */
    data class CloseOtherTabs(
        val keepTabId: String,
    ) : ShellAction

    /** Switch focus to the tab with the given id. */
    data class SwitchTab(
        val tabId: String,
    ) : ShellAction

    /** Navigate the sidebar to a different section. */
    data class NavigateSidebar(
        val section: SidebarSection,
    ) : ShellAction

    /**
     * Open a tab for a specific entity (e.g. a topic).
     * Deduplicates by (type + entityId) — re-opening the same entity activates
     * the existing tab.
     */
    data class OpenEntityTab(
        val type: TabType,
        val entityId: String,
        val title: String,
    ) : ShellAction
}
