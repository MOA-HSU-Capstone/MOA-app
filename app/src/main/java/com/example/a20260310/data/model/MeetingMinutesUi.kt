package com.example.a20260310.data.model

import com.example.a20260310.data.remote.dto.SummaryGenerateResponseDto

data class MeetingDraft(
    val title: String = "",
    val date: String = "",
    val time: String = "",
    val attendees: String = "",
) {
    fun toDescription(): String? = buildString {
        val dt = "${date.trim()} ${time.trim()}".trim()
        if (dt.isNotBlank()) appendLine("일시: $dt")
        if (attendees.isNotBlank()) appendLine("참석자: $attendees")
    }.trim().ifBlank { null }

    fun displayDatetime(): String {
        val dt = "${date.trim()} ${time.trim()}".trim()
        return dt.ifBlank { "—" }
    }

    /** 쉼표 구분 참석자 문자열 → 목록 (폼 Chip과 동일 규칙) */
    fun participantList(): List<String> =
        attendees.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

data class MinutesUiModel(
    val subject: String,
    val datetime: String,
    val attendees: String,
    /** 구조화 요약 (SummaryFragment에서 직접 접근) */
    val summary: MeetingSummary,
    /** LLM 요약 본문 (회의 요약 카드용, 호환 유지) */
    val summaryText: String,
    val agenda: String,
    val discussion: String,
    val note: String,
    val followup: String,
    val writerLabel: String,
)

object MinutesUiMapper {
    fun build(
        draft: MeetingDraft,
        transcript: String,
        response: SummaryGenerateResponseDto,
    ): MinutesUiModel {
        val payload = response.summary
        val summary = payload.toDomain()
        val agenda = when {
            payload.decisions.isNotEmpty() ->
                payload.decisions.joinToString("\n") { "• $it" }
            else -> "—"
        }
        val discussion = buildString {
            append(payload.summary.trim().ifBlank { "—" })
            val t = transcript.trim()
            if (t.isNotEmpty()) {
                append("\n\n──── 전사 ────\n")
                append(t)
            }
        }
        val note = payload.error?.trim()?.takeIf { it.isNotEmpty() } ?: "—"
        val followup = if (payload.actionItems.isNotEmpty()) {
            payload.actionItems.joinToString("\n") { item ->
                val owner = item.assignee?.trim()?.ifBlank { "미정" }
                val deadline = item.dueDate?.trim()?.ifBlank { "미정" }
                "• ${item.task.trim()} (담당: $owner / 마감: $deadline)"
            }
        } else {
            "—"
        }
        return MinutesUiModel(
            subject = draft.title.trim().ifBlank { "—" },
            datetime = draft.displayDatetime(),
            attendees = draft.attendees.trim().ifBlank { "—" },
            summary = summary,
            summaryText = payload.summary.trim().ifBlank { "—" },
            agenda = agenda,
            discussion = discussion,
            note = note,
            followup = followup,
            writerLabel = "작성자: MOA",
        )
    }
}
