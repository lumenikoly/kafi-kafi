package com.lightkafka.ui

import com.lightkafka.core.kafka.ConsumedMessage
import com.lightkafka.core.kafka.ConsumerGroupSummary
import com.lightkafka.core.kafka.ConsumerGroupDetail
import com.lightkafka.core.storage.ClusterProfile
import com.lightkafka.core.storage.ProducerTemplate
import com.lightkafka.core.storage.SendHistoryEntry

const val MAX_HISTORY_ENTRIES: Int = 25
const val MESSAGE_PREVIEW_LIMIT: Int = 120

data class MessageFilterState(
    val globalSearch: String = "",
    val keySearch: String = "",
    val valueSearch: String = "",
    val partitionFilter: String = "",
)

data class ProducerDraft(
    val topic: String = "",
    val partitionText: String = "",
    val key: String = "",
    val value: String = "",
    val headersText: String = "",
)

sealed interface SidebarTab {
    data object Topics : SidebarTab
    data object ConsumerGroups : SidebarTab
}

data class MainUiState(
    val profiles: List<ClusterProfile>,
    val activeProfileId: String?,
    val topics: List<String>,
    val topicSearchQuery: String = "",
    val selectedTopic: String? = null,
    val messages: List<ConsumedMessage> = emptyList(),
    val selectedMessageId: String? = null,
    val filters: MessageFilterState = MessageFilterState(),
    val isConsumerPaused: Boolean = false,
    val isProducerPanelOpen: Boolean = false,
    val isConnectionManagerOpen: Boolean = false,
    val isDiagnosticsOpen: Boolean = false,
    val exportStatus: String? = null,
    val producerDraft: ProducerDraft = ProducerDraft(),
    val templates: List<ProducerTemplate> = emptyList(),
    val history: List<SendHistoryEntry> = emptyList(),
    // Connection management
    val connectionStatuses: Map<String, ConnectionStatus> = emptyMap(),
    val profileToDelete: String? = null,
    // Topic management
    val topicToDelete: String? = null,
    val isTopicConfigDialogOpen: Boolean = false,
    // Sidebar navigation
    val selectedSidebarTab: SidebarTab = SidebarTab.Topics,
    // Consumer groups
    val consumerGroups: List<ConsumerGroupSummary> = emptyList(),
    val selectedConsumerGroupId: String? = null,
    val selectedConsumerGroupDetail: ConsumerGroupDetail? = null,
    val isLoadingConsumerGroups: Boolean = false,
    val consumerGroupsError: String? = null,
)

sealed interface MainUiAction {
    data class SetTopicSearch(
        val query: String,
    ) : MainUiAction

    data class SelectTopic(
        val topic: String?,
    ) : MainUiAction

    data class SetGlobalSearch(
        val query: String,
    ) : MainUiAction

    data class SetKeySearch(
        val query: String,
    ) : MainUiAction

    data class SetValueSearch(
        val query: String,
    ) : MainUiAction

    data class SetPartitionFilter(
        val partition: String,
    ) : MainUiAction

    data object TogglePause : MainUiAction

    data class SelectMessage(
        val messageId: String?,
    ) : MainUiAction

    data class SetProducerPanelOpen(
        val open: Boolean,
    ) : MainUiAction

    data class SetConnectionManagerOpen(
        val open: Boolean,
    ) : MainUiAction

    data class SetDiagnosticsOpen(
        val open: Boolean,
    ) : MainUiAction

    data class SetExportStatus(
        val status: String?,
    ) : MainUiAction

    data class AddTopic(
        val topic: String,
    ) : MainUiAction

    data class UpdateProducerTopic(
        val topic: String,
    ) : MainUiAction

    data class UpdateProducerPartition(
        val partitionText: String,
    ) : MainUiAction

    data class UpdateProducerKey(
        val key: String,
    ) : MainUiAction

    data class UpdateProducerValue(
        val value: String,
    ) : MainUiAction

    data class UpdateProducerHeaders(
        val headersText: String,
    ) : MainUiAction

    data class ApplyTemplate(
        val templateId: String,
    ) : MainUiAction

    data class AddHistoryEntry(
        val entry: SendHistoryEntry,
    ) : MainUiAction

    data class UpsertProfile(
        val profile: ClusterProfile,
    ) : MainUiAction

    data class DeleteProfile(
        val profileId: String,
    ) : MainUiAction

    data class SetActiveProfile(
        val profileId: String?,
    ) : MainUiAction

    data class AddMessages(
        val messages: List<ConsumedMessage>,
    ) : MainUiAction

    data class SetTopics(
        val topics: List<String>,
    ) : MainUiAction

