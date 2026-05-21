package com.example.a20260310.data.model

import com.example.a20260310.data.remote.dto.ActionItemPayload
import com.example.a20260310.data.remote.dto.LlmSummaryPayload
import com.example.a20260310.data.remote.dto.SummaryDetailResponseDto

data class MeetingSummary(
    val summary: String,
    val decisions: List<String> = emptyList(),
    val actionItems: List<ActionItem> = emptyList(),
    val error: String? = null,
)

data class ActionItem(
    val task: String,
    val owner: String? = null,
    val deadline: String? = null,
)

fun LlmSummaryPayload.toDomain(): MeetingSummary =
    MeetingSummary(
        summary = summary,
        decisions = decisions,
        actionItems = actionItems.map {
            ActionItem(
                task = it.task,
                owner = it.owner.trim().ifBlank { null },
                deadline = it.deadline.trim().ifBlank { null },
            )
        },
        error = error,
    )

fun SummaryDetailResponseDto.toDomain(): MeetingSummary =
    MeetingSummary(
        summary = summary.trim(),
        decisions = decisions.map { it.content.trim() }.filter { it.isNotEmpty() },
        actionItems = actionItems.map {
            ActionItem(
                task = it.task.trim(),
                owner = it.assignee?.trim()?.ifBlank { null },
                deadline = it.dueDate?.trim()?.ifBlank { null },
            )
        },
        error = null,
    )

fun MeetingSummary.toDto(): LlmSummaryPayload =
    LlmSummaryPayload(
        summary = summary,
        decisions = decisions,
        actionItems = actionItems.map {
            ActionItemPayload(
                task = it.task,
                owner = it.owner.orEmpty(),
                deadline = it.deadline.orEmpty(),
            )
        },
        error = error,
    )