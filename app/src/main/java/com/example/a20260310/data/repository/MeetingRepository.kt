package com.example.a20260310.data.repository

import android.util.Log
import com.example.a20260310.data.remote.ApiClient
import com.example.a20260310.data.remote.MeetingApiService
import com.example.a20260310.data.remote.dto.ActionItemCreateRequestDto
import com.example.a20260310.data.remote.dto.ActionItemDto
import com.example.a20260310.data.remote.dto.ActionItemUpdateRequestDto
import com.example.a20260310.data.remote.dto.DecisionCreateRequestDto
import com.example.a20260310.data.remote.dto.DecisionDto
import com.example.a20260310.data.remote.dto.DecisionUpdateRequestDto
import com.example.a20260310.data.remote.dto.ImageUploadResponseDto
import com.example.a20260310.data.remote.dto.MeetingCreateRequest
import com.example.a20260310.data.remote.dto.MeetingResponseDto
import com.example.a20260310.data.remote.dto.SummaryDetailResponseDto
import com.example.a20260310.data.remote.dto.SummaryGenerateResponseDto
import com.example.a20260310.data.remote.dto.SummaryUpdateRequest
import com.example.a20260310.data.remote.dto.TranscriptResponseDto
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.File

class MeetingRepository(
    private val api: MeetingApiService = ApiClient.meetingApi,
) {
    companion object {
        private const val TAG = "MeetingRepository"
    }

    suspend fun createMeeting(
        title: String,
        meetingDate: String,
        meetingTime: String,
        attendees: List<String>,
        description: String? = null,
        folderId: Int? = null,
    ): MeetingResponseDto {
        val normalizedAttendees =
            attendees.map { it.trim() }
                .filter { it.isNotEmpty() }

        return api.createMeeting(
            MeetingCreateRequest(
                title = title.trim(),
                folderId = folderId,
                meetingDate = meetingDate.trim(),
                meetingTime = meetingTime.trim(),
                attendees = normalizedAttendees,
                description = description?.trim()?.takeIf { it.isNotEmpty() },
            )
        )
    }

    suspend fun getMeetings(): List<MeetingResponseDto> {
        return api.getMeetings()
    }

    suspend fun getMeeting(meetingId: Int): MeetingResponseDto {
        return api.getMeeting(meetingId)
    }

    suspend fun updateMeeting(
        meetingId: Int,
        title: String,
        meetingDate: String,
        meetingTime: String,
        attendees: List<String>,
    ): MeetingResponseDto {
        val trimmedTitle = title.trim()
        require(trimmedTitle.isNotEmpty()) { "제목은 비울 수 없습니다." }

        val normalizedAttendees =
            attendees.map { it.trim() }
                .filter { it.isNotEmpty() }

        val attendeeArray = JsonArray().apply {
            normalizedAttendees.forEach { add(it) }
        }

        val body = JsonObject()
        body.addProperty("title", trimmedTitle)
        body.addProperty("meeting_date", meetingDate.trim())
        body.addProperty("meeting_time", meetingTime.trim())
        body.add("attendees", attendeeArray)

        return api.updateMeeting(meetingId, body)
    }

    suspend fun uploadAudioFiles(meetingId: Int, files: List<File>): TranscriptResponseDto {
        require(files.isNotEmpty()) { "uploadAudioFiles requires at least one file" }
        val validFiles = files.filter { it.exists() && it.length() > 0L }
        require(validFiles.isNotEmpty()) { "uploadAudioFiles requires non-empty files" }

        val parts =
            validFiles.map { file ->
                val mime = audioMediaTypeForFile(file.name)
                Log.d(TAG, "uploadAudioFiles part name=files file=${file.name} mime=$mime")
                val body = file.asRequestBody(mime.toMediaTypeOrNull())
                MultipartBody.Part.createFormData("files", file.name, body)
            }

        return try {
            api.uploadAudioFiles(meetingId, parts)
        } catch (e: HttpException) {
            Log.e(TAG, "uploadAudioFiles failed code=${e.code()} path=/upload/audio/$meetingId")
            throw e
        }
    }

    private fun audioMediaTypeForFile(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "m4a", "mp4", "aac" -> "audio/mp4"
            else -> "application/octet-stream"
        }
    }

    suspend fun uploadImageFiles(
        meetingId: Int,
        files: List<File>,
        imageType: String = "image",
    ): List<ImageUploadResponseDto> {
        require(files.isNotEmpty()) { "uploadImageFiles requires at least one file" }
        val validFiles = files.filter { it.exists() && it.length() > 0L }
        require(validFiles.isNotEmpty()) { "uploadImageFiles requires non-empty files" }

        val parts =
            validFiles.map { file ->
                val mediaType = mediaTypeForUploadFile(file.name).toMediaTypeOrNull()
                val body = file.asRequestBody(mediaType)
                MultipartBody.Part.createFormData("files", file.name, body)
            }

        val imageTypeBody = imageType.toRequestBody("text/plain".toMediaType())
        return api.uploadImageFiles(meetingId, parts, imageTypeBody)
    }

    private fun mediaTypeForUploadFile(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
    }

    suspend fun generateSummary(meetingId: Int): SummaryGenerateResponseDto {
        return api.generateSummary(meetingId)
    }

    suspend fun getSummary(meetingId: Int): SummaryDetailResponseDto {
        return api.getSummary(meetingId)
    }

    suspend fun updateSummary(
        meetingId: Int,
        request: com.example.a20260310.data.remote.dto.SummaryUpdateRequest,
    ): SummaryDetailResponseDto {
        return api.updateSummary(meetingId, request)
    }

    suspend fun deleteMeeting(meetingId: Int): Response<Unit> {
        return api.deleteMeeting(meetingId)
    }

    suspend fun createDecision(meetingId: Int, content: String): DecisionDto {
        return api.createDecision(meetingId, DecisionCreateRequestDto(content = content))
    }

    suspend fun updateDecision(decisionId: Int, content: String): DecisionDto {
        return api.updateDecision(decisionId, DecisionUpdateRequestDto(content = content))
    }

    suspend fun deleteDecision(decisionId: Int): Response<Unit> {
        return api.deleteDecision(decisionId)
    }

    suspend fun createActionItem(
        meetingId: Int,
        task: String,
        assignee: String?,
        dueDate: String?,
    ): ActionItemDto {
        return api.createActionItem(
            meetingId,
            ActionItemCreateRequestDto(task = task, assignee = assignee, dueDate = dueDate),
        )
    }

    suspend fun updateActionItem(
        actionItemId: Int,
        task: String,
        assignee: String?,
        dueDate: String?,
    ): ActionItemDto {
        return api.updateActionItem(
            actionItemId,
            ActionItemUpdateRequestDto(task = task, assignee = assignee, dueDate = dueDate),
        )
    }

    suspend fun deleteActionItem(actionItemId: Int): Response<Unit> {
        return api.deleteActionItem(actionItemId)
    }
}