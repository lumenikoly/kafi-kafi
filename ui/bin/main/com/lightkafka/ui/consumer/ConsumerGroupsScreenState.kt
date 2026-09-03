package com.lightkafka.ui.consumer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lightkafka.core.kafka.ConsumerGroupDetail
import com.lightkafka.core.kafka.ConsumerGroupSummary
import com.lightkafka.core.kafka.KafkaConsumerGroupService
import com.lightkafka.core.kafka.KafkaResult
import com.lightkafka.core.kafka.OffsetResetSpec
import com.lightkafka.ui.infra.formatKafkaError

internal class ConsumerGroupsScreenState(
    val service: KafkaConsumerGroupService,
) {
    var groups by mutableStateOf<List<ConsumerGroupSummary>>(emptyList())
    var selectedGroupId by mutableStateOf<String?>(null)
    var detail by mutableStateOf<ConsumerGroupDetail?>(null)
    var search by mutableStateOf("")
    var isLoading by mutableStateOf(true)
    var isDetailLoading by mutableStateOf(false)
    var listError by mutableStateOf<String?>(null)
    var detailError by mutableStateOf<String?>(null)
    var actionMessage by mutableStateOf<String?>(null)
    var actionIsError by mutableStateOf(false)
    var actionInProgress by mutableStateOf(false)
    var groupsRefreshKey by mutableStateOf(0)
    var detailRefreshKey by mutableStateOf(0)
    var resetDialogOpen by mutableStateOf(false)
    var deleteDialogOpen by mutableStateOf(false)

    suspend fun loadGroups() {
        isLoading = true
        listError = null
        when (val result = service.listGroups()) {
            is KafkaResult.Success -> {
                groups = result.value
                if (selectedGroupId !in groups.map(ConsumerGroupSummary::groupId)) {
                    selectedGroupId = groups.firstOrNull()?.groupId
                }
            }
            is KafkaResult.Failure -> listError = formatKafkaError(result.error)
        }
        isLoading = false
    }

    suspend fun loadDetail() {
        val groupId = selectedGroupId
        if (groupId == null) {
            detail = null
            detailError = null
            return
        }
        isDetailLoading = true
        detailError = null
        when (val result = service.describeGroup(groupId)) {
            is KafkaResult.Success -> {
                detail = result.value
                if (result.value == null) detailError = "Consumer group no longer exists"
            }
            is KafkaResult.Failure -> detailError = formatKafkaError(result.error)
        }
        isDetailLoading = false
    }

    fun selectGroup(groupId: String) {
        selectedGroupId = groupId
        actionMessage = null
    }

    suspend fun resetOffsets(
        groupId: String,
        topic: String,
        spec: OffsetResetSpec,
    ) {
        actionInProgress = true
        when (val result = service.resetOffsets(groupId, topic, spec)) {
            is KafkaResult.Success -> {
                actionMessage = "Offsets reset for $topic"
                actionIsError = false
                resetDialogOpen = false
                detailRefreshKey++
            }
            is KafkaResult.Failure -> {
                actionMessage = formatKafkaError(result.error)
                actionIsError = true
            }
        }
        actionInProgress = false
    }

    suspend fun deleteGroup(groupId: String) {
        actionInProgress = true
        when (val result = service.deleteGroup(groupId)) {
            is KafkaResult.Success -> {
                deleteDialogOpen = false
                selectedGroupId = null
                detail = null
                actionMessage = null
                groupsRefreshKey++
            }
            is KafkaResult.Failure -> {
                actionMessage = formatKafkaError(result.error)
                actionIsError = true
                deleteDialogOpen = false
            }
        }
        actionInProgress = false
    }
}
