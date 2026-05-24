package com.example.aislopwithlove

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.aislopwithlove.data.Repository
import com.example.aislopwithlove.data.models.TokenUsage
import com.example.aislopwithlove.theme.AISlopWithLoveTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ModelVersion(
    val modelName: String,
    val label: String,
    val description: String,
    val pricePerMillionInput: Double,
    val pricePerMillionOutput: Double,
    val vramGb: Double
) {
    WEAK(
        modelName = "deepseek/deepseek-v3.2",
        label = "DeepSeek V3.2",
        description = "Предыдущее поколение, 685B параметров",
        pricePerMillionInput = 0.252,
        pricePerMillionOutput = 0.378,
        vramGb = 320.0
    ),
    MEDIUM(
        modelName = "deepseek-v4-flash",
        label = "DeepSeek V4-Flash",
        description = "Оптимизация скорость/качество, 13B активных",
        pricePerMillionInput = 0.14,
        pricePerMillionOutput = 0.28,
        vramGb = 80.0
    ),
    STRONG(
        modelName = "deepseek-v4-pro",
        label = "DeepSeek V4-Pro",
        description = "Максимальная мощность, 49B активных",
        pricePerMillionInput = 1.74,
        pricePerMillionOutput = 3.48,
        vramGb = 320.0
    )
}

