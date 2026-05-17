package com.example.aislopwithlove.day1

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
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import com.example.aislopwithlove.day1.data.Repository
import com.example.aislopwithlove.day1.data.models.DeepSeekMessageDto
import com.example.aislopwithlove.day1.theme.AISlopWithLoveTheme
import kotlinx.coroutines.launch

enum class ResponseMode {
    NO_CONTROL,
    WITH_FORMAT,
    WITH_LENGTH_LIMIT,
    WITH_STOP_SEQUENCE
}

class MainActivity : ComponentActivity() {

    private val repository by lazy { Repository() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AISlopWithLoveTheme {
                Content()
            }
        }
    }

    @Composable
    private fun Content() {
        var inputText by remember { mutableStateOf("") }
        var isInputEnabled by remember { mutableStateOf(true) }
        var selectedMode by remember { mutableStateOf(ResponseMode.NO_CONTROL) }
        val messages = remember { mutableStateListOf<DeepSeekMessageDto>() }
        var currentAssistantMessageIndex by remember { mutableIntStateOf(-1) }

        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Выбор режима
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ResponseMode.values().forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            label = {
                                Text(
                                    when (mode) {
                                        ResponseMode.NO_CONTROL -> "Без контроля"
                                        ResponseMode.WITH_FORMAT -> "Формат JSON"
                                        ResponseMode.WITH_LENGTH_LIMIT -> "Лимит длины"
                                        ResponseMode.WITH_STOP_SEQUENCE -> "Стоп-символ !"
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Поле ввода и кнопка
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        enabled = isInputEnabled,
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Введите запрос...") }
                    )

                    IconButton(
                        enabled = isInputEnabled && inputText.isNotBlank(),
                        onClick = {
                            // Добавляем сообщение пользователя
                            messages.add(
                                DeepSeekMessageDto(
                                    role = DeepSeekMessageDto.Role.USER,
                                    text = inputText
                                )
                            )

                            // Добавляем пустое сообщение ассистента
                            val assistantIndex = messages.size
                            messages.add(
                                DeepSeekMessageDto(
                                    role = DeepSeekMessageDto.Role.ASSISTANT,
                                    text = ""
                                )
                            )
                            currentAssistantMessageIndex = assistantIndex

                            isInputEnabled = false

                            sendMessage(
                                scope = lifecycleScope,
                                repository = repository,
                                text = inputText,
                                mode = selectedMode,
                                assistantIndex = assistantIndex,
                                onStreamingUpdate = { index, text ->
                                    messages[index] = DeepSeekMessageDto(
                                        role = DeepSeekMessageDto.Role.ASSISTANT,
                                        text = text
                                    )
                                },
                                onComplete = {
                                    isInputEnabled = true
                                    currentAssistantMessageIndex = -1
                                },
                                onError = { errorMessage ->
                                    messages.add(
                                        DeepSeekMessageDto(
                                            role = DeepSeekMessageDto.Role.SYSTEM,
                                            text = "Ошибка: $errorMessage"
                                        )
                                    )
                                    isInputEnabled = true
                                    currentAssistantMessageIndex = -1
                                }
                            )

                            inputText = ""
                        }
                    ) {
                        Image(
                            painter = painterResource(android.R.drawable.ic_menu_send),
                            contentDescription = "Отправить"
                        )
                    }
                }

                // Список сообщений
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    itemsIndexed(messages) { index, message ->
                        MessageBubble(
                            message = message,
                            isStreaming = index == currentAssistantMessageIndex
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun MessageBubble(
        message: DeepSeekMessageDto,
        isStreaming: Boolean
    ) {
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
                    .widthIn(max = 320.dp)
                    .animateContentSize(),
                colors = CardDefaults.cardColors(containerColor = backgroundColor)
            ) {
                Text(
                    text = if (message.text.isEmpty() && isStreaming) "..."
                    else if (message.text.isEmpty()) "Пустое сообщение"
                    else message.text,
                    modifier = Modifier.padding(12.dp)
                )
            }

            if (isStreaming && message.text.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    val infiniteTransition = rememberInfiniteTransition()
                    repeat(3) { index ->
                        val alpha by infiniteTransition.animateFloat(
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
        }
    }
}

// Отдельная функция вне компонента
private fun sendMessage(
    scope: LifecycleCoroutineScope,
    repository: Repository,
    text: String,
    mode: ResponseMode,
    assistantIndex: Int,
    onStreamingUpdate: (Int, String) -> Unit,
    onComplete: () -> Unit,
    onError: (String) -> Unit
) {
    val systemPrompt = when (mode) {
        ResponseMode.WITH_FORMAT -> """
            Ты должен отвечать ТОЛЬКО в формате JSON:
            {
                "answer": "твой ответ здесь",
                "reasoning": "краткое объяснение"
            }
            Не используй никакой другой текст вне JSON.
        """.trimIndent()
        ResponseMode.WITH_LENGTH_LIMIT -> "Отвечай кратко, не более 50 слов."
        ResponseMode.WITH_STOP_SEQUENCE -> "Отвечай до первого восклицательного знака (!)."
        else -> null
    }

    val maxTokens = when (mode) {
        ResponseMode.WITH_LENGTH_LIMIT -> 100
        else -> null
    }

    val stopSequences = when (mode) {
        ResponseMode.WITH_STOP_SEQUENCE -> listOf("!")
        else -> null
    }

    scope.launch {
        try {
            var fullResponse = ""

            if (mode == ResponseMode.NO_CONTROL) {
                repository.sentStreamingRequest(
                    text = text,
                    onChunk = { chunk ->
                        fullResponse += chunk
                        onStreamingUpdate(assistantIndex, fullResponse)
                    },
                    onComplete = onComplete
                )
            } else {
                repository.sendStreamingRequestWithControl(
                    text = text,
                    systemPrompt = systemPrompt,
                    maxTokens = maxTokens,
                    stopSequences = stopSequences,
                    onChunk = { chunk ->
                        fullResponse += chunk
                        onStreamingUpdate(assistantIndex, fullResponse)
                    },
                    onComplete = onComplete
                )
            }
        } catch (e: Exception) {
            onError(e.message ?: "Unknown error")
        }
    }
}