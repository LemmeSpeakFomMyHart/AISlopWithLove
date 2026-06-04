package com.example.aislopwithlove

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.aislopwithlove.agent.Agent
import com.example.aislopwithlove.agent.AgentState
import com.example.aislopwithlove.data.Repository
import com.example.aislopwithlove.data.models.DeepSeekMessageDto
import com.example.aislopwithlove.theme.AISlopWithLoveTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val repository by lazy { Repository() }
    private lateinit var agent: Agent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        agent = Agent(
            context = applicationContext,
            repository = repository,
            modelName = "deepseek/deepseek-v3.2",  // ← 131K контекст
            systemPrompt = "Ты полезный ассистент. Отвечай кратко и по делу."
        )

        setContent {
            AISlopWithLoveTheme {
                ChatScreen()
            }
        }
    }

    @Composable
    private fun ChatScreen() {
        var inputText by remember { mutableStateOf("") }
        val agentState by agent.state.collectAsState()
        val history by agent.history.collectAsState()
        val tokenStats by agent.tokenStats.collectAsState()

        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Заголовок
                Text(
                    text = "День 8: Работа с токенами 🔢",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // Статистика токенов
                tokenStats?.let { stats ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (stats.isNearLimit)
                                MaterialTheme.colorScheme.errorContainer
                            else
                                MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = stats.format(),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Индикатор состояния
                StatusIndicator(state = agentState)

                // История сообщений (фильтруем system)
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(history.filter { it.role != DeepSeekMessageDto.Role.SYSTEM }) { message ->
                        MessageItem(message = message)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (agentState is AgentState.Responding) {
                        item {
                            MessageItem(
                                message = DeepSeekMessageDto(
                                    role = DeepSeekMessageDto.Role.ASSISTANT,
                                    text = (agentState as AgentState.Responding).text
                                ),
                                isStreaming = true
                            )
                        }
                    }
                }

                // Поле ввода
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Введите сообщение...") },
                        enabled = agentState !is AgentState.Thinking && agentState !is AgentState.Responding
                    )

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                val userMessage = inputText
                                inputText = ""

                                lifecycleScope.launch {
                                    agent.sendMessage(
                                        userMessage = userMessage,
                                        onChunk = { /* UI обновляется через state */ },
                                        onComplete = { /* можно показать уведомление */ }
                                    )
                                }
                            }
                        },
                        enabled = agentState !is AgentState.Thinking && agentState !is AgentState.Responding
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("→", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }

                // Кнопка очистки истории
                Button(
                    onClick = {
                        lifecycleScope.launch {
                            agent.clearHistory()
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Очистить историю")
                }
            }
        }
    }

    @Composable
    private fun StatusIndicator(state: AgentState) {
        val (text, color) = when (state) {
            is AgentState.Idle -> "Готов" to MaterialTheme.colorScheme.primary
            is AgentState.Thinking -> "Думаю..." to MaterialTheme.colorScheme.secondary
            is AgentState.Responding -> "Отвечает..." to MaterialTheme.colorScheme.tertiary
            is AgentState.Error -> "Ошибка: ${state.message}" to MaterialTheme.colorScheme.error
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
        }
    }

    @Composable
    private fun MessageItem(message: DeepSeekMessageDto, isStreaming: Boolean = false) {
        val alignment = if (message.role == DeepSeekMessageDto.Role.USER) Alignment.End else Alignment.Start
        val backgroundColor = when (message.role) {
            DeepSeekMessageDto.Role.USER -> MaterialTheme.colorScheme.primaryContainer
            DeepSeekMessageDto.Role.SYSTEM -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.secondaryContainer
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = alignment
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .padding(4.dp),
                colors = CardDefaults.cardColors(containerColor = backgroundColor)
            ) {
                Text(
                    text = if (message.text.isEmpty() && isStreaming) "..." else message.text,
                    modifier = Modifier.padding(12.dp),
                    softWrap = true,
                    overflow = TextOverflow.Visible
                )
            }
        }
    }
}