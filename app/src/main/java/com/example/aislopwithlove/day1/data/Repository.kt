package com.example.aislopwithlove.day1.data

import com.example.aislopwithlove.day1.data.models.DeepSeekMessageDto
import com.example.aislopwithlove.day1.data.models.DeepSeekRequestDto
import com.example.aislopwithlove.day1.data.models.DeepSeekResponseDto
import com.example.aislopwithlove.day1.data.parsers.DeepSeekStreamParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        .create(DeepSeekApiService::class.java)

    private val streamParser = DeepSeekStreamParser()

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

    suspend fun sentStreamingRequest(
        text: String,
        onChunk: (String) -> Unit,
        onComplete: () -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            val responseBody = deepseekApiService.sendMessageAndGetStream(
                DeepSeekRequestDto(
                    messages = listOf(
                        DeepSeekMessageDto(
                            role = DeepSeekMessageDto.Role.USER,
                            text = text
                        )
                    ),
                    stream = true
                )
            )

            responseBody.source().use { source ->
                val reader = source.buffer

                while (!source.exhausted()) {
                    val line = reader.readUtf8Line() ?: continue

                    if (line.isNotEmpty()) {
                        streamParser.parseChunk(line)?.let { chunk ->
                            chunk.choices.firstOrNull()?.message?.text?.let { content ->
                                withContext(Dispatchers.Main) {
                                    onChunk(content)
                                }
                            }

                            if (chunk.choices.firstOrNull()?.finishReason != null) {
                                withContext(Dispatchers.Main) {
                                    onComplete()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}