package com.hermes.mobile.domain.backup

import android.content.Context
import android.content.Intent
import com.hermes.mobile.domain.model.ChatMessage
import com.hermes.mobile.domain.model.MessageRole
import com.hermes.mobile.domain.model.SessionInfo
import com.hermes.mobile.persistence.database.MessageDao
import com.hermes.mobile.persistence.database.MessageEntity
import com.hermes.mobile.persistence.database.SessionDao
import com.hermes.mobile.persistence.database.SessionEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class MessageBackupItem(
    val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val timestampEpochMs: Long,
    val status: String,
    val errorMessage: String? = null,
    val thinkingContent: String? = null
)

@Serializable
data class SessionBackupItem(
    val id: String,
    val title: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val model: String,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val lastMessagePreview: String = "",
    val messages: List<MessageBackupItem> = emptyList()
)

@Serializable
data class FullBackupData(
    val version: Int = 1,
    val exportedAtEpochMs: Long,
    val app: String = "Hermes Mobile UI",
    val sessions: List<SessionBackupItem>
)

object ChatBackupManager {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Форматирует беседу в Markdown для сохранения или отправки в мессенджер/заметки
     */
    fun formatSessionToMarkdown(session: SessionInfo, messages: List<ChatMessage>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateStr = dateFormat.format(Date(session.createdAtEpochMs))

        val sb = StringBuilder()
        sb.append("# ").append(session.title.ifBlank { "Беседа с Hermes" }).append("\n\n")
        sb.append("*Создано: ").append(dateStr).append(" • Модель: ").append(session.model).append("*\n\n")
        sb.append("---\n\n")

        for (msg in messages) {
            val timeStr = timeFormat.format(Date(msg.timestampEpochMs))
            val roleHeader = when (msg.role) {
                MessageRole.USER -> "### 👤 Вы ($timeStr)\n\n"
                MessageRole.ASSISTANT -> "### 🤖 Hermes ($timeStr)\n\n"
                MessageRole.SYSTEM -> "### ⚙️ Система ($timeStr)\n\n"
            }
            sb.append(roleHeader)

            if (!msg.thinkingContent.isNullOrBlank()) {
                sb.append("> 💭 **Размышления:**\n")
                msg.thinkingContent.lines().forEach { line ->
                    sb.append("> ").append(line).append("\n")
                }
                sb.append("\n")
            }

            sb.append(msg.content).append("\n\n")

            if (msg.attachments.isNotEmpty()) {
                sb.append("**Вложения:**\n")
                msg.attachments.forEach { att ->
                    sb.append("- 📎 ").append(att.name).append(" (").append(att.mimeType).append(")\n")
                }
                sb.append("\n")
            }

            sb.append("---\n\n")
        }

        sb.append("\n*Экспортировано из Hermes Mobile UI*\n")
        return sb.toString()
    }

    /**
     * Открывает стандартное системное окно «Поделиться» с текстом диалога в Markdown
     */
    fun shareMarkdown(context: Context, title: String, markdown: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, markdown)
            type = "text/plain"
        }
        val chooser = Intent.createChooser(sendIntent, "Поделиться беседой: $title").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    /**
     * Создает структурированную резервную копию всех бесед и сообщений в формате JSON
     */
    suspend fun createFullBackupJson(sessionDao: SessionDao, messageDao: MessageDao): String {
        val allSessions = sessionDao.getAllSessionsList()
        val allMessages = messageDao.getAllMessagesList()

        val messagesBySession = allMessages.groupBy { it.sessionId }

        val backupSessions = allSessions.map { s ->
            val msgs = messagesBySession[s.id].orEmpty().map { m ->
                MessageBackupItem(
                    id = m.id,
                    sessionId = m.sessionId,
                    role = m.role,
                    content = m.content,
                    timestampEpochMs = m.timestampEpochMs,
                    status = m.status,
                    errorMessage = m.errorMessage,
                    thinkingContent = m.thinkingContent
                )
            }
            SessionBackupItem(
                id = s.id,
                title = s.title,
                createdAtEpochMs = s.createdAtEpochMs,
                updatedAtEpochMs = s.updatedAtEpochMs,
                model = s.model,
                isPinned = s.isPinned,
                isArchived = s.isArchived,
                lastMessagePreview = s.lastMessagePreview,
                messages = msgs
            )
        }

        val backupData = FullBackupData(
            version = 1,
            exportedAtEpochMs = System.currentTimeMillis(),
            sessions = backupSessions
        )

        return json.encodeToString(backupData)
    }

    /**
     * Открывает стандартное окно «Поделиться» с файлом резервной копии
     */
    fun shareBackupJson(context: Context, backupJson: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_SUBJECT, "Hermes_Backup_${System.currentTimeMillis()}.json")
            putExtra(Intent.EXTRA_TEXT, backupJson)
            type = "application/json"
        }
        val chooser = Intent.createChooser(sendIntent, "Экспорт резервной копии бесед").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    /**
     * Восстанавливает беседы и сообщения из строки JSON резервной копии
     * Возвращает Pair(импортировано_сессий, импортировано_сообщений)
     */
    suspend fun restoreFromJson(
        backupJson: String,
        sessionDao: SessionDao,
        messageDao: MessageDao
    ): Pair<Int, Int> {
        val backupData = json.decodeFromString<FullBackupData>(backupJson)
        var restoredSessions = 0
        var restoredMessages = 0

        for (sessionItem in backupData.sessions) {
            val sessionEntity = SessionEntity(
                id = sessionItem.id,
                title = sessionItem.title,
                createdAtEpochMs = sessionItem.createdAtEpochMs,
                updatedAtEpochMs = sessionItem.updatedAtEpochMs,
                model = sessionItem.model,
                isPinned = sessionItem.isPinned,
                isArchived = sessionItem.isArchived,
                lastMessagePreview = sessionItem.lastMessagePreview
            )
            sessionDao.insertSession(sessionEntity)
            restoredSessions++

            if (sessionItem.messages.isNotEmpty()) {
                val messageEntities = sessionItem.messages.map { m ->
                    MessageEntity(
                        id = m.id,
                        sessionId = m.sessionId,
                        role = m.role,
                        content = m.content,
                        timestampEpochMs = m.timestampEpochMs,
                        status = m.status,
                        errorMessage = m.errorMessage,
                        attachmentsJson = null,
                        thinkingContent = m.thinkingContent
                    )
                }
                messageDao.insertMessages(messageEntities)
                restoredMessages += messageEntities.size
            }
        }

        return Pair(restoredSessions, restoredMessages)
    }
}
