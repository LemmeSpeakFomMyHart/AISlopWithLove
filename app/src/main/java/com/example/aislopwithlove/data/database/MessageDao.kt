package com.example.aislopwithlove.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object для работы с сообщениями в базе данных.
 */
@Dao
interface MessageDao {

    /**
     * Сохраняет сообщение в базу данных.
     *
     * @param message Сообщение для сохранения
     */
    @Insert
    suspend fun insert(message: MessageEntity)

    /**
     * Возвращает Flow со всеми сообщениями диалога в хронологическом порядке.
     * Flow автоматически обновляется при изменениях в БД.
     *
     * @param conversationId ID диалога (по умолчанию "default")
     * @return Flow списка сообщений
     */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesFlow(conversationId: String = "default"): Flow<List<MessageEntity>>

    /**
     * Возвращает список всех сообщений диалога в хронологическом порядке (одноразовый запрос).
     *
     * @param conversationId ID диалога (по умолчанию "default")
     * @return Список сообщений
     */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessages(conversationId: String = "default"): List<MessageEntity>

    /**
     * Удаляет все сообщения указанного диалога.
     *
     * @param conversationId ID диалога (по умолчанию "default")
     */
    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearConversation(conversationId: String = "default")

    /**
     * Удаляет все сообщения из всех диалогов (полная очистка БД).
     */
    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}