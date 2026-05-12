package com.example.a20260310.data.model

import com.example.a20260310.data.remote.dto.LlmSummaryPayload

import com.example.a20260310.data.remote.dto.ActionItemPayload

/**
 * 서버에서 내려오는 구조화된 회의 요약을 앱 도메인 레이어에서 다루기 위한 모델.
 * Retrofit 디코딩은 DTO 레이어가 담당하므로 어노테이션을 두지 않는다.
 */
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

/**
 * DTO -> 도메인 변환.
 * DTO의 owner/deadline은 빈 문자열로 내려올 수 있어 도메인에서는 null로 정규화한다.
 */
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