package com.hermes.mobile.transport.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClientHelloDto(
    val type: String = "client_hello",
    @SerialName("protocol_version") val protocolVersion: String = "1.0",
    @SerialName("device_id") val deviceId: String,
    @SerialName("auth_token") val authToken: String? = null
)

@Serializable
data class ServerReadyDto(
    val type: String = "server_ready",
    val status: String,
    @SerialName("server_version") val serverVersion: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("active_model") val activeModel: String = "Hermes 3",
    val capabilities: List<String> = emptyList()
)

@Serializable
data class AttachmentDto(
    val name: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("uri_or_path") val uriOrPath: String,
    @SerialName("size_bytes") val sizeBytes: Long = 0,
    @SerialName("data_url") val dataUrl: String? = null
)

@Serializable
data class SendMessageDto(
    val type: String = "send_message",
    @SerialName("message_id") val messageId: String,
    @SerialName("session_id") val sessionId: String,
    val content: String,
    val attachments: List<AttachmentDto> = emptyList()
)

@Serializable
data class StreamChunkDto(
    val type: String = "stream_chunk",
    @SerialName("message_id") val messageId: String,
    @SerialName("session_id") val sessionId: String,
    val delta: String,
    val sequence: Int
)

@Serializable
data class MessageCompleteDto(
    val type: String = "message_complete",
    @SerialName("message_id") val messageId: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("final_content") val finalContent: String,
    @SerialName("thinking") val thinking: String? = null
)

@Serializable
data class StopGenerationDto(
    val type: String = "stop_generation",
    @SerialName("session_id") val sessionId: String
)

@Serializable
data class ToolApprovalRequestDto(
    val type: String = "tool_approval_request",
    @SerialName("request_id") val requestId: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("tool_name") val toolName: String,
    @SerialName("risk_level") val riskLevel: String,
    @SerialName("parameters_preview") val parametersPreview: String,
    @SerialName("can_always_allow") val canAlwaysAllow: Boolean = false,
    @SerialName("created_at_epoch_ms") val createdAtEpochMs: Long = System.currentTimeMillis()
)

@Serializable
data class ToolApprovalResponseDto(
    val type: String = "tool_approval_response",
    @SerialName("request_id") val requestId: String,
    @SerialName("session_id") val sessionId: String,
    val decision: String // approve, deny, always_allow
)

@Serializable
data class ClarifyPromptDto(
    val type: String = "clarify_prompt",
    @SerialName("prompt_id") val promptId: String,
    @SerialName("session_id") val sessionId: String,
    val question: String,
    val options: List<String> = emptyList(),
    @SerialName("is_multi_choice") val isMultiChoice: Boolean = false,
    @SerialName("created_at_epoch_ms") val createdAtEpochMs: Long = System.currentTimeMillis()
)

@Serializable
data class ClarifyResponseDto(
    val type: String = "clarify_response",
    @SerialName("prompt_id") val promptId: String,
    @SerialName("session_id") val sessionId: String,
    val answer: String
)

@Serializable
data class SessionItemDto(
    val id: String,
    val title: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
    val model: String? = null,
    @SerialName("is_pinned") val isPinned: Boolean = false,
    @SerialName("is_archived") val isArchived: Boolean = false,
    @SerialName("last_message_preview") val lastMessagePreview: String? = null
)

@Serializable
data class SessionListResponseDto(
    val sessions: List<SessionItemDto> = emptyList()
)

/**
 * G4 (03 §D9б): элемент истории сообщений серверной сессии
 * (GET /api/sessions/{id}/messages). Формат восстановлен по полевым
 * данным маршрута get_session_messages; неизвестные поля игнорируются.
 */
@Serializable
data class SessionMessageDto(
    val id: String? = null,
    val role: String? = null,
    val content: String? = null,
    val text: String? = null,
    @SerialName("thinking") val thinking: String? = null,
    @SerialName("created_at") val createdAt: Long = 0L,
    @SerialName("timestamp") val timestamp: Long = 0L
)

@Serializable
data class HealthResponseDto(
    val ok: Boolean = false,
    val status: String? = null,
    val version: String? = null,
    @SerialName("mobile_plugin") val mobilePlugin: String? = null
)
