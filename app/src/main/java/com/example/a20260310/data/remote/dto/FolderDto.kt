package com.example.a20260310.data.remote.dto

import com.google.gson.annotations.SerializedName

data class FolderDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
)

data class FolderCreateRequest(
    @SerializedName("name") val name: String,
)