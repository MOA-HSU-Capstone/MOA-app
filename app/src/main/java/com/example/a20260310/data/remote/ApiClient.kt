package com.example.a20260310.data.remote

import com.example.a20260310.BuildConfig
import com.example.a20260310.data.auth.TokenManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import com.example.a20260310.data.remote.FolderApiService

object ApiClient {

    val meetingApi: MeetingApiService by lazy {
        retrofit.create(MeetingApiService::class.java)
    }

    val authApi: AuthApiService by lazy {
        retrofit.create(AuthApiService::class.java)
    }
    val folderApi: FolderApiService by lazy {
        retrofit.create(FolderApiService::class.java)
    }

    private val retrofit: Retrofit by lazy {
        val logging = HttpLoggingInterceptor().apply {
            redactHeader("Authorization")
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val token = TokenManager.getAccessToken()

                val request =
                    if (token.isNullOrBlank()) {
                        original
                    } else {
                        original.newBuilder()
                            .header("Authorization", "Bearer $token")
                            .build()
                    }

                chain.proceed(request)
            }
            .addInterceptor(logging)
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.MOA_API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}