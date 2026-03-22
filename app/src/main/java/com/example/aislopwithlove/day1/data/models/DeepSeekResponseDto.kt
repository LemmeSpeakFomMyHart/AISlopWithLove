package com.example.aislopwithlove.day1.data.models

import com.google.gson.annotations.SerializedName

data class DeepSeekResponseDto(
    @SerializedName("id") val id: String,
    @SerializedName("choices") val choices: List<Choice>
) {
    data class Choice(
        @SerializedName("delta") val message: DeepSeekMessageDto,
        @SerializedName("finish_reason") val finishReason: String
    )
}