package com.example.aislopwithlove.day1

import android.R
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
        var isLoading by remember { mutableStateOf(false) }
        val messages = remember { mutableStateListOf<DeepSeekMessageDto>() }

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
                            messages.add(
                                DeepSeekMessageDto(
                                    role = DeepSeekMessageDto.Role.USER,
                                    text = inputText
                                )
                            )

                            lifecycleScope.launch {
                                try {
                                    isInputEnabled = false
                                    isLoading = true
                                    val aiResponse = repository.sendRequest(inputText)
                                    messages.addAll(aiResponse.choices.map { it.message })
                                } catch (e: Exception) {
                                    messages.add(
                                        DeepSeekMessageDto(
                                            role = DeepSeekMessageDto.Role.SYSTEM,
                                            "Ошибка: ${e.message}"
                                        )
                                    )
                                }

                                isInputEnabled = true
                                isLoading = false
                            }

                            inputText = ""
                        }) {
                            Image(
                                painterResource(R.drawable.ic_menu_send),
                                contentDescription = null
                            )
                        }
                    }

                    Messages(messages)
                }

                if (isLoading) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }
        }
    }

    @Composable
    private fun Messages(messages: List<DeepSeekMessageDto>) {
        LazyColumn(modifier = Modifier.padding(16.dp)) {
            items(messages) {
                Text("Роль: ${it.role}, сообщение: ${it.text}")
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