    data object ClearMessages : MainUiAction

    // Connection status actions
    data class UpdateConnectionStatus(
        val profileId: String,
        val status: ConnectionStatus,
    ) : MainUiAction

    // Delete confirmation
    data class RequestDeleteProfile(
        val profileId: String?,
    ) : MainUiAction

    data class ConfirmDeleteProfile(
        val profileId: String,
    ) : MainUiAction

    data object CancelDeleteProfile : MainUiAction

    // Topic management actions
    data class RequestDeleteTopic(
        val topicName: String?,
    ) : MainUiAction

    data class ConfirmDeleteTopic(
        val topicName: String,
    ) : MainUiAction

    data object CancelDeleteTopic : MainUiAction

    data class RemoveTopic(
        val topicName: String,
    ) : MainUiAction

    data class SetTopicConfigDialogOpen(
        val open: Boolean,
    ) : MainUiAction

    // Sidebar navigation
    data class SelectSidebarTab(
        val tab: SidebarTab,
    ) : MainUiAction

    // Consumer groups actions
    data class SetConsumerGroups(
        val groups: List<ConsumerGroupSummary>,
    ) : MainUiAction

    data class SetConsumerGroupsLoading(
        val loading: Boolean,
    ) : MainUiAction

    data class SetConsumerGroupsError(
        val error: String?,
    ) : MainUiAction

    data class SelectConsumerGroup(
        val groupId: String?,
    ) : MainUiAction

    data class SetConsumerGroupDetail(
        val detail: ConsumerGroupDetail?,
    ) : MainUiAction

    data class RemoveConsumerGroup(
        val groupId: String,
    ) : MainUiAction
}

fun reduceMainUiState(
    state: MainUiState,
    action: MainUiAction,
): MainUiState =
    when (action) {
        is MainUiAction.SetTopicSearch,
        is MainUiAction.SelectTopic,
        is MainUiAction.SetGlobalSearch,
        is MainUiAction.SetKeySearch,
        is MainUiAction.SetValueSearch,
        is MainUiAction.SetPartitionFilter,
        MainUiAction.TogglePause,
        is MainUiAction.SelectMessage,
        is MainUiAction.SetExportStatus,
        is MainUiAction.AddTopic,
        is MainUiAction.AddMessages,
        is MainUiAction.SetTopics,
        MainUiAction.ClearMessages,
        -> reduceBrowserAction(state, action)

        is MainUiAction.SetProducerPanelOpen,
        is MainUiAction.UpdateProducerTopic,
        is MainUiAction.UpdateProducerPartition,
        is MainUiAction.UpdateProducerKey,
        is MainUiAction.UpdateProducerValue,
        is MainUiAction.UpdateProducerHeaders,
        is MainUiAction.ApplyTemplate,
        is MainUiAction.AddHistoryEntry,
        -> reduceProducerAction(state, action)

        is MainUiAction.SetConnectionManagerOpen,
        is MainUiAction.SetDiagnosticsOpen,
        is MainUiAction.UpsertProfile,
        is MainUiAction.DeleteProfile,
        is MainUiAction.SetActiveProfile,
        is MainUiAction.UpdateConnectionStatus,
        is MainUiAction.RequestDeleteProfile,
        is MainUiAction.ConfirmDeleteProfile,
        is MainUiAction.CancelDeleteProfile,
        -> reduceConnectionAction(state, action)

        is MainUiAction.RequestDeleteTopic,
        is MainUiAction.ConfirmDeleteTopic,
        is MainUiAction.CancelDeleteTopic,
        is MainUiAction.RemoveTopic,
        is MainUiAction.SetTopicConfigDialogOpen,
        -> reduceTopicAction(state, action)

        is MainUiAction.SelectSidebarTab,
        is MainUiAction.SetConsumerGroups,
        is MainUiAction.SetConsumerGroupsLoading,
        is MainUiAction.SetConsumerGroupsError,
        is MainUiAction.SelectConsumerGroup,
        is MainUiAction.SetConsumerGroupDetail,
        is MainUiAction.RemoveConsumerGroup,
        -> reduceConsumerGroupsAction(state, action)
    }

