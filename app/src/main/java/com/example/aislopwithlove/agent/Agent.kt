package com.example.aislopwithlove.agent

import android.content.Context
import com.example.aislopwithlove.data.Repository
import com.example.aislopwithlove.data.database.AppDatabase
import com.example.aislopwithlove.data.database.MessageEntity
import com.example.aislopwithlove.data.models.DeepSeekMessageDto
import com.example.aislopwithlove.data.models.TokenUsage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Состояние агента в процессе обработки запроса.
 */
sealed class AgentState {
    /** Агент ожидает команды */
    data object Idle : AgentState()

    /** Агент анализирует запрос (обычно перед вызовом API) */
    data object Thinking : AgentState()

    /** Агент генерирует ответ (стриминг от API) */
    data class Responding(val text: String) : AgentState()

    /** Произошла ошибка */
    data class Error(val message: String) : AgentState()
}

/**
 * Результат выполнения запроса агентом.
 *
 * @property success Успешен ли запрос
 * @property response Текст ответа (при успехе)
 * @property error Сообщение об ошибке (при неудаче)
 * @property tokenUsage Информация об использованных токенах
 * @property durationMs Время выполнения в миллисекундах
 */
data class AgentResult(
    val success: Boolean,
    val response: String = "",
    val error: String? = null,
    val tokenUsage: TokenUsage? = null,
    val durationMs: Long = 0
)

/**
 * Агент для взаимодействия с LLM с поддержкой:
 * - сохранения контекста в БД (Room)
 * - восстановления истории после перезапуска
 * - потоковой генерации ответов
 * - множественных диалогов (через conversationId)
 *
 * @param context Контекст приложения (для доступа к БД)
 * @param repository Репозиторий для API-вызовов
 * @param modelName Имя модели DeepSeek (по умолчанию V4-Flash)
 * @param systemPrompt Системный промпт (задаёт поведение агента)
 * @param conversationId Идентификатор диалога (по умолчанию "default")
 */
class Agent(
    private val context: Context,
    private val repository: Repository,
    private val modelName: String = "deepseek-v4-flash",
    private val systemPrompt: String? = null,
    private val conversationId: String = "default"
) {

    private val _state = MutableStateFlow<AgentState>(AgentState.Idle)
    val state: StateFlow<AgentState> = _state.asStateFlow()

    private val _history = MutableStateFlow<List<DeepSeekMessageDto>>(emptyList())
    val history: StateFlow<List<DeepSeekMessageDto>> = _history.asStateFlow()

    private val database = AppDatabase.getInstance(context)
    private val messageDao = database.messageDao()

    private val conversationHistory = mutableListOf<DeepSeekMessageDto>()

    // Флаг, указывающий, что инициализация завершена
    private var isInitialized = false

    init {
        // Загружаем историю синхронно в корутине и только потом добавляем systemPrompt
        CoroutineScope(Dispatchers.IO).launch {
            loadHistoryFromDatabase()

            // Теперь, когда история загружена, проверяем и добавляем systemPrompt
            systemPrompt?.let { prompt ->
                if (conversationHistory.none { it.role == DeepSeekMessageDto.Role.SYSTEM }) {
                    addMessage(
                        DeepSeekMessageDto(
                            role = DeepSeekMessageDto.Role.SYSTEM,
                            text = prompt
                        )
                    )
                }
            }
            isInitialized = true
        }
    }

    private suspend fun loadHistoryFromDatabase() {
        val messages = messageDao.getMessages(conversationId)
        conversationHistory.clear()
        conversationHistory.addAll(messages.map { it.toDto() })
        _history.value = conversationHistory.toList()
    }

    private suspend fun addMessage(message: DeepSeekMessageDto) {
        conversationHistory.add(message)
        _history.value = conversationHistory.toList()
        val entity = MessageEntity.fromDto(message, conversationId)
        messageDao.insert(entity)
    }

    suspend fun sendMessage(
        userMessage: String,
        onChunk: (String) -> Unit = {},
        onComplete: (AgentResult) -> Unit = {}
    ) {
        // Ждём завершения инициализации
        while (!isInitialized) {
            kotlinx.coroutines.delay(10)
        }

        _state.value = AgentState.Thinking

        val userDto = DeepSeekMessageDto(
            role = DeepSeekMessageDto.Role.USER,
            text = userMessage
        )
        addMessage(userDto)

        val startTime = System.currentTimeMillis()
        var fullResponse = ""
        var tokenUsage: TokenUsage? = null

        try {
            repository.sendStreamingRequestWithContext(
                messages = conversationHistory.toList(),
                modelName = modelName,
                onChunk = { chunk ->
                    fullResponse += chunk
                    _state.value = AgentState.Responding(fullResponse)
                    onChunk(chunk)
                },
                onComplete = { usage ->
                    tokenUsage = usage
                    val duration = System.currentTimeMillis() - startTime

                    CoroutineScope(Dispatchers.IO).launch {
                        val assistantDto = DeepSeekMessageDto(
                            role = DeepSeekMessageDto.Role.ASSISTANT,
                            text = fullResponse
                        )
                        addMessage(assistantDto)
                    }

                    val result = AgentResult(
                        success = true,
                        response = fullResponse,
                        tokenUsage = tokenUsage,
                        durationMs = duration
                    )

                    _state.value = AgentState.Idle
                    onComplete(result)
                },
                onError = { error ->
                    val result = AgentResult(
                        success = false,
                        error = error,
                        durationMs = System.currentTimeMillis() - startTime
                    )
                    _state.value = AgentState.Error(error)
                    onComplete(result)
                }
            )
        } catch (e: Exception) {
            val result = AgentResult(
                success = false,
                error = e.message ?: "Unknown error",
                durationMs = System.currentTimeMillis() - startTime
            )
            _state.value = AgentState.Error(e.message ?: "Unknown error")
            onComplete(result)
        }
    }

    suspend fun clearHistory() {
        conversationHistory.clear()
        _history.value = emptyList()
        messageDao.clearConversation(conversationId)
        _state.value = AgentState.Idle

        systemPrompt?.let { prompt ->
            addMessage(
                DeepSeekMessageDto(
                    role = DeepSeekMessageDto.Role.SYSTEM,
                    text = prompt
                )
            )
        }
    }
}