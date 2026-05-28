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

    /** STT·요약 등 장시간 응답 대기용 (백엔드 타임아웃 5분+ 여유) */
    private const val LONG_READ_TIMEOUT_SEC = 600L
    private const val LONG_WRITE_TIMEOUT_SEC = 600L
    private const val CONNECT_TIMEOUT_SEC = 120L

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
                HttpLoggingInterceptor.Level.BASIC
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
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(LONG_READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .writeTimeout(LONG_WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BuildConfig.MOA_API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
