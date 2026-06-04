// data.remote.dto.UploadedFileDto.kt
package com.example.a20260310.data.remote.dto

import com.google.gson.annotations.SerializedName

data class UploadedFileDto(
    @SerializedName("id")
    val id: Int,

    @SerializedName("meeting_id")
    val meetingId: Int,

    @SerializedName("original_name")
    val originalName: String,

    @SerializedName("saved_path")
    val savedPath: String,

    @SerializedName("file_type")
    val fileType: String,

    @SerializedName("mime_type")
    val mimeType: String? = null,

    @SerializedName("size_bytes")
    val sizeBytes: Long? = null,

    @SerializedName("created_at")
    val createdAt: String? = null
)