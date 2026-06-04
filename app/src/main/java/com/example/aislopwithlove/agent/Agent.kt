package com.example.aislopwithlove.agent

import android.content.Context
import android.util.Log
import com.example.aislopwithlove.data.Repository
import com.example.aislopwithlove.data.database.AppDatabase
import com.example.aislopwithlove.data.database.MessageEntity
import com.example.aislopwithlove.data.models.DeepSeekMessageDto
import com.example.aislopwithlove.data.models.TokenUsage
import com.example.aislopwithlove.utils.TokenCounter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
 * Статистика токенов для диалога.
 *
 * @property historyTokens Токены во всей истории
 * @property lastPromptTokens Токены последнего запроса
 * @property lastResponseTokens Токены последнего ответа
 * @property totalCost Общая стоимость
 * @property contextUsagePercent Процент использования контекста
 * @property isNearLimit Превышает ли 90% лимита
 * @property contextLimit Максимальный размер контекстного окна модели
 */
data class TokenStats(
    val historyTokens: Int,
    val lastPromptTokens: Int,
    val lastResponseTokens: Int,
    val totalCost: Double,
    val contextUsagePercent: Double,
    val isNearLimit: Boolean,
    val contextLimit: Int
) {
    fun format(): String = buildString {
        append("📊 Статистика токенов:\n")
        append(
            "   История: ${TokenCounter.formatTokens(historyTokens)} / ${
                TokenCounter.formatTokens(
                    contextLimit
                )
            }\n"
        )
        append("   Последний запрос: ${TokenCounter.formatTokens(lastPromptTokens)}\n")
        append("   Последний ответ: ${TokenCounter.formatTokens(lastResponseTokens)}\n")
        append("   Общая стоимость: ${TokenCounter.formatCost(totalCost)}\n")
        append("   Контекст: ${String.format("%.1f", contextUsagePercent)}% ")
        if (isNearLimit) append("⚠️ ПРИБЛИЖАЕТСЯ К ЛИМИТУ!")
    }
}

