package com.example.a20260310.data.remote

import com.example.a20260310.data.remote.dto.ActionItemCreateRequestDto
import com.example.a20260310.data.remote.dto.ActionItemDto
import com.example.a20260310.data.remote.dto.ActionItemUpdateRequestDto
import com.example.a20260310.data.remote.dto.DecisionCreateRequestDto
import com.example.a20260310.data.remote.dto.DecisionDto
import com.example.a20260310.data.remote.dto.DecisionUpdateRequestDto
import com.example.a20260310.data.remote.dto.ImageUploadResponseDto
import com.example.a20260310.data.remote.dto.MeetingCreateRequest
import com.example.a20260310.data.remote.dto.MeetingFilesResponse
import com.example.a20260310.data.remote.dto.MeetingFilesResponseDto
import com.example.a20260310.data.remote.dto.MeetingResponseDto
import com.example.a20260310.data.remote.dto.SummaryDetailResponseDto
import com.example.a20260310.data.remote.dto.SummaryGenerateResponseDto
import com.example.a20260310.data.remote.dto.SummaryUpdateRequest
import com.example.a20260310.data.remote.dto.TranscriptResponseDto
import com.google.gson.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface MeetingApiService {

    @POST("meetings")
    suspend fun createMeeting(
        @Body request: MeetingCreateRequest,
    ): MeetingResponseDto

    @GET("meetings")
    suspend fun getMeetings(): List<MeetingResponseDto>

    @GET("meetings/{meetingId}")
    suspend fun getMeeting(
        @Path("meetingId") meetingId: Int,
    ): MeetingResponseDto

    @PATCH("meetings/{meetingId}")
    suspend fun updateMeeting(
        @Path("meetingId") meetingId: Int,
        @Body body: JsonObject,
    ): MeetingResponseDto

    @Multipart
    @POST("upload/audio/{meeting_id}")
    suspend fun uploadAudioFiles(
        @Path("meeting_id") meetingId: Int,
        @Part files: List<MultipartBody.Part>,
    ): TranscriptResponseDto

    @Multipart
    @POST("upload/image/{meeting_id}")
    suspend fun uploadImageFiles(
        @Path("meeting_id") meetingId: Int,
        @Part files: List<MultipartBody.Part>,
        @Part("image_type") imageType: RequestBody,
    ): List<ImageUploadResponseDto>

    @POST("meetings/{meetingId}/summary")
    suspend fun generateSummary(
        @Path("meetingId") meetingId: Int,
    ): SummaryGenerateResponseDto

    @GET("meetings/{meetingId}/summary")
    suspend fun getSummary(
        @Path("meetingId") meetingId: Int,
    ): SummaryDetailResponseDto

    @PATCH("meetings/{meetingId}/summary")
    suspend fun updateSummary(
        @Path("meetingId") meetingId: Int,
        @Body body: SummaryUpdateRequest,
    ): SummaryDetailResponseDto

    @DELETE("meetings/{meetingId}")
    suspend fun deleteMeeting(
        @Path("meetingId") meetingId: Int,
    ): Response<Unit>

    @POST("meetings/{meeting_id}/decisions")
    suspend fun createDecision(
        @Path("meeting_id") meetingId: Int,
        @Body request: DecisionCreateRequestDto,
    ): DecisionDto

    @PATCH("decisions/{decision_id}")
    suspend fun updateDecision(
        @Path("decision_id") decisionId: Int,
        @Body request: DecisionUpdateRequestDto,
    ): DecisionDto

    @DELETE("decisions/{decision_id}")
    suspend fun deleteDecision(
        @Path("decision_id") decisionId: Int,
    ): Response<Unit>

    @POST("meetings/{meeting_id}/action-items")
    suspend fun createActionItem(
        @Path("meeting_id") meetingId: Int,
        @Body request: ActionItemCreateRequestDto,
    ): ActionItemDto

    @PATCH("action-items/{action_item_id}")
    suspend fun updateActionItem(
        @Path("action_item_id") actionItemId: Int,
        @Body request: ActionItemUpdateRequestDto,
    ): ActionItemDto

    @DELETE("action-items/{action_item_id}")
    suspend fun deleteActionItem(
        @Path("action_item_id") actionItemId: Int,
    ): Response<Unit>

    @GET("/meetings/{meeting_id}/files")
    suspend fun getMeetingFiles(
        @Path("meeting_id") meetingId: Int
    ): MeetingFilesResponseDto
}
