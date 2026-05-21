package com.example.a20260310.data.repository

import com.example.a20260310.data.auth.TokenManager
import com.example.a20260310.data.remote.ApiClient
import com.example.a20260310.data.remote.AuthApiService
import com.example.a20260310.data.remote.dto.UserCreateRequest

class AuthRepository(
    private val api: AuthApiService = ApiClient.authApi,
) {
    suspend fun signup(username: String, password: String) {
        api.signup(UserCreateRequest(username = username, password = password))
    }

    suspend fun login(username: String, password: String) {
        val tokenResponse = api.login(username = username, password = password)
        TokenManager.saveAccessToken(tokenResponse.accessToken)
    }

    fun logout() {
        TokenManager.clear()
    }

    fun isLoggedIn(): Boolean = TokenManager.isLoggedIn()
}