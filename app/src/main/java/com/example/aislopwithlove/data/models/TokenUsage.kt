package com.example.aislopwithlove.data.models

import com.google.gson.annotations.SerializedName

data class TokenUsage(
    @SerializedName("prompt_tokens") val promptTokens: Int,
    @SerializedName("completion_tokens") val completionTokens: Int,
    @SerializedName("total_tokens") val totalTokens: Int
)