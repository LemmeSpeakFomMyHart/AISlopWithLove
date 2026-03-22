package com.example.aislopwithlove.day1.data.models

import com.google.gson.annotations.SerializedName

data class DeepSeekMessageDto(
    @SerializedName("role") val role: Role,
    @SerializedName("content") val text: String
) {
    enum class Role {
        @SerializedName("system")
        SYSTEM,

        @SerializedName("user")
        USER,

        @SerializedName("assistant")
        ASSISTANT,
    }
}