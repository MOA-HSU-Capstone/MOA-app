package com.example.a20260310.data.remote

import com.example.a20260310.data.remote.dto.FolderCreateRequest
import com.example.a20260310.data.remote.dto.FolderDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface FolderApiService {
    @GET("folders")
    suspend fun getFolders(): List<FolderDto>

    @POST("folders")
    suspend fun createFolder(
        @Body body: FolderCreateRequest
    ): FolderDto
}