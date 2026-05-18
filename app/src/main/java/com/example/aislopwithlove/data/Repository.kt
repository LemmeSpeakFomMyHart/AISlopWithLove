package com.example.aislopwithlove.data

import com.example.aislopwithlove.data.models.DeepSeekMessageDto
import com.example.aislopwithlove.data.models.DeepSeekRequestDto
import com.example.aislopwithlove.data.models.DeepSeekResponseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

private const val BASE_URL = "https://api.deepseek.com"
private const val AUTHORIZATION_HEADER = "Authorization"
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

    suspend fun sendStreamingRequestWithControl(
        text: String,
        systemPrompt: String? = null,
        maxTokens: Int? = null,
        stopSequences: List<String>? = null,
        onChunk: (String) -> Unit,
        onComplete: () -> Unit
    ) {
        withContext(Dispatchers.IO) {
            val messages = buildList {
                systemPrompt?.let {
                    add(DeepSeekMessageDto(role = DeepSeekMessageDto.Role.SYSTEM, text = it))
                }
                add(DeepSeekMessageDto(role = DeepSeekMessageDto.Role.USER, text = text))
            }

            val response = deepseekApiService.sendMessageAndGetStream(
                DeepSeekRequestDto(
                    messages = messages,
                    stream = true,
                    maxTokens = maxTokens,
                    stop = stopSequences,
                    temperature = 0.7
                )
            )

            val reader = BufferedReader(InputStreamReader(response.byteStream(), Charset.forName("UTF-8")))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: continue

                if (currentLine.isNotEmpty() && currentLine.startsWith("data: ")) {
                    val jsonData = currentLine.removePrefix("data: ").trim()

                    if (jsonData == "[DONE]") {
                        withContext(Dispatchers.Main) {
                            onComplete()
                        }
                        break
                    }

                    try {
                        val gson = com.google.gson.Gson()
                        val responseDto = gson.fromJson(jsonData, DeepSeekResponseDto::class.java)
                        val content = responseDto.choices.firstOrNull()?.message?.text

                        if (!content.isNullOrEmpty()) {
                            // Очищаем от битых последовательностей, НО сохраняем \n \r \t
                            val cleanContent = content
                                .replace(Regex("[\\u0000-\\u0008\\u000B-\\u001F\\u007F-\\u009F]"), "") // убираем control символы, кроме \n \r \t
                                .replace(Regex("(?<=[а-яА-Яa-zA-Z])[\\p{C}]+(?=[а-яА-Яa-zA-Z])"), "") // убираем битые символы между буквами

                            withContext(Dispatchers.Main) {
                                onChunk(cleanContent)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            reader.close()
        }
    }
}