data class TestResult(
    val model: ModelVersion,
    val query: String,
    val response: String,
    val durationMs: Long,
    val tokenUsage: TokenUsage?,
    val costUsd: Double,
    val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
    var qualityScore: Double = 0.0  // оценка от другой модели
)

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
        var inputText by remember { mutableStateOf(DEFAULT_QUERY) }
        var isRunning by remember { mutableStateOf(false) }
        val results = remember { mutableStateListOf<TestResult>() }
        var evaluationInProgress by remember { mutableStateOf(false) }
        var verdict by remember { mutableStateOf("") }

        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "День 5: Сравнение версий моделей",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                TextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Введите запрос...") },
                    minLines = 2,
                    enabled = !isRunning && !evaluationInProgress
                )

                // Кнопка для запуска всех трёх моделей
                Button(
                    onClick = {
                        lifecycleScope.launch {
                            isRunning = true
                            results.clear()

                            // Запускаем все три модели последовательно
                            for (model in ModelVersion.values()) {
                                var fullResponse = ""
                                var tokenUsage: TokenUsage? = null
                                val startTime = SystemClock.elapsedRealtime()

                                try {
                                    repository.sendStreamingRequestWithModel(
                                        text = inputText,
                                        modelName = model.modelName,
                                        onChunk = { chunk -> fullResponse += chunk },
                                        onComplete = { usage ->
                                            tokenUsage = usage
                                        },
                                        onError = { error ->
                                            fullResponse = "Ошибка: $error"
                                        }
                                    )

                                    val duration = SystemClock.elapsedRealtime() - startTime
                                    val cost = calculateCost(tokenUsage, model)

                                    results.add(
                                        TestResult(
                                            model = model,
                                            query = inputText,
                                            response = fullResponse,
                                            durationMs = duration,
                                            tokenUsage = tokenUsage,
                                            costUsd = cost
                                        )
                                    )
                                } catch (e: Exception) {
                                    results.add(
                                        TestResult(
                                            model = model,
                                            query = inputText,
                                            response = "Ошибка: ${e.message}",
                                            durationMs = SystemClock.elapsedRealtime() - startTime,
                                            tokenUsage = null,
                                            costUsd = 0.0
                                        )
                                    )
                                }
                            }

                            isRunning = false

                            // Автоматическая оценка ответов
                            evaluationInProgress = true
                            evaluateResults(results, inputText)
                            verdict = getVerdict(results.toList(), inputText)  // ← получаем вердикт
                            evaluationInProgress = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    enabled = !isRunning && !evaluationInProgress
                ) {
                    Text(if (isRunning) "Выполняется..." else "Запустить тест всех моделей")
                }

                if (evaluationInProgress) {
                    Text(
                        text = "🔄 Идёт оценка ответов моделями...",
                        modifier = Modifier.padding(8.dp)
                    )
                }

                // Результаты тестов
                if (results.isNotEmpty()) {
                    Text(
                        text = "Результаты тестов:",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                    )

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(results) { result ->
                            TestResultCard(result = result)
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Итоговая таблица сравнения
                        item {
                            // Итоговая таблица
                            ComparisonTable(results = results)

                            // Вердикт от нейросети
                            if (verdict.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "🧠 Вердикт от модели ${ModelVersion.STRONG.label}:",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                        Text(
                                            text = verdict,
                                            style = MaterialTheme.typography.bodyMedium,
                                            softWrap = true,
                                            overflow = TextOverflow.Visible
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun TestResultCard(result: TestResult) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Заголовок
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = result.model.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(text = result.timestamp, style = MaterialTheme.typography.bodySmall)
                }

                // Метрики
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "⏱️ ${result.durationMs} мс", style = MaterialTheme.typography.bodySmall)
                    if (result.tokenUsage != null) {
                        Text(
                            text = "🔤 ${result.tokenUsage.totalTokens} токенов",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(text = "💰 $${String.format("%.6f", result.costUsd)}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "💾 VRAM: ~${result.model.vramGb} GB", style = MaterialTheme.typography.bodySmall)
                }

                // Скорость
                if (result.tokenUsage != null && result.durationMs > 0) {
                    val tokensPerSec = (result.tokenUsage.totalTokens * 1000.0 / result.durationMs)
                    Text(
                        text = "⚡ Скорость: ${String.format("%.1f", tokensPerSec)} ток/сек",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Оценка качества
                if (result.qualityScore > 0) {
                    Text(
                        text = "⭐ Оценка модели: ${String.format("%.1f", result.qualityScore)}/10",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Полный ответ (разворачиваемый)
                var expanded by remember { mutableStateOf(false) }
                Text(
                    text = if (expanded) result.response else result.response.take(500) + if (result.response.length > 500) "..." else "",
                    style = MaterialTheme.typography.bodyMedium,
                    softWrap = true,
                    overflow = TextOverflow.Visible
                )
                if (result.response.length > 500) {
                    Button(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(if (expanded) "Свернуть" else "Развернуть полностью")
                    }
                }
            }
        }
    }

    @Composable
    private fun ComparisonTable(results: List<TestResult>) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "📊 Сравнительная таблица",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Заголовки
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Модель", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(2f))
                    Text("Время", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text("Токены", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text("Стоимость", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text("Оценка", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Данные
                results.forEach { result ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(result.model.label, modifier = Modifier.weight(2f))
                        Text("${result.durationMs}мс", modifier = Modifier.weight(1f))
                        Text("${result.tokenUsage?.totalTokens ?: 0}", modifier = Modifier.weight(1f))
                        Text("$${String.format("%.5f", result.costUsd)}", modifier = Modifier.weight(1f))
                        Text(
                            if (result.qualityScore > 0) String.format("%.1f/10", result.qualityScore) else "—",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    private suspend fun evaluateResults(results: MutableList<TestResult>, originalQuery: String) {
        // Используем сильную модель (V4-Pro) для оценки всех ответов
        val evaluatorModel = ModelVersion.STRONG

        // Сначала оцениваем каждый ответ по отдельности (по шкале)
        for (i in results.indices) {
            val result = results[i]

            val evaluationPrompt = """
            Ты — эксперт по оценке качества ответов ИИ.
            
            Задача пользователя: "$originalQuery"
            
            Ответ модели ${result.model.label}: 
            "${result.response}"
            
            Оцени этот ответ по шкале от 1 до 10 по следующим критериям:
            - Точность (соответствует ли ответ задаче?)
            - Полнота (охвачены ли все аспекты?)
            - Чёткость (легко ли понять ответ?)
            
            Ответь ТОЛЬКО числом от 1 до 10, ничего больше.
            Например: 7.5
        """.trimIndent()

            var scoreResponse = ""

            try {
                repository.sendStreamingRequestWithModel(
                    text = evaluationPrompt,
                    modelName = evaluatorModel.modelName,
                    onChunk = { chunk -> scoreResponse += chunk },
                    onComplete = {},
                    onError = { scoreResponse = "5.0" }
                )

                val score = scoreResponse.replace(",", ".").replace(Regex("[^0-9.]"), "").toDoubleOrNull()?.coerceIn(1.0, 10.0) ?: 5.0
                results[i] = result.copy(qualityScore = score)
            } catch (e: Exception) {
                results[i] = result.copy(qualityScore = 5.0)
            }
        }
    }

    private fun calculateCost(tokenUsage: TokenUsage?, model: ModelVersion): Double {
        if (tokenUsage == null) return 0.0
        val inputCost = (tokenUsage.promptTokens / 1_000_000.0) * model.pricePerMillionInput
        val outputCost = (tokenUsage.completionTokens / 1_000_000.0) * model.pricePerMillionOutput
        return inputCost + outputCost
    }

    private suspend fun getVerdict(results: List<TestResult>, originalQuery: String): String {
        val evaluatorModel = ModelVersion.STRONG

        // Формируем промпт для сравнения
        val comparisonPrompt = buildString {
            appendLine("Ты — эксперт по оценке качества ответов ИИ.")
            appendLine()
            appendLine("Была задана задача: \"$originalQuery\"")
            appendLine()
            appendLine("Три разные модели ИИ дали свои ответы:")
            appendLine()

            results.forEachIndexed { index, result ->
                appendLine("=== МОДЕЛЬ ${index + 1}: ${result.model.label} ===")
                appendLine(result.response)
                appendLine()
            }

            appendLine("=== ЗАДАНИЕ ДЛЯ ТЕБЯ ===")
            appendLine("Проанализируй все три ответа и дай развёрнутый вердикт по следующей структуре:")
            appendLine()
            appendLine("1. **Краткий анализ каждого ответа:**")
            appendLine("   - Модель 1 (${results[0].model.label}): [сильные и слабые стороны]")
            appendLine("   - Модель 2 (${results[1].model.label}): [сильные и слабые стороны]")
            appendLine("   - Модель 3 (${results[2].model.label}): [сильные и слабые стороны]")
            appendLine()
            appendLine("2. **Сравнение:**")
            appendLine("   - Какая модель дала самый точный ответ?")
            appendLine("   - Какая модель дала самый полный ответ?")
            appendLine("   - Какая модель дала самый понятный ответ?")
            appendLine()
            appendLine("3. **Итоговый вердикт:**")
            appendLine("   - Какую модель ты рекомендуешь для данной задачи и почему?")
            appendLine("   - Какая модель оказалась худшей и почему?")
            appendLine()
            appendLine("Будь объективен и аргументирован. Пиши на русском языке.")
        }

        var verdict = ""

        try {
            repository.sendStreamingRequestWithModel(
                text = comparisonPrompt,
                modelName = evaluatorModel.modelName,
                onChunk = { chunk -> verdict += chunk },
                onComplete = {},
                onError = { verdict = "Не удалось получить вердикт" }
            )
        } catch (e: Exception) {
            verdict = "Ошибка при получении вердикта: ${e.message}"
        }

        return verdict
    }

    companion object {
        private const val DEFAULT_QUERY = "Объясни концепцию 'ограниченной рациональности' Герберта Саймона. Приведи пример из бизнеса и из повседневной жизни. Ответ дай кратко, в 3-4 абзаца."
    }
}