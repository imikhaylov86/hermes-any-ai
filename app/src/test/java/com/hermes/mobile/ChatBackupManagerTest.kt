package com.hermes.mobile

import com.hermes.mobile.domain.backup.ChatBackupManager
import com.hermes.mobile.domain.backup.FullBackupData
import com.hermes.mobile.domain.backup.MessageBackupItem
import com.hermes.mobile.domain.backup.SessionBackupItem
import com.hermes.mobile.domain.model.Attachment
import com.hermes.mobile.domain.model.ChatMessage
import com.hermes.mobile.domain.model.MessageRole
import com.hermes.mobile.domain.model.MessageStatus
import com.hermes.mobile.domain.model.SessionInfo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ChatBackupManagerTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testFormatSessionToMarkdown() {
        val session = SessionInfo(
            id = "sess_test_1",
            title = "Тестирование экспорта",
            createdAtEpochMs = 1700000000000L,
            updatedAtEpochMs = 1700000100000L,
            model = "Hermes 3"
        )

        val messages = listOf(
            ChatMessage(
                id = "m1",
                sessionId = "sess_test_1",
                role = MessageRole.USER,
                content = "Напиши функцию на Kotlin",
                timestampEpochMs = 1700000010000L,
                status = MessageStatus.COMPLETE,
                attachments = listOf(
                    Attachment(name = "specs.txt", mimeType = "text/plain", sizeBytes = 120, uriOrPath = "content://dummy")
                )
            ),
            ChatMessage(
                id = "m2",
                sessionId = "sess_test_1",
                role = MessageRole.ASSISTANT,
                content = "```kotlin\nfun hello() = \"world\"\n```",
                timestampEpochMs = 1700000020000L,
                status = MessageStatus.COMPLETE,
                thinkingContent = "Пользователь просит функцию"
            )
        )

        val markdown = ChatBackupManager.formatSessionToMarkdown(session, messages)

        assertTrue(markdown.contains("# Тестирование экспорта"))
        assertTrue(markdown.contains("Hermes 3"))
        assertTrue(markdown.contains("### 👤 Вы"))
        assertTrue(markdown.contains("Напиши функцию на Kotlin"))
        assertTrue(markdown.contains("📎 specs.txt"))
        assertTrue(markdown.contains("### 🤖 Hermes"))
        assertTrue(markdown.contains("> 💭 **Размышления:**"))
        assertTrue(markdown.contains("fun hello() = \"world\""))
    }

    @Test
    fun testFullBackupJsonSerialization() {
        val backup = FullBackupData(
            version = 1,
            exportedAtEpochMs = 1700000000000L,
            sessions = listOf(
                SessionBackupItem(
                    id = "s1",
                    title = "Беседа 1",
                    createdAtEpochMs = 1700000000000L,
                    updatedAtEpochMs = 1700000050000L,
                    model = "Hermes 3",
                    messages = listOf(
                        MessageBackupItem(
                            id = "m1",
                            sessionId = "s1",
                            role = "USER",
                            content = "Привет",
                            timestampEpochMs = 1700000010000L,
                            status = "COMPLETE"
                        )
                    )
                )
            )
        )

        val encoded = json.encodeToString(backup)
        assertTrue(encoded.contains("\"title\": \"Беседа 1\""))
        assertTrue(encoded.contains("\"app\": \"Hermes Mobile UI\""))

        val decoded = json.decodeFromString<FullBackupData>(encoded)
        assertEquals(1, decoded.version)
        assertEquals(1, decoded.sessions.size)
        assertEquals("Беседа 1", decoded.sessions[0].title)
        assertEquals("Привет", decoded.sessions[0].messages[0].content)
    }
}