private fun reduceBrowserAction(
    state: MainUiState,
    action: MainUiAction,
): MainUiState =
    when (action) {
        is MainUiAction.SetTopicSearch -> state.copy(topicSearchQuery = action.query)
        is MainUiAction.SelectTopic -> state.copy(selectedTopic = action.topic)
        is MainUiAction.SetGlobalSearch ->
            state.copy(filters = state.filters.copy(globalSearch = action.query))

        is MainUiAction.SetKeySearch ->
            state.copy(filters = state.filters.copy(keySearch = action.query))

        is MainUiAction.SetValueSearch ->
            state.copy(filters = state.filters.copy(valueSearch = action.query))

        is MainUiAction.SetPartitionFilter ->
            state.copy(filters = state.filters.copy(partitionFilter = action.partition))

        MainUiAction.TogglePause -> state.copy(isConsumerPaused = !state.isConsumerPaused)
        is MainUiAction.SelectMessage -> state.copy(selectedMessageId = action.messageId)
        is MainUiAction.SetExportStatus -> state.copy(exportStatus = action.status)
        is MainUiAction.AddTopic -> {
            val topicName = action.topic.trim()
            if (topicName.isEmpty() || state.topics.contains(topicName)) {
                state
            } else {
                state.copy(topics = (state.topics + topicName).sorted())
            }
        }
        is MainUiAction.AddMessages -> {
            val seenIds = state.messages.mapTo(HashSet()) { messageId(it) }
            val newUnique =
                buildList {
                    action.messages.forEach { message ->
                        if (seenIds.add(messageId(message))) {
                            add(message)
                        }
                    }
                }
            val newTopics = newUnique.map { it.topic }.toSet() - state.topics.toSet()

            state.copy(
                messages = state.messages + newUnique,
                topics = (state.topics + newTopics).sorted(),
            )
        }
        is MainUiAction.SetTopics -> {
            val normalizedTopics =
                action.topics
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .distinct()
                    .sorted()
            state.copy(
                topics = normalizedTopics,
                selectedTopic = state.selectedTopic?.takeIf { normalizedTopics.contains(it) },
            )
        }
        MainUiAction.ClearMessages -> {
            state.copy(messages = emptyList(), selectedMessageId = null)
        }
        else -> state
    }

private fun reduceProducerAction(
    state: MainUiState,
    action: MainUiAction,
): MainUiState =
    when (action) {
        is MainUiAction.SetProducerPanelOpen -> state.copy(isProducerPanelOpen = action.open)
        is MainUiAction.UpdateProducerTopic ->
            state.copy(producerDraft = state.producerDraft.copy(topic = action.topic))

        is MainUiAction.UpdateProducerPartition ->
            state.copy(producerDraft = state.producerDraft.copy(partitionText = action.partitionText))

        is MainUiAction.UpdateProducerKey ->
            state.copy(producerDraft = state.producerDraft.copy(key = action.key))

        is MainUiAction.UpdateProducerValue ->
            state.copy(producerDraft = state.producerDraft.copy(value = action.value))

        is MainUiAction.UpdateProducerHeaders ->
            state.copy(producerDraft = state.producerDraft.copy(headersText = action.headersText))

        is MainUiAction.ApplyTemplate -> applyTemplate(state, action.templateId)
        is MainUiAction.AddHistoryEntry ->
            state.copy(history = listOf(action.entry) + state.history.take(MAX_HISTORY_ENTRIES - 1))

        else -> state
    }

private fun reduceConnectionAction(
    state: MainUiState,
    action: MainUiAction,
): MainUiState =
    when (action) {
        is MainUiAction.SetConnectionManagerOpen -> state.copy(isConnectionManagerOpen = action.open)
        is MainUiAction.SetDiagnosticsOpen -> state.copy(isDiagnosticsOpen = action.open)
        is MainUiAction.UpsertProfile -> upsertProfile(state, action.profile)
        is MainUiAction.DeleteProfile -> deleteProfile(state, action.profileId)
        is MainUiAction.SetActiveProfile -> state.copy(activeProfileId = action.profileId)
        is MainUiAction.UpdateConnectionStatus ->
            state.copy(
                connectionStatuses = state.connectionStatuses + (action.profileId to action.status),
            )
        is MainUiAction.RequestDeleteProfile -> state.copy(profileToDelete = action.profileId)
        is MainUiAction.ConfirmDeleteProfile -> {
            val newState = deleteProfile(state, action.profileId)
            newState.copy(profileToDelete = null)
        }
        is MainUiAction.CancelDeleteProfile -> state.copy(profileToDelete = null)
        else -> state
    }

private fun reduceTopicAction(
    state: MainUiState,
    action: MainUiAction,
): MainUiState =
    when (action) {
        is MainUiAction.RequestDeleteTopic -> state.copy(topicToDelete = action.topicName)
        is MainUiAction.ConfirmDeleteTopic -> state.copy(topicToDelete = null)
        is MainUiAction.CancelDeleteTopic -> state.copy(topicToDelete = null)
        is MainUiAction.RemoveTopic -> {
            state.copy(
                topics = state.topics.filterNot { it == action.topicName },
                selectedTopic = if (state.selectedTopic == action.topicName) null else state.selectedTopic,
            )
        }
        is MainUiAction.SetTopicConfigDialogOpen -> state.copy(isTopicConfigDialogOpen = action.open)
        else -> state
    }

