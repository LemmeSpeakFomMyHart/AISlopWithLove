package com.example.aislopwithlove

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.aislopwithlove.data.Repository
import com.example.aislopwithlove.data.models.DeepSeekMessageDto
import com.example.aislopwithlove.theme.AISlopWithLoveTheme
import kotlinx.coroutines.launch

enum class ReasoningMode {
    DIRECT, STEP_BY_STEP, SELF_PROMPT, EXPERT_GROUP
}

class MainActivity : ComponentActivity() {

    private val repository by lazy { Repository() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AISlopWithLoveTheme {
                ChatScreen()
            }
        }
    }

    // ========== UI COMPONENTS ==========

    @Composable
    private fun ChatScreen() {
        var inputText by remember { mutableStateOf(DEFAULT_TASK) }
        var isInputEnabled by remember { mutableStateOf(true) }
        var selectedMode by remember { mutableStateOf(ReasoningMode.DIRECT) }
        val messages = remember { mutableStateListOf<DeepSeekMessageDto>() }
        var streamingIndex by remember { mutableIntStateOf(-1) }

        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TaskInputField(
                    text = inputText,
                    onTextChange = { inputText = it },
                    enabled = isInputEnabled
                )

                ModeSelector(
                    selectedMode = selectedMode,
                    onModeSelect = { selectedMode = it }
                )

                SendButton(
                    enabled = isInputEnabled && inputText.isNotBlank(),
                    onClick = {
                        val currentText = inputText
                        val currentMode = selectedMode

                        messages.add(
                            DeepSeekMessageDto(
                                role = DeepSeekMessageDto.Role.USER,
                                text = currentText
                            )
                        )

                        val assistantIndex = messages.size
                        messages.add(
                            DeepSeekMessageDto(
                                role = DeepSeekMessageDto.Role.ASSISTANT,
                                text = ""
                            )
                        )
                        streamingIndex = assistantIndex
                        isInputEnabled = false

                        lifecycleScope.launch {
                            sendRequest(
                                text = currentText,
                                mode = currentMode,
                                assistantIndex = assistantIndex,
                                onStreamingUpdate = { index, text ->
                                    messages[index] = DeepSeekMessageDto(
                                        role = DeepSeekMessageDto.Role.ASSISTANT,
                                        text = text
                                    )
                                },
                                onComplete = {
                                    isInputEnabled = true
                                    streamingIndex = -1
                                },
                                onError = { error ->
                                    messages.add(
                                        DeepSeekMessageDto(
                                            role = DeepSeekMessageDto.Role.SYSTEM,
                                            text = "Ошибка: $error"
                                        )
                                    )
                                    isInputEnabled = true
                                    streamingIndex = -1
                                }
                            )
                        }

                        // НЕ СТИРАЕМ inputText — оставляем для следующих экспериментов
                    }
                )

                MessagesList(
                    messages = messages,
                    streamingIndex = streamingIndex
                )
            }
        }
    }

    @Composable
    private fun TaskInputField(text: String, onTextChange: (String) -> Unit, enabled: Boolean) {
        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text("Введите задачу для решения...") },
            minLines = 3,
            enabled = enabled
        )
    }

    @Composable
    private fun ModeSelector(selectedMode: ReasoningMode, onModeSelect: (ReasoningMode) -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReasoningMode.values().forEach { mode ->
                FilterChip(
                    selected = selectedMode == mode,
                    onClick = { onModeSelect(mode) },
                    label = {
                        Text(
                            modeToLabel(mode),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    @Composable
    private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(enabled = enabled, onClick = onClick) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("→", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }

    @Composable
    private fun MessagesList(messages: List<DeepSeekMessageDto>, streamingIndex: Int) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            itemsIndexed(messages) { index, message ->
                MessageBubble(
                    message = message,
                    isStreaming = index == streamingIndex
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    @Composable
    private fun MessageBubble(message: DeepSeekMessageDto, isStreaming: Boolean) {
        val backgroundColor = when (message.role) {
            DeepSeekMessageDto.Role.USER -> MaterialTheme.colorScheme.primaryContainer
            DeepSeekMessageDto.Role.ASSISTANT -> MaterialTheme.colorScheme.secondaryContainer
            DeepSeekMessageDto.Role.SYSTEM -> MaterialTheme.colorScheme.errorContainer
        }

        val alignment = when (message.role) {
            DeepSeekMessageDto.Role.USER -> Alignment.End
            else -> Alignment.Start
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = alignment
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()  // ← убрали ограничение ширины
                    .animateContentSize(),
                colors = CardDefaults.cardColors(containerColor = backgroundColor)
            ) {
                Text(
                    text = getMessageText(message, isStreaming),
                    modifier = Modifier.padding(12.dp)
                )
            }

            if (isStreaming && message.text.isNotEmpty()) {
                TypingIndicator()
            }
        }
    }

    @Composable
    private fun TypingIndicator() {
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            val transition = rememberInfiniteTransition()
            repeat(3) { index ->
                val alpha by transition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(500, delayMillis = index * 150),
                        repeatMode = RepeatMode.Reverse
                    )
                )
                Box(
                    modifier = Modifier
                        .padding(2.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                        )
                )
            }
        }
    }

    // ========== BUSINESS LOGIC ==========

    private suspend fun sendRequest(
        text: String,
        mode: ReasoningMode,
        assistantIndex: Int,
        onStreamingUpdate: (Int, String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            var fullResponse = ""
            val systemPrompt = getSystemPrompt(mode, text)

            repository.sendStreamingRequestWithControl(
                text = text,
                systemPrompt = systemPrompt,
                maxTokens = null,
                stopSequences = null,
                onChunk = { chunk ->
                    fullResponse += chunk
                    onStreamingUpdate(assistantIndex, fullResponse)
                },
                onComplete = onComplete
            )
        } catch (e: Exception) {
            onError(e.message ?: "Unknown error")
        }
    }

    // ========== HELPERS ==========

    private fun modeToLabel(mode: ReasoningMode): String = when (mode) {
        ReasoningMode.DIRECT -> "Прямой ответ"
        ReasoningMode.STEP_BY_STEP -> "Пошагово"
        ReasoningMode.SELF_PROMPT -> "Самопромпт"
        ReasoningMode.EXPERT_GROUP -> "Эксперты"
    }

    private fun getMessageText(message: DeepSeekMessageDto, isStreaming: Boolean): String {
        return when {
            message.text.isEmpty() && isStreaming -> "..."
            message.text.isEmpty() -> "Пустое сообщение"
            else -> message.text
        }
    }

    private fun getSystemPrompt(mode: ReasoningMode, task: String): String? {
        return when (mode) {
            ReasoningMode.DIRECT -> null

            ReasoningMode.STEP_BY_STEP -> """
                Реши следующую задачу пошагово.
                Объясняй каждый шаг подробно.
                В конце дай финальный ответ.
                
                Задача: $task
            """.trimIndent()

            ReasoningMode.SELF_PROMPT -> """
                Сначала составь подробный промпт (инструкцию) для решения этой задачи.
                Промпт должен включать:
                - анализ условия
                - план решения
                - возможные подводные камни
                
                Затем, используя этот промпт, реши задачу.
                
                Задача: $task
            """.trimIndent()

            ReasoningMode.EXPERT_GROUP -> """
    Ты должен выступить в роли трех разных экспертов. Каждый эксперт должен самостоятельно и полностью решить задачу, не зависимо от других.
    
    Порядок работы:
    
    1. АНАЛИТИК: решает задачу полностью, используя логический и аналитический подход. Дает развернутое решение.
    
    2. ИНЖЕНЕР: решает эту же задачу полностью, но с практической, алгоритмической точки зрения. Описывает четкий алгоритм действий.
    
    3. КРИТИК: решает эту же задачу полностью, но проверяет каждое свое действие на ошибки и предлагает оптимальный путь.
    
    ВАЖНО: Каждый эксперт должен дать ПОЛНОЕ, САМОСТОЯТЕЛЬНОЕ решение задачи. Не ссылаться на решения других экспертов.
    
    Формат ответа:
    
    === АНАЛИТИК ===
    [полное решение аналитика]
    
    === ИНЖЕНЕР ===
    [полное решение инженера]
    
    === КРИТИК ===
    [полное решение критика]
    
    Задача: $task
""".trimIndent()
        }
    }

    // ========== CONSTANTS ==========

    companion object {
        private const val DEFAULT_TASK = "На острове живут рыцари (всегда говорят правду) и лжецы (всегда лгут). \n" +
                "Ты встречаешь трех островитян: A, B, C.\n" +
                "A говорит: \"B - лжец\"\n" +
                "B говорит: \"C - лжец\"\n" +
                "C говорит: \"A и B - лжецы\"\n" +
                "\n" +
                "Кто рыцарь, а кто лжец?"
    }
}