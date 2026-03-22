package com.example.aislopwithlove.day1.data.parsers

import com.example.aislopwithlove.day1.data.models.DeepSeekResponseDto
import com.google.gson.Gson

class DeepSeekStreamParser {
    fun parseChunk(chunk: String): DeepSeekResponseDto? {
        // SSE формат: "data: {...}\n\n"
        if (!chunk.startsWith("data: ")) return null

        val jsonData = chunk.removePrefix("data: ").trim()

        // Проверяем на конец стрима
        if (jsonData == "[DONE]") return null

        return try {
            Gson().fromJson(jsonData, DeepSeekResponseDto::class.java)
        } catch (e: Exception) {
            null
        }
    }
}

