package com.example.a20260310.data.remote.dto

import com.google.gson.annotations.SerializedName

data class MeetingCreateRequest(
    val title: String,
    @SerializedName("meeting_date") val meetingDate: String,
    @SerializedName("meeting_time") val meetingTime: String,
    @SerializedName("attendees") val attendees: List<String> = emptyList(),
    val description: String? = null,
)

data class MeetingResponseDto(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("meeting_date") val meetingDate: String? = null,
    @SerializedName("meeting_time") val meetingTime: String? = null,
    @SerializedName("attendees") val attendees: List<String>? = emptyList(),
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
    @SerializedName(value = "assignee", alternate = ["owner"]) val owner: String = "",
    @SerializedName(value = "due_date", alternate = ["deadline"]) val deadline: String = "",
)

data class LlmSummaryPayload(
    @SerializedName("summary") val summary: String = "",
    @SerializedName("decisions") val decisions: List<String> = emptyList(),
    @SerializedName("action_items") val actionItems: List<ActionItemPayload> = emptyList(),
    @SerializedName("error") val error: String? = null,
)

data class SummaryGenerateResponseDto(
    @SerializedName("meeting_id") val meetingId: Int,
    @SerializedName("summary") val summary: LlmSummaryPayload,
)

data class SummaryDetailResponseDto(
    @SerializedName("id") val id: Int,
    @SerializedName("meeting_id") val meetingId: Int,
    @SerializedName("summary") val summary: LlmSummaryPayload,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
)

data class SummaryUpdateRequest(
    @SerializedName("summary") val summary: String = "",
    @SerializedName("decisions") val decisions: List<String> = emptyList(),
    @SerializedName("action_items") val actionItems: List<ActionItemPayload> = emptyList(),
    @SerializedName("error") val error: String? = null,
)

data class DecisionDto(
    @SerializedName("id") val id: Int,
    @SerializedName("meeting_id") val meetingId: Int,
    @SerializedName("content") val content: String,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
)

data class ActionItemDto(
    @SerializedName("id") val id: Int,
    @SerializedName("meeting_id") val meetingId: Int,
    @SerializedName("task") val task: String,
    @SerializedName("assignee") val assignee: String? = null,
    @SerializedName("due_date") val dueDate: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
)

data class MeetingSummaryResponseDto(
    @SerializedName("id") val id: Int,
    @SerializedName("meeting_id") val meetingId: Int,
    @SerializedName("summary") val summary: String,
    @SerializedName("decisions") val decisions: List<DecisionDto> = emptyList(),
    @SerializedName("action_items") val actionItems: List<ActionItemDto> = emptyList(),
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
)

data class DecisionCreateRequestDto(
    @SerializedName("content") val content: String,
)

data class DecisionUpdateRequestDto(
    @SerializedName("content") val content: String,
)

data class ActionItemCreateRequestDto(
    @SerializedName("task") val task: String,
    @SerializedName("assignee") val assignee: String? = null,
    @SerializedName("due_date") val dueDate: String? = null,
)

data class ActionItemUpdateRequestDto(
    @SerializedName("task") val task: String,
    @SerializedName("assignee") val assignee: String? = null,
    @SerializedName("due_date") val dueDate: String? = null,
)
