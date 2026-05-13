package com.example.a20260310.data.repository

import android.util.Log
import com.example.a20260310.data.remote.ApiClient
import com.example.a20260310.data.remote.MeetingApiService
import com.example.a20260310.data.remote.dto.ImageUploadResponseDto
import com.example.a20260310.data.remote.dto.MeetingCreateRequest
import com.example.a20260310.data.remote.dto.MeetingResponseDto
import com.example.a20260310.data.remote.dto.MeetingSummaryResponseDto
import com.example.a20260310.data.remote.dto.TranscriptResponseDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import com.example.a20260310.data.model.MeetingSummary
import com.example.a20260310.data.remote.mapper.toDomain

class MeetingRepository(
    private val api: MeetingApiService = ApiClient.meetingApi,
) {
    companion object {
        private const val TAG = "MeetingRepository"
    }

    suspend fun createMeeting(title: String, description: String?): MeetingResponseDto {
        return api.createMeeting(
            MeetingCreateRequest(
                title = title,
                description = description,
            )
        )
    }

    suspend fun uploadAudioFiles(meetingId: Int, files: List<File>): TranscriptResponseDto {
        require(files.isNotEmpty()) { "uploadAudioFiles requires at least one file" }
        val validFiles = files.filter { it.exists() && it.length() > 0L }
        require(validFiles.isNotEmpty()) { "uploadAudioFiles requires non-empty files" }

        val parts = validFiles.map { file ->
            val mime = audioMediaTypeForFile(file.name)
            Log.d(TAG, "uploadAudioFiles part name=files file=${file.name} mime=$mime")
            val body = file.asRequestBody(mime.toMediaTypeOrNull())
            MultipartBody.Part.createFormData("files", file.name, body)
        }

        return try {
            api.uploadAudioFiles(meetingId, parts)
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string()?.trim().orEmpty()
            Log.e(TAG, "uploadAudioFiles failed code=${e.code()} errorBody=$errorBody")
            throw e
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

        val parts = validFiles.map { file ->
            val mediaType = mediaTypeForUploadFile(file.name).toMediaTypeOrNull()
            val body = file.asRequestBody(mediaType)
            MultipartBody.Part.createFormData("files", file.name, body)
        }

        val imageTypeBody = imageType.toRequestBody("text/plain".toMediaType())
        return api.uploadImageFiles(meetingId, parts, imageTypeBody)
    }

    suspend fun getMeetingSummary(meetingId: Int): MeetingSummary {
        return api.getMeetingSummary(meetingId).toDomain()
    }

    private fun audioMediaTypeForFile(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "m4a", "mp4", "aac" -> "audio/mp4"
            else -> "application/octet-stream"
        }
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
}