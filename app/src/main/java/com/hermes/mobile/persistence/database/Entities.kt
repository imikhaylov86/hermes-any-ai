package com.hermes.mobile.persistence.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hermes.mobile.domain.model.Attachment
import com.hermes.mobile.domain.model.ChatMessage
import com.hermes.mobile.domain.model.MessageRole
import com.hermes.mobile.domain.model.MessageStatus
import com.hermes.mobile.domain.model.SessionInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
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

        val resolvedRole = try {
            MessageRole.valueOf(role.trim().uppercase())
        } catch (e: Exception) {
            when (role.trim().lowercase()) {
                "user" -> MessageRole.USER
                "assistant", "bot", "model" -> MessageRole.ASSISTANT
                "system" -> MessageRole.SYSTEM
                else -> MessageRole.USER
            }
        }

        val resolvedStatus = try {
            MessageStatus.valueOf(status.trim().uppercase())
        } catch (e: Exception) {
            when (status.trim().lowercase()) {
                "sending" -> MessageStatus.SENDING
                "sent", "delivered" -> MessageStatus.SENT
                "streaming" -> MessageStatus.STREAMING
                "complete", "completed", "done", "success" -> MessageStatus.COMPLETE
                "error", "failed" -> MessageStatus.ERROR
                else -> MessageStatus.COMPLETE
            }
        }

        return ChatMessage(
            id = id,
            sessionId = sessionId,
            role = resolvedRole,
            content = content,
            timestampEpochMs = timestampEpochMs,
            status = resolvedStatus,
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
    val lastMessagePreview: String,
    val hermesSessionId: String? = null
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
            lastMessagePreview = lastMessagePreview,
            hermesSessionId = hermesSessionId
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
                lastMessagePreview = session.lastMessagePreview,
                hermesSessionId = session.hermesSessionId
            )
        }
    }
}

@Entity(
    tableName = "archived_session_payloads",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ],
    indices = [androidx.room.Index("sessionId")]
)
data class ArchivedSessionPayloadEntity(
    @PrimaryKey val sessionId: String,
    val messageCount: Int,
    val originalSizeBytes: Long,
    val compressedSizeBytes: Long,
    val compressedPayload: ByteArray,
    val archivedAtEpochMs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ArchivedSessionPayloadEntity

        if (sessionId != other.sessionId) return false
        if (messageCount != other.messageCount) return false
        if (originalSizeBytes != other.originalSizeBytes) return false
        if (compressedSizeBytes != other.compressedSizeBytes) return false
        if (!compressedPayload.contentEquals(other.compressedPayload)) return false
        if (archivedAtEpochMs != other.archivedAtEpochMs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sessionId.hashCode()
        result = 31 * result + messageCount
        result = 31 * result + originalSizeBytes.hashCode()
        result = 31 * result + compressedSizeBytes.hashCode()
        result = 31 * result + compressedPayload.contentHashCode()
        result = 31 * result + archivedAtEpochMs.hashCode()
        return result
    }
}
