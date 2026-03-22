package com.example.aislopwithlove.day1

import android.R
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.aislopwithlove.day1.data.Repository
import com.example.aislopwithlove.day1.data.models.DeepSeekMessageDto
import com.example.aislopwithlove.day1.theme.AISlopWithLoveTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val repository by lazy {
        Repository()
    }

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
        val messages = remember { mutableStateListOf<DeepSeekMessageDto>() }

        var streamingMessage by remember { mutableStateOf("") }
        var currentAssistantMessageIndex by remember { mutableIntStateOf(-1) }

        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            enabled = isInputEnabled,
                            value = inputText,
                            onValueChange = { inputText = it }
                        )
                        IconButton(enabled = isInputEnabled, onClick = {
                            if (inputText.isNotBlank()) {
                                messages.add(
                                    DeepSeekMessageDto(
                                        role = DeepSeekMessageDto.Role.USER,
                                        text = inputText
                                    )
                                )

                                val assistantMessageIndex = messages.size
                                messages.add(
                                    DeepSeekMessageDto(
                                        role = DeepSeekMessageDto.Role.ASSISTANT,
                                        text = ""
                                    )
                                )
                                currentAssistantMessageIndex = assistantMessageIndex
                                streamingMessage = ""

                                lifecycleScope.launch {
                                    try {
                                        isInputEnabled = false
                                        repository.sentStreamingRequest(
                                            text = inputText,
                                            onChunk = { chunk ->
                                                streamingMessage += chunk
                                                messages[assistantMessageIndex] =
                                                    DeepSeekMessageDto(
                                                        role = DeepSeekMessageDto.Role.ASSISTANT,
                                                        text = streamingMessage
                                                    )
                                            },
                                            onComplete = {
                                                isInputEnabled = true
                                                currentAssistantMessageIndex = -1
                                                streamingMessage = ""
                                            }
                                        )
                                    } catch (e: Exception) {
                                        messages.add(
                                            DeepSeekMessageDto(
                                                role = DeepSeekMessageDto.Role.SYSTEM,
                                                "Ошибка: ${e.message}"
                                            )
                                        )

                                        isInputEnabled = true
                                        currentAssistantMessageIndex = -1
                                        streamingMessage = ""
                                    }
                                }

                                inputText = ""
                            }
                        }) {
                            Image(
                                painterResource(R.drawable.ic_menu_send),
                                contentDescription = null
                            )
                        }
                    }

                    Messages(messages, streamingIndex = currentAssistantMessageIndex)
                }
            }
        }
    }

    @Composable
    private fun Messages(messages: List<DeepSeekMessageDto>, streamingIndex: Int) {
        LazyColumn(modifier = Modifier.padding(16.dp)) {
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
                    .widthIn(max = 280.dp)
                    .animateContentSize(),
                colors = CardDefaults.cardColors(containerColor = backgroundColor)
            ) {
                Text(
                    text = message.text.ifEmpty { if (isStreaming) "..." else "Пустое сообщение" },
                    modifier = Modifier.padding(12.dp)
                )
            }

            if (isStreaming) {
                Row(
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(3) { index ->
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .animateContentSize()
                                .padding(2.dp)
                        ) {
                            val infiniteTransition = rememberInfiniteTransition()
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
                                    .fillMaxSize()
                                    .background(
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                }
            }
        }
    }

    @Preview(showBackground = true, showSystemUi = true)
    @Composable
    fun GreetingPreview() {
        AISlopWithLoveTheme {
            Content()
        }
    }
}