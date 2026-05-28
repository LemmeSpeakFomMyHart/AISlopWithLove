package com.example.aislopwithlove.agent

import com.example.aislopwithlove.data.Repository
import com.example.aislopwithlove.data.models.DeepSeekMessageDto
import com.example.aislopwithlove.data.models.TokenUsage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Состояние агента в процессе обработки запроса
 */
sealed class AgentState {
    data object Idle : AgentState()
    data object Thinking : AgentState()
    data class Responding(val text: String) : AgentState()
    data class Error(val message: String) : AgentState()
}

/**
 * Результат выполнения запроса
 */
data class AgentResult(
    val success: Boolean,
    val response: String = "",
    val error: String? = null,
    val tokenUsage: TokenUsage? = null,
    val durationMs: Long = 0
)

/**
 * Простой агент для взаимодействия с LLM
 * 
 * Агент инкапсулирует в себе:
 * - логику отправки запроса
 * - управление состоянием
 * - обработку ошибок
 * - потоковую передачу ответа
 */
class Agent(
    private val repository: Repository,
    private val modelName: String = "deepseek-v4-flash"
) {
    
    // Текущее состояние агента (для UI)
    private val _state = MutableStateFlow<AgentState>(AgentState.Idle)
    val state: StateFlow<AgentState> = _state.asStateFlow()
    
    // История диалога (контекст)
    private val conversationHistory = mutableListOf<DeepSeekMessageDto>()
    
    /**
     * Отправить запрос агенту (с потоковым ответом)
     * 
     * @param userMessage текст запроса от пользователя
     * @param onChunk колбэк для каждого чанка ответа
     * @param onComplete колбэк при завершении
     */
    suspend fun sendMessage(
        userMessage: String,
        onChunk: (String) -> Unit,
        onComplete: (AgentResult) -> Unit
    ) {
        // Меняем состояние на "думаю"
        _state.value = AgentState.Thinking
        
        // Добавляем сообщение пользователя в историю
        conversationHistory.add(
            DeepSeekMessageDto(
                role = DeepSeekMessageDto.Role.USER,
                text = userMessage
            )
        )
        
        val startTime = System.currentTimeMillis()
        var fullResponse = ""
        var tokenUsage: TokenUsage? = null
        
        try {
            // Отправляем запрос через репозиторий
            repository.sendStreamingRequestWithContext(
                messages = conversationHistory.toList(), // передаём копию истории
                modelName = modelName,
                onChunk = { chunk ->
                    fullResponse += chunk
                    _state.value = AgentState.Responding(fullResponse)
                    onChunk(chunk)
                },
                onComplete = { usage ->
                    tokenUsage = usage
                    val duration = System.currentTimeMillis() - startTime
                    
                    // Добавляем ответ ассистента в историю
                    conversationHistory.add(
                        DeepSeekMessageDto(
                            role = DeepSeekMessageDto.Role.ASSISTANT,
                            text = fullResponse
                        )
                    )
                    
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
    
    /**
     * Очистить историю диалога
     */
    fun clearHistory() {
        conversationHistory.clear()
        _state.value = AgentState.Idle
    }
    
    /**
     * Получить текущую историю диалога
     */
    fun getHistory(): List<DeepSeekMessageDto> = conversationHistory.toList()
}