package com.example.a20260310.data.repository

import com.example.a20260310.data.remote.ApiClient
import com.example.a20260310.data.remote.dto.FolderCreateRequest
import com.example.a20260310.data.remote.dto.FolderDto

class FolderRepository(
    private val api: com.example.a20260310.data.remote.FolderApiService = ApiClient.folderApi
) {
    suspend fun getFolders(): List<FolderDto> {
        return api.getFolders()
    }

    suspend fun createFolder(name: String): FolderDto {
        return api.createFolder(FolderCreateRequest(name.trim()))
    }
}