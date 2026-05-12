package com.example.a20260310.data.remote.dto

import com.example.a20260310.data.model.ActionItem
import com.google.gson.annotations.SerializedName

data class MeetingCreateRequest(
    @SerializedName("title")
    val title: String,

    @SerializedName("meeting_date")
    val meetingDate: String? = null,

    @SerializedName("meeting_time")
    val meetingTime: String? = null,

    @SerializedName("attendees")
    val attendees: List<String>? = null,

    @SerializedName("description")
    val description: String? = null,
)

data class MeetingResponseDto(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("meeting_date") val meetingDate: String? = null,
    @SerializedName("meeting_time") val meetingTime: String? = null,
    @SerializedName("attendees") val attendees: List<String> = emptyList(),
    @SerializedName("description") val description: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
)

data class TranscriptResponseDto(
    @SerializedName("id") val id: Int,
    @SerializedName("meeting_id") val meetingId: Int,
    @SerializedName("content") val content: String,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
)

data class ImageUploadResponseDto(
    @SerializedName("meeting_id") val meetingId: Int,
    @SerializedName("file_path") val filePath: String,
    @SerializedName("image_type") val imageType: String,
    @SerializedName("ocr_text") val ocrText: String? = null,
    @SerializedName("analysis_text") val analysisText: String? = null,
)

data class ActionItemPayload(
    @SerializedName("task") val task: String = "",
    @SerializedName("assignee") val assignee: String? = null,
    @SerializedName("due_date") val dueDate: String? = null,
)

data class LlmSummaryPayload(
    @SerializedName("summary") val summary: String = "",
    @SerializedName("decisions") val decisions: List<String> = emptyList(),
    @SerializedName("action_items") val actionItems: List<ActionItemPayload> = emptyList(),
    @SerializedName("error") val error: String? = null,
)

data class SummaryGenerateResponseDto(
    @SerializedName("id")
    val id: Int,

    @SerializedName("meeting_id")
    val meetingId: Int,

    @SerializedName("summary")
    val summary: LlmSummaryPayload,

    @SerializedName("created_at")
    val createdAt: String? = null,

    @SerializedName("updated_at")
    val updatedAt: String? = null,
)

data class SummaryUpdateRequestDto(
    @SerializedName("summary")
    val summary: String,

    @SerializedName("decisions")
    val decisions: List<String> = emptyList(),

    @SerializedName("action_items")
    val actionItems: List<ActionItemPayload> = emptyList(),
)

fun ActionItemPayload.toDomain(): ActionItem =
    ActionItem(
        task = task,
        owner = assignee,
        deadline = dueDate,
    )