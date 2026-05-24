package com.example.aislopwithlove.data

import com.example.aislopwithlove.BuildConfig
import com.example.aislopwithlove.data.models.DeepSeekMessageDto
import com.example.aislopwithlove.data.models.DeepSeekRequestDto
import com.example.aislopwithlove.data.models.DeepSeekResponseDto
import com.example.aislopwithlove.data.models.TokenUsage
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

// Константы для разных API
private const val DEEPSEEK_BASE_URL = "https://api.deepseek.com"
private const val OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1/"
private const val AUTHORIZATION_HEADER = "Authorization"

// API ключи из BuildConfig (из secrets.properties)
private const val DEEPSEEK_API_KEY = BuildConfig.DEEPSEEK_API_KEY
private const val OPENROUTER_API_KEY = BuildConfig.OPENROUTER_API_KEY

// Заголовки для OpenRouter
private const val OPENROUTER_APP_TITLE = "AISlopWithLove"
private const val OPENROUTER_APP_URL = "https://github.com/LemmeSpeakFomMyHart/AISlopWithLove"

class Repository {

    // ========== HTTP КЛИЕНТЫ ==========

    private val deepSeekClient = OkHttpClient.Builder()
        .addNetworkInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .readTimeout(120, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header(AUTHORIZATION_HEADER, "Bearer $DEEPSEEK_API_KEY")
                .build()
            chain.proceed(request)
        }
        .build()

    private val openRouterClient = OkHttpClient.Builder()
        .addNetworkInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .readTimeout(120, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header(AUTHORIZATION_HEADER, "Bearer $OPENROUTER_API_KEY")
                .header("HTTP-Referer", OPENROUTER_APP_URL)
                .header("X-Title", OPENROUTER_APP_TITLE)
                .build()
            chain.proceed(request)
        }
        .build()

    // ========== API СЕРВИСЫ (один интерфейс, два экземпляра) ==========

    private val deepSeekApi = Retrofit.Builder()
        .baseUrl(DEEPSEEK_BASE_URL)
        .client(deepSeekClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(DeepSeekApiService::class.java)

    private val openRouterApi = Retrofit.Builder()
        .baseUrl(OPENROUTER_BASE_URL)
        .client(openRouterClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(DeepSeekApiService::class.java)

    // ========== ПУБЛИЧНЫЙ МЕТОД ДЛЯ СТРИМИНГА ==========

    suspend fun sendStreamingRequestWithModel(
        text: String,
        modelName: String,
        onChunk: (String) -> Unit,
        onComplete: (TokenUsage?) -> Unit,
        onError: (String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val (apiService, actualModelName) = getApiServiceAndModelName(modelName)

                val request = DeepSeekRequestDto(
                    model = actualModelName,
                    messages = listOf(
                        DeepSeekMessageDto(
                            role = DeepSeekMessageDto.Role.USER,
                            text = text
                        )
                    ),
                    stream = true,
                    temperature = 0.7
                )

                val response = apiService.sendMessageAndGetStream(request)
                val reader = BufferedReader(InputStreamReader(response.byteStream(), Charsets.UTF_8))
                var line: String?
                var finalUsage: TokenUsage? = null

                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line ?: continue

                    if (currentLine.isNotEmpty() && currentLine.startsWith("data: ")) {
                        val jsonData = currentLine.removePrefix("data: ").trim()

                        if (jsonData == "[DONE]") {
                            withContext(Dispatchers.Main) { onComplete(finalUsage) }
                            break
                        }

                        try {
                            val responseDto = Gson().fromJson(jsonData, DeepSeekResponseDto::class.java)

                            // Сохраняем usage, если он пришёл (в последнем чанке)
                            if (responseDto.usage != null) {
                                finalUsage = responseDto.usage
                            }

                            val content = responseDto.choices.firstOrNull()?.message?.text

                            if (!content.isNullOrEmpty()) {
                                val cleanContent = cleanText(content)
                                withContext(Dispatchers.Main) { onChunk(cleanContent) }
                            }
                        } catch (e: Exception) {
                            // Пропускаем битые чанки
                            e.printStackTrace()
                        }
                    }
                }
                reader.close()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Unknown error")
                }
            }
        }
    }

    // ========== ПРИВАТНЫЕ МЕТОДЫ ==========

    private fun getApiServiceAndModelName(modelName: String): Pair<DeepSeekApiService, String> {
        return when (modelName) {
            "deepseek/deepseek-v3.2" -> openRouterApi to modelName
            "deepseek-v4-flash" -> deepSeekApi to modelName
            "deepseek-v4-pro" -> deepSeekApi to modelName
            else -> deepSeekApi to modelName
        }
    }

    private fun cleanText(text: String): String {
        return text
            .replace(Regex("[\\u0000-\\u0008\\u000B-\\u001F\\u007F-\\u009F]"), "")
            .replace(Regex("(?<=[а-яА-Яa-zA-Z])[\\p{C}]+(?=[а-яА-Яa-zA-Z])"), "")
    }
}