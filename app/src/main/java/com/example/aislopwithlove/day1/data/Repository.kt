package com.example.aislopwithlove.day1.data

import com.example.aislopwithlove.day1.data.models.DeepSeekMessageDto
import com.example.aislopwithlove.day1.data.models.DeepSeekRequestDto
import com.example.aislopwithlove.day1.data.models.DeepSeekResponseDto
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private const val BASE_URL = "https://api.deepseek.com"
private const val AUTHORIZATION_HEADER = "Authorization"

//todo:add to secure place
private const val DEEPSEEK_API_KEY = "sk-3f83038e276c415bb3e6d8c2db99b7c4"

class Repository {

    private val okHttpClient = OkHttpClient.Builder()
        .addNetworkInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
                .newBuilder()
                .header(AUTHORIZATION_HEADER, "Bearer $DEEPSEEK_API_KEY")
                .build()
            chain.proceed(request)
        }
        .build()

    private val deepseekApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(DeepseekApiService::class.java)

    suspend fun sendRequest(text: String): DeepSeekResponseDto {
        return deepseekApiService.sendMessage(
            DeepSeekRequestDto(
                messages = listOf(
                    DeepSeekMessageDto(
                        role = DeepSeekMessageDto.Role.USER,
                        text = text
                    )
                )
            )
        )
    }
}