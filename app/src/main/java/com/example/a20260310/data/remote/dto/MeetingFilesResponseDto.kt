// data.remote.dto.MeetingFilesResponseDto.kt
package com.example.a20260310.data.remote.dto

import com.google.gson.annotations.SerializedName

data class MeetingFilesResponseDto(
    @SerializedName("meeting_id")
    val meetingId: Int,

    @SerializedName("audio_files")
    val audioFiles: List<UploadedFileDto> = emptyList(),

    @SerializedName("image_files")
    val imageFiles: List<UploadedFileDto> = emptyList()
)