private fun reduceConsumerGroupsAction(
    state: MainUiState,
    action: MainUiAction,
): MainUiState =
    when (action) {
        is MainUiAction.SelectSidebarTab -> state.copy(selectedSidebarTab = action.tab)
        is MainUiAction.SetConsumerGroups -> state.copy(
            consumerGroups = action.groups,
            isLoadingConsumerGroups = false,
            consumerGroupsError = null,
        )
        is MainUiAction.SetConsumerGroupsLoading -> state.copy(isLoadingConsumerGroups = action.loading)
        is MainUiAction.SetConsumerGroupsError -> state.copy(
            consumerGroupsError = action.error,
            isLoadingConsumerGroups = false,
        )
        is MainUiAction.SelectConsumerGroup -> state.copy(
            selectedConsumerGroupId = action.groupId,
        )
        is MainUiAction.SetConsumerGroupDetail -> state.copy(selectedConsumerGroupDetail = action.detail)
        is MainUiAction.RemoveConsumerGroup -> state.copy(
            consumerGroups = state.consumerGroups.filterNot { it.groupId == action.groupId },
            selectedConsumerGroupId = if (state.selectedConsumerGroupId == action.groupId) null else state.selectedConsumerGroupId,
            selectedConsumerGroupDetail = if (state.selectedConsumerGroupId == action.groupId) null else state.selectedConsumerGroupDetail,
        )
        else -> state
    }

private fun applyTemplate(
    state: MainUiState,
    templateId: String,
): MainUiState {
    val template = state.templates.firstOrNull { it.id == templateId } ?: return state
    val headersText = template.headers.entries.joinToString("\n") { "${it.key}=${it.value}" }
    return state.copy(
        producerDraft =
            state.producerDraft.copy(
                topic = template.topic,
                partitionText = template.partition?.toString().orEmpty(),
                key = template.key.orEmpty(),
                value = template.value.orEmpty(),
                headersText = headersText,
            ),
    )
}

private fun upsertProfile(
    state: MainUiState,
    profile: ClusterProfile,
): MainUiState {
    val existing = state.profiles.indexOfFirst { it.id == profile.id }
    val profiles =
        if (existing >= 0) {
            state.profiles.toMutableList().apply {
                this[existing] = profile
            }
        } else {
            state.profiles + profile
        }
    val active = state.activeProfileId ?: profile.id
    return state.copy(profiles = profiles, activeProfileId = active)
}

private fun deleteProfile(
    state: MainUiState,
    profileId: String,
): MainUiState {
    val remaining = state.profiles.filterNot { it.id == profileId }
    val active =
        if (state.activeProfileId == profileId) {
            remaining.firstOrNull()?.id
        } else {
            state.activeProfileId
        }
    return state.copy(profiles = remaining, activeProfileId = active)
}

fun MainUiState.visibleTopics(): List<String> =
    topics.filter {
        topicSearchQuery.isBlank() || it.contains(topicSearchQuery.trim(), ignoreCase = true)
    }

fun MainUiState.filteredMessages(): List<ConsumedMessage> =
    messages.filter { message ->
        val topicMatch = selectedTopic == null || message.topic == selectedTopic
        if (!topicMatch) {
            return@filter false
        }

        val partitionMatch =
            filters.partitionFilter
                .trim()
                .toIntOrNull()
                ?.let { expected -> message.partition == expected }
                ?: true
        if (!partitionMatch) {
            return@filter false
        }

        val keyText = previewBytes(message.key)
        val valueText = previewBytes(message.value)
        val globalMatch =
            filters.globalSearch.isBlank() ||
                keyText.contains(filters.globalSearch.trim(), ignoreCase = true) ||
                valueText.contains(filters.globalSearch.trim(), ignoreCase = true)
        val keyMatch =
            filters.keySearch.isBlank() ||
                keyText.contains(filters.keySearch.trim(), ignoreCase = true)
        val valueMatch =
            filters.valueSearch.isBlank() ||
                valueText.contains(filters.valueSearch.trim(), ignoreCase = true)

        globalMatch && keyMatch && valueMatch
    }

fun MainUiState.getConnectionStatus(profileId: String): ConnectionStatus? = connectionStatuses[profileId]

fun MainUiState.activeProfile(): ClusterProfile? = profiles.firstOrNull { it.id == activeProfileId }
