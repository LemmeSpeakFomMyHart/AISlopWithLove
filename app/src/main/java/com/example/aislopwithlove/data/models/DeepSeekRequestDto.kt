package com.example.aislopwithlove.data.models

import com.google.gson.annotations.SerializedName

data class DeepSeekRequestDto(
    @SerializedName("model") val model: String = "deepseek-v4-flash",  // сменил, т.к. coder устарел
    @SerializedName("messages") val messages: List<DeepSeekMessageDto>,
    @SerializedName("stream") val stream: Boolean = false,
    @SerializedName("max_tokens") val maxTokens: Int? = null,
    @SerializedName("stop") val stop: List<String>? = null,
    @SerializedName("temperature") val temperature: Double? = null
)