package com.example.aislopwithlove.day1.data

import com.example.aislopwithlove.day1.data.models.DeepSeekRequestDto
import com.example.aislopwithlove.day1.data.models.DeepSeekResponseDto
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Streaming

interface DeepSeekApiService {

    @POST("chat/completions")
    suspend fun sendMessage(@Body request: DeepSeekRequestDto): DeepSeekResponseDto

    @POST("chat/completions")
    @Streaming
    suspend fun sendMessageAndGetStream(@Body request: DeepSeekRequestDto): ResponseBody
}