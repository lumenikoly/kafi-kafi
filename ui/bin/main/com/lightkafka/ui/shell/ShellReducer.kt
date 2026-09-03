package com.lightkafka.ui.shell

/**
 * Pure reducer that evolves [ShellState] in response to [ShellAction]s.
 *
 * Rules:
 * - **OpenTab** — deduplicates by [TabType]; if the tab exists, activate it;
 *   otherwise append it and activate.
 * - **OpenEntityTab** — deduplicates by (type + entityId); if the tab exists,
 *   activate it; otherwise create it via [TabFactory] and activate.
 * - **CloseTab** — removes the tab; if it was active, activates the previous
 *   tab (or the next one, or null).
 * - **CloseOtherTabs** — keeps only the referenced tab and makes it active.
 * - **SwitchTab** — sets [ShellState.activeTabId] if the id exists in the tab list.
 * - **NavigateSidebar** — updates [ShellState.sidebarSection].
 */
object ShellReducer : (ShellState, ShellAction) -> ShellState {
    override fun invoke(
        state: ShellState,
        action: ShellAction,
    ): ShellState =
        when (action) {
            is ShellAction.OpenTab -> openTab(state, action.type)
            is ShellAction.OpenEntityTab -> openEntityTab(state, action)
            is ShellAction.CloseTab -> closeTab(state, action.tabId)
            is ShellAction.CloseOtherTabs -> closeOtherTabs(state, action.keepTabId)
            is ShellAction.SwitchTab -> switchTab(state, action.tabId)
            is ShellAction.NavigateSidebar -> state.copy(sidebarSection = action.section)
        }

    // ── internal helpers ──────────────────────────────────────────────

    private fun openTab(
        state: ShellState,
        type: TabType,
    ): ShellState {
        val existing = state.tabs.find { it.type == type }
        return if (existing != null) {
            state.copy(activeTabId = existing.id)
        } else {
            val newTab = TabFactory.create(type)
            state.copy(
                tabs = state.tabs + newTab,
                activeTabId = newTab.id,
            )
        }
    }

    private fun openEntityTab(
        state: ShellState,
        action: ShellAction.OpenEntityTab,
    ): ShellState {
        val newTab =
            when (action.type) {
                TabType.TOPIC_DETAIL -> TabFactory.createForTopic(action.entityId)
                else ->
                    TabInstance(
                        id = "${action.type.name.lowercase()}-${action.entityId}",
                        type = action.type,
                        title = action.title,
                    )
            }
        val existing = state.tabs.find { it.type == action.type && it.id == newTab.id }
        return if (existing != null) {
            state.copy(activeTabId = existing.id)
        } else {
            state.copy(
                tabs = state.tabs + newTab,
                activeTabId = newTab.id,
            )
        }
    }

    private fun closeTab(
        state: ShellState,
        tabId: String,
    ): ShellState {
        val idx = state.tabs.indexOfFirst { it.id == tabId }
        if (idx == -1) return state

        val remaining = state.tabs.filter { it.id != tabId }

        val newActiveId =
            if (state.activeTabId != tabId) {
                state.activeTabId
            } else {
                // Prefer the tab before, then after, then null
                val before = remaining.getOrNull(idx - 1)
                val after = remaining.getOrNull(idx)
                before?.id ?: after?.id
            }

        return state.copy(tabs = remaining, activeTabId = newActiveId)
    }

    private fun closeOtherTabs(
        state: ShellState,
        keepTabId: String,
    ): ShellState {
        val kept = state.tabs.find { it.id == keepTabId } ?: return state
        return state.copy(tabs = listOf(kept), activeTabId = kept.id)
    }

    private fun switchTab(
        state: ShellState,
        tabId: String,
    ): ShellState {
        if (state.tabs.none { it.id == tabId }) return state
        return state.copy(activeTabId = tabId)
    }
}
