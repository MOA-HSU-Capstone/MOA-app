package com.example.a20260310.data.remote

import com.example.a20260310.data.remote.dto.FolderCreateRequest
import com.example.a20260310.data.remote.dto.FolderDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface FolderApiService {
    @GET("folders")
    suspend fun getFolders(): List<FolderDto>

    @POST("folders")
    suspend fun createFolder(
        @Body body: FolderCreateRequest
    ): FolderDto

    @PATCH("folders/{folder_id}")
    suspend fun updateFolder(
        @Path("folder_id") folderId: Int,
        @Body body: FolderCreateRequest
    ): FolderDto

    @DELETE("folders/{folder_id}")
    suspend fun deleteFolder(
        @Path("folder_id") folderId: Int
    ): Map<String, Any>
}