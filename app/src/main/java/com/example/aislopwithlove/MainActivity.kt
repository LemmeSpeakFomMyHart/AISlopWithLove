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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.aislopwithlove.data.Repository
import com.example.aislopwithlove.data.models.DeepSeekMessageDto
import com.example.aislopwithlove.theme.AISlopWithLoveTheme
import kotlinx.coroutines.launch

enum class TemperatureMode(val value: Double, val label: String) {
    LOW(0.0, "Temperature 0"),
    MEDIUM(0.7, "Temperature 0.7"),
    HIGH(1.2, "Temperature 1.2")
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

    @Composable
    private fun ChatScreen() {
        var inputText by remember { mutableStateOf(DEFAULT_TASK) }
        var isInputEnabled by remember { mutableStateOf(true) }
        var selectedTemperature by remember { mutableStateOf(TemperatureMode.MEDIUM) }
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

                TemperatureSelector(
                    selectedMode = selectedTemperature,
                    onModeSelect = { selectedTemperature = it }
                )

                SendButton(
                    enabled = isInputEnabled && inputText.isNotBlank(),
                    onClick = {
                        val currentText = inputText
                        val currentTemp = selectedTemperature

                        messages.add(
                            DeepSeekMessageDto(
                                role = DeepSeekMessageDto.Role.USER,
                                text = "[${currentTemp.label}] $currentText"
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
                            sendRequestWithTemperature(
                                text = currentText,
                                temperature = currentTemp.value,
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
            placeholder = { Text("Введите запрос...") },
            minLines = 3,
            enabled = enabled
        )
    }

    @Composable
    private fun TemperatureSelector(
        selectedMode: TemperatureMode,
        onModeSelect: (TemperatureMode) -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TemperatureMode.values().forEach { mode ->
                FilterChip(
                    selected = selectedMode == mode,
                    onClick = { onModeSelect(mode) },
                    label = {
                        Text(
                            mode.label,
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
                    .fillMaxWidth()
                    .animateContentSize(),
                colors = CardDefaults.cardColors(containerColor = backgroundColor)
            ) {
                Text(
                    text = getMessageText(message, isStreaming),
                    modifier = Modifier.padding(12.dp),
                    softWrap = true,
                    overflow = TextOverflow.Visible
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

    private suspend fun sendRequestWithTemperature(
        text: String,
        temperature: Double,
        assistantIndex: Int,
        onStreamingUpdate: (Int, String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            var fullResponse = ""

            repository.sendStreamingRequestWithTemperature(
                text = text,
                temperature = temperature,
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

    private fun getMessageText(message: DeepSeekMessageDto, isStreaming: Boolean): String {
        return when {
            message.text.isEmpty() && isStreaming -> "..."
            message.text.isEmpty() -> "Пустое сообщение"
            else -> message.text
        }
    }

    companion object {
        private const val DEFAULT_TASK = "Придумай 5 идей для стартапа в сфере экологии. Для каждой идеи дай название и краткое описание."
    }
}