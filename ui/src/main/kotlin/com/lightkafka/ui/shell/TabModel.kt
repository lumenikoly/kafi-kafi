package com.lightkafka.ui.shell

/** The kinds of tabs the shell supports. */
enum class TabType {
    TOPICS,
    BROKERS,
    CONSUMER_GROUPS,
    SETTINGS,
    CONNECTIONS,
    CLUSTER_OVERVIEW,
    TOPIC_DETAIL,
    CREATE_TOPIC,
}

/**
 * A single open tab in the workspace.
 *
 * @param id     stable unique identifier (used for deduplication and selection)
 * @param type   determines which workspace content renders
 * @param title  displayed in the tab bar
 */
data class TabInstance(
    val id: String,
    val type: TabType,
    val title: String,
)

/** Helper to generate stable IDs and titles from a [TabType]. */
object TabFactory {
    fun create(type: TabType): TabInstance =
        TabInstance(
            id = type.name.lowercase(),
            type = type,
            title =
                type.name
                    .replace('_', ' ')
                    .lowercase()
                    .replaceFirstChar { it.uppercase() },
        )

    /** Create a [TOPIC_DETAIL][TabType.TOPIC_DETAIL] tab for the given topic name. */
    fun createForTopic(name: String): TabInstance =
        TabInstance(
            id = "topic-detail-$name",
            type = TabType.TOPIC_DETAIL,
            title = name,
        )
}
