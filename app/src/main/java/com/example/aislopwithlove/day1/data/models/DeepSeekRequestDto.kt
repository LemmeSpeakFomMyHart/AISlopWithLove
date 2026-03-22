package com.example.aislopwithlove.day1.data.models

import com.google.gson.annotations.SerializedName

data class DeepSeekRequestDto(
    @SerializedName("model") val deepseekModelType: String = "deepseek-coder",
    @SerializedName("messages") val messages: List<DeepSeekMessageDto>,
    @SerializedName("stream") val stream: Boolean = false
)