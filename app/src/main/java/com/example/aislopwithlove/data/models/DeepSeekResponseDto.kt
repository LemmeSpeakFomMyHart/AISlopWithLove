package com.example.aislopwithlove.data.models

import com.google.gson.annotations.SerializedName

data class DeepSeekResponseDto(
    @SerializedName("id") val id: String,
    @SerializedName("choices") val choices: List<Choice>,
    @SerializedName("usage") val usage: TokenUsage? = null  // ← добавляем
) {
    data class Choice(
        @SerializedName("delta") val message: DeepSeekMessageDto,
        @SerializedName("finish_reason") val finishReason: String
    )
}