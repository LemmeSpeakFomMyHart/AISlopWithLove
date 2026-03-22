package com.example.aislopwithlove.day1.data

import com.example.aislopwithlove.day1.data.models.DeepSeekRequestDto
import com.example.aislopwithlove.day1.data.models.DeepSeekResponseDto
import retrofit2.http.Body
import retrofit2.http.POST

interface DeepseekApiService {

    @POST("chat/completions")
    suspend fun sendMessage(@Body request: DeepSeekRequestDto): DeepSeekResponseDto
}