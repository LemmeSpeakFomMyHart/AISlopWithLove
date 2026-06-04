package com.example.aislopwithlove.utils

import com.example.aislopwithlove.data.models.DeepSeekMessageDto
import java.util.Base64
import kotlin.math.log2

/**
 * Утилита для приблизительного подсчёта токенов.
 * 
 * DeepSeek использует токенизатор на основе Byte-Pair Encoding (BPE).
 * Точный подсчёт требует вызова API или загрузки токенизатора,
 * но для демонстрации используем эвристику:
 * - 1 токен ≈ 4 символа для английского текста
 * - 1 токен ≈ 1.5-2 символа для русского текста
 * 
 * Для production лучше использовать официальный токенизатор от DeepSeek.
 */
object TokenCounter {
    
    /**
     * Подсчитывает приблизительное количество токенов в тексте.
     * 
     * @param text Исходный текст
     * @return Примерное количество токенов
     */
    fun countTokens(text: String): Int {
        if (text.isEmpty()) return 0
        
        // Оценка для смешанного русско-английского текста
        // Русские символы в UTF-8 занимают 2 байта, английские - 1 байт
        var bytes = 0
        for (char in text) {
            bytes += when {
                char.code <= 0x7F -> 1      // ASCII (английские буквы, цифры, пунктуация)
                char.code <= 0x7FF -> 2     // Русские буквы и другие
                else -> 3                    // Редкие символы
            }
        }
        
        // BPE в среднем даёт ~4 байта на токен
        return maxOf(1, bytes / 4)
    }
    
    /**
     * Подсчитывает общее количество токенов в истории диалога.
     * 
     * @param messages Список сообщений
     * @return Общее количество токенов
     */
    fun countTokensInMessages(messages: List<DeepSeekMessageDto>): Int {
        var total = 0
        for (message in messages) {
            // Добавляем служебные токены для каждого сообщения (~4 токена на метаданные)
            total += 4 // role, metadata
            total += countTokens(message.text)
        }
        return total
    }
    
    /**
     * Форматирует количество токенов в удобочитаемый вид.
     */
    fun formatTokens(tokens: Int): String {
        return when {
            tokens >= 1_000_000 -> String.format("%.2fM", tokens / 1_000_000.0)
            tokens >= 1_000 -> String.format("%.1fK", tokens / 1000.0)
            else -> tokens.toString()
        }
    }
    
    /**
     * Рассчитывает стоимость запроса на основе токенов.
     * Цены для модели deepseek-v4-flash:
     * - Вход: $0.14 за 1M токенов
     * - Выход: $0.28 за 1M токенов
     */
    fun calculateCost(promptTokens: Int, completionTokens: Int): Double {
        val inputCost = promptTokens / 1_000_000.0 * 0.14
        val outputCost = completionTokens / 1_000_000.0 * 0.28
        return inputCost + outputCost
    }
    
    /**
     * Форматирует стоимость в удобочитаемый вид.
     */
    fun formatCost(cost: Double): String {
        return when {
            cost < 0.0001 -> "< $0.0001"
            cost < 0.001 -> String.format("$%.6f", cost)
            else -> String.format("$%.4f", cost)
        }
    }
}