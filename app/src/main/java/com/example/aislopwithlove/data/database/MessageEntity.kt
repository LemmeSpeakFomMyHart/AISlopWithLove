package com.example.aislopwithlove.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.aislopwithlove.data.models.DeepSeekMessageDto

/**
 * Сущность сообщения для хранения в базе данных Room.
 *
 * @property id Уникальный идентификатор (автогенерация)
 * @property role Роль отправителя: "USER", "ASSISTANT", "SYSTEM"
 * @property content Текст сообщения
 * @property timestamp Время создания сообщения в миллисекундах
 * @property conversationId Идентификатор диалога (для поддержки множественных бесед)
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val conversationId: String = "default"
) {
    /**
     * Преобразует сущность базы данных в DTO для работы с API.
     *
     * @return DeepSeekMessageDto для отправки в API
     */
    fun toDto(): DeepSeekMessageDto = DeepSeekMessageDto(
        role = DeepSeekMessageDto.Role.valueOf(role),
        text = content
    )

    companion object {
        /**
         * Создаёт сущность базы данных из DTO.
         *
         * @param dto DTO сообщения
         * @param conversationId ID диалога
         * @return MessageEntity для сохранения в БД
         */
        fun fromDto(dto: DeepSeekMessageDto, conversationId: String = "default"): MessageEntity =
            MessageEntity(
                role = dto.role.name,
                content = dto.text,
                conversationId = conversationId
            )
    }
}