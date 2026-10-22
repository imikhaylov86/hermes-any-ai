package com.hermes.mobile.persistence.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hermes.mobile.domain.model.Attachment
import com.hermes.mobile.domain.model.ChatMessage
import com.hermes.mobile.domain.model.MessageRole
import com.hermes.mobile.domain.model.MessageStatus
import com.hermes.mobile.domain.model.SessionInfo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val timestampEpochMs: Long,
    val status: String,
    val errorMessage: String? = null,
    val attachmentsJson: String? = null,
    val thinkingContent: String? = null
) {
    fun toDomain(): ChatMessage {
        val parsedAttachments: List<Attachment> = if (!attachmentsJson.isNullOrBlank()) {
            try {
                Json.decodeFromString<List<Attachment>>(attachmentsJson)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        return ChatMessage(
            id = id,
            sessionId = sessionId,
            role = MessageRole.valueOf(role),
            content = content,
            timestampEpochMs = timestampEpochMs,
            status = MessageStatus.valueOf(status),
            attachments = parsedAttachments,
            errorMessage = errorMessage,
            thinkingContent = thinkingContent
        )
    }

    companion object {
        fun fromDomain(msg: ChatMessage): MessageEntity {
            val attJson = if (msg.attachments.isNotEmpty()) {
                try {
                    Json.encodeToString(msg.attachments)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }

            return MessageEntity(
                id = msg.id,
                sessionId = msg.sessionId,
                role = msg.role.name,
                content = msg.content,
                timestampEpochMs = msg.timestampEpochMs,
                status = msg.status.name,
                errorMessage = msg.errorMessage,
                attachmentsJson = attJson,
                thinkingContent = msg.thinkingContent
            )
        }
    }
}

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val model: String,
    val isPinned: Boolean,
    val isArchived: Boolean,
    val lastMessagePreview: String
) {
    fun toDomain(): SessionInfo {
        return SessionInfo(
            id = id,
            title = title,
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
            model = model,
            isPinned = isPinned,
            isArchived = isArchived,
            lastMessagePreview = lastMessagePreview
        )
    }

    companion object {
        fun fromDomain(session: SessionInfo): SessionEntity {
            return SessionEntity(
                id = session.id,
                title = session.title,
                createdAtEpochMs = session.createdAtEpochMs,
                updatedAtEpochMs = session.updatedAtEpochMs,
                model = session.model,
                isPinned = session.isPinned,
                isArchived = session.isArchived,
                lastMessagePreview = session.lastMessagePreview
            )
        }
    }
}
