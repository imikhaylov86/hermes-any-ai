package com.hermes.mobile

import com.hermes.mobile.presentation.service.NotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationHelperTest {

    @Test
    fun testCleanMarkdownSnippetStripsThinkTags() {
        val raw = "<think>Analyzing user request and preparing solution...</think>Привет! Вот решение вашей задачи."
        val cleaned = NotificationHelper.cleanMarkdownSnippet(raw)
        assertEquals("Привет! Вот решение вашей задачи.", cleaned)
    }

    @Test
    fun testCleanMarkdownSnippetStripsMediaTags() {
        val raw = "Сгенерировано фото: MEDIA:/sdcard/Pictures/generated.png"
        val cleaned = NotificationHelper.cleanMarkdownSnippet(raw)
        assertEquals("Сгенерировано фото:", cleaned)
    }

    @Test
    fun testCleanMarkdownSnippetMediaFallback() {
        val raw = "MEDIA:/sdcard/Pictures/generated.png"
        val cleaned = NotificationHelper.cleanMarkdownSnippet(raw)
        assertEquals("Изображение готово", cleaned)
    }

    @Test
    fun testCleanMarkdownSnippetStripsFormattingAndCodeBlocks() {
        val raw = """
            # Заголовок
            
            ```kotlin
            val x = 10
            ```
            
            Это **жирный** и *курсивный* текст с `кодом`.
        """.trimIndent()
        val cleaned = NotificationHelper.cleanMarkdownSnippet(raw)
        assertTrue(cleaned.contains("Заголовок"))
        assertTrue(cleaned.contains("Это жирный и курсивный текст с кодом."))
        assertTrue(!cleaned.contains("```"))
        assertTrue(!cleaned.contains("**"))
        assertTrue(!cleaned.contains("#"))
    }

    @Test
    fun testCleanMarkdownSnippetTruncation() {
        val longText = "А".repeat(300)
        val cleaned = NotificationHelper.cleanMarkdownSnippet(longText)
        assertTrue(cleaned.length <= 250)
        assertTrue(cleaned.endsWith("..."))
    }

    @Test
    fun testCleanMarkdownSnippetEmptyFallback() {
        val empty = ""
        val cleaned = NotificationHelper.cleanMarkdownSnippet(empty)
        assertEquals("Ответ сгенерирован", cleaned)
    }
}
