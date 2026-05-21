package com.example.a20260310.data.model

import com.example.a20260310.data.remote.dto.SummaryDetailResponseDto

data class MeetingDraft(
    val title: String = "",
    val date: String = "",
    val time: String = "",
    val attendees: String = "",
) {
    fun displayDatetime(): String {
        val dt = "${date.trim()} ${time.trim()}".trim()
        return dt.ifBlank { "—" }
    }

    fun participantList(): List<String> =
        attendees.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

data class MinutesUiModel(
    val subject: String,
    val datetime: String,
    val attendees: String,
    val summary: MeetingSummary,
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
        response: SummaryDetailResponseDto,
    ): MinutesUiModel {
        val summary = response.toDomain()

        val agenda =
            if (response.decisions.isNotEmpty()) {
                response.decisions.joinToString("\n") { decision ->
                    "• ${decision.content.trim()}"
                }
            } else {
                "—"
            }

        val discussion = buildString {
            append(response.summary.trim().ifBlank { "—" })
            val t = transcript.trim()
            if (t.isNotEmpty()) {
                append("\n\n──── 전사 ────\n")
                append(t)
            }
        }

        val note = "—"

        val followup =
            if (response.actionItems.isNotEmpty()) {
                response.actionItems.joinToString("\n") { item ->
                    val owner = item.assignee?.trim().orEmpty().ifBlank { "미정" }
                    val deadline = item.dueDate?.trim().orEmpty().ifBlank { "미정" }
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
            summaryText = response.summary.trim().ifBlank { "—" },
            agenda = agenda,
            discussion = discussion,
            note = note,
            followup = followup,
            writerLabel = "작성자: MOA",
        )
    }
}