/**
 * Агент для взаимодействия с LLM с поддержкой:
 * - сохранения контекста в БД (Room)
 * - восстановления истории после перезапуска
 * - потоковой генерации ответов
 * - множественных диалогов (через conversationId)
 * - динамического подсчёта токенов
 * - автоматического определения лимита контекста модели
 *
 * @param context Контекст приложения (для доступа к БД)
 * @param repository Репозиторий для API-вызовов
 * @param modelName Имя модели DeepSeek (по умолчанию deepseek-v4-flash)
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

    /** Поток состояния агента (для UI) */
    private val _state = MutableStateFlow<AgentState>(AgentState.Idle)
    val state: StateFlow<AgentState> = _state.asStateFlow()

    /** Реактивный поток истории диалога (для UI) */
    private val _history = MutableStateFlow<List<DeepSeekMessageDto>>(emptyList())
    val history: StateFlow<List<DeepSeekMessageDto>> = _history.asStateFlow()

    /** Реактивный поток статистики токенов (для UI) */
    private val _tokenStats = MutableStateFlow<TokenStats?>(null)
    val tokenStats: StateFlow<TokenStats?> = _tokenStats.asStateFlow()

    private val database = AppDatabase.getInstance(context)
    private val messageDao = database.messageDao()

    /** История диалога в памяти (синхронизируется с БД) */
    private val conversationHistory = mutableListOf<DeepSeekMessageDto>()

    /** Флаг, указывающий, что инициализация завершена */
    private var isInitialized = false

    /** Максимальный размер контекстного окна модели */
    private var contextLimit: Int = 1_000_000

    /** Накопленная стоимость */
    private var totalCost = 0.0

    init {
        CoroutineScope(Dispatchers.IO).launch {
            // Устанавливаем лимит контекста в зависимости от модели
            contextLimit = when {
                modelName.contains("v4-flash") || modelName.contains("v4-pro") -> 1_000_000
                modelName.contains("v3.2") -> 131_072
                modelName.contains("llama") -> 8_192  // ← добавляем
                else -> 1_000_000
            }
            Log.d("Agent", "Установлен лимит контекста для $modelName: ${contextLimit} токенов")

            // Загружаем историю из БД
            loadHistoryFromDatabase()

            // Добавляем systemPrompt, если его нет и он задан
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

            // Обновляем статистику после загрузки
            updateTokenStats(promptTokens = 0, responseTokens = 0)
            isInitialized = true
        }
    }

    /**
     * Загружает историю диалога из базы данных.
     * Вызывается при инициализации агента.
     */
    private suspend fun loadHistoryFromDatabase() {
        val messages = messageDao.getMessages(conversationId)
        conversationHistory.clear()
        conversationHistory.addAll(messages.map { it.toDto() })
        _history.value = conversationHistory.toList()
    }

    /**
     * Добавляет сообщение в историю и сохраняет его в БД.
     *
     * @param message Сообщение для добавления
     */
    private suspend fun addMessage(message: DeepSeekMessageDto) {
        conversationHistory.add(message)
        _history.value = conversationHistory.toList()
        val entity = MessageEntity.fromDto(message, conversationId)
        messageDao.insert(entity)

        // Обновляем статистику (без новых ответов)
        updateTokenStats(promptTokens = 0, responseTokens = 0)
    }

    /**
     * Обновляет статистику токенов.
     *
     * @param promptTokens Токены последнего запроса
     * @param responseTokens Токены последнего ответа
     */
    private fun updateTokenStats(promptTokens: Int, responseTokens: Int) {
        val historyTokens = TokenCounter.countTokensInMessages(conversationHistory)
        val usagePercent = (historyTokens.toDouble() / contextLimit) * 100

        _tokenStats.value = TokenStats(
            historyTokens = historyTokens,
            lastPromptTokens = promptTokens,
            lastResponseTokens = responseTokens,
            totalCost = totalCost,
            contextUsagePercent = usagePercent,
            isNearLimit = usagePercent > 90,
            contextLimit = contextLimit
        )
    }

    /**
     * Отправляет сообщение агенту и получает ответ.
     *
     * @param userMessage Текст сообщения пользователя
     * @param onChunk Колбэк, вызываемый при получении каждого чанка стрима
     * @param onComplete Колбэк, вызываемый при завершении с результатом
     */
    suspend fun sendMessage(
        userMessage: String,
        onChunk: (String) -> Unit = {},
        onComplete: (AgentResult) -> Unit = {}
    ) {
        // Ждём завершения инициализации
        while (!isInitialized) {
            delay(10)
        }

        _state.value = AgentState.Thinking

        // Считаем токены запроса ДО отправки
        val promptTokens = TokenCounter.countTokens(userMessage)

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

                    // Если API вернул точное количество токенов — используем его
                    val responseTokens =
                        usage?.completionTokens ?: TokenCounter.countTokens(fullResponse)

                    // Обновляем общую стоимость
                    totalCost += TokenCounter.calculateCost(promptTokens, responseTokens)

                    CoroutineScope(Dispatchers.IO).launch {
                        val assistantDto = DeepSeekMessageDto(
                            role = DeepSeekMessageDto.Role.ASSISTANT,
                            text = fullResponse
                        )
                        addMessage(assistantDto)

                        // Обновляем статистику с точными значениями
                        updateTokenStats(promptTokens, responseTokens)
                    }

                    val result = AgentResult(
                        success = true,
                        response = fullResponse,
                        tokenUsage = usage,
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
     * Очищает историю диалога (и в памяти, и в БД).
     * Если задан systemPrompt, он будет восстановлен.
     */
    suspend fun clearHistory() {
        conversationHistory.clear()
        _history.value = emptyList()
        messageDao.clearConversation(conversationId)
        _state.value = AgentState.Idle
        totalCost = 0.0
        updateTokenStats(promptTokens = 0, responseTokens = 0)

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