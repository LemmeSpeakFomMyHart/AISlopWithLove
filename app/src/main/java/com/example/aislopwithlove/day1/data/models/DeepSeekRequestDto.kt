package com.example.aislopwithlove.day1.data.models

import com.google.gson.annotations.SerializedName

data class DeepSeekRequestDto(
    @SerializedName("model") val deepseekModelType: String = "deepseek-chat",
    @SerializedName("messages") val messages: List<DeepSeekMessageDto>
)