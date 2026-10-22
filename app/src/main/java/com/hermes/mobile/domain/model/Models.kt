package com.hermes.mobile.domain.model

import kotlinx.serialization.Serializable

/**
 * Роли отправителей сообщений
 */
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

/**
 * Статус доставки и генерации сообщения
 */
enum class MessageStatus {
    SENDING,
    SENT,
    STREAMING,
    COMPLETE,
    ERROR
}

/**
 * Вложение (изображение или документ)
 */
@Serializable
data class Attachment(
    val name: String,
    val mimeType: String,
    val uriOrPath: String,
    val sizeBytes: Long = 0,
    val dataUrl: String? = null
)

/**
 * Сообщение в чате
 */
data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.COMPLETE,
    val attachments: List<Attachment> = emptyList(),
    val errorMessage: String? = null,
    val thinkingContent: String? = null
)

/**
 * Метаданные беседы (сессии)
 */
data class SessionInfo(
    val id: String,
    val title: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val model: String = "Hermes 3",
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val lastMessagePreview: String = ""
)

/**
 * Уровни риска вызова инструментов агента
 */
enum class RiskLevel {
    LOW,
    MEDIUM,
    CRITICAL
}

/**
 * Решение пользователя по вызову инструмента
 */
enum class ApprovalDecision {
    APPROVE,
    DENY,
    ALWAYS_ALLOW
}

/**
 * Запрос подтверждения вызова инструмента (Tool Approval)
 */
data class ToolApprovalRequest(
    val requestId: String,
    val sessionId: String,
    val toolName: String,
    val riskLevel: RiskLevel,
    val parametersPreview: String,
    val canAlwaysAllow: Boolean = false,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val isResponded: Boolean = false,
    val userDecision: ApprovalDecision? = null,
    val respondedAtEpochMs: Long? = null
)

/**
 * Уточняющий вопрос агента (Clarify)
 */
data class ClarifyPrompt(
    val promptId: String,
    val sessionId: String,
    val question: String,
    val options: List<String> = emptyList(),
    val isMultiChoice: Boolean = false,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val isAnswered: Boolean = false,
    val userAnswer: String? = null
)

/**
 * Статус подключения провайдера по спецификации providers.schema.json
 */
enum class ProviderStatus {
    CONNECTED,
    DISCONNECTED,
    ERROR,
    AUTH_REQUIRED
}

enum class ProviderApiMode(
    val id: String,
    val displayName: String,
    val defaultUrl: String,
    val description: String
) {
    OPENAI_COMPATIBLE(
        id = "chat_completions",
        displayName = "OpenAI Compatible",
        defaultUrl = "https://api.openai.com/v1",
        description = "vLLM, Ollama, LM Studio, DeepSeek, OpenRouter, Groq, Together"
    ),
    ANTHROPIC_MESSAGES(
        id = "anthropic_messages",
        displayName = "Anthropic Messages",
        defaultUrl = "https://api.anthropic.com/v1",
        description = "Claude API и совместимые прокси-шлюзы"
    ),
    OLLAMA_NATIVE(
        id = "ollama",
        displayName = "Ollama Native",
        defaultUrl = "http://127.0.0.1:11434/api",
        description = "Локальный инстанс Ollama без прокси"
    ),
    GOOGLE_GEMINI(
        id = "gemini",
        displayName = "Google Gemini",
        defaultUrl = "https://generativelanguage.googleapis.com/v1beta",
        description = "Google AI Studio API"
    );

    companion object {
        fun fromId(id: String?): ProviderApiMode {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: OPENAI_COMPATIBLE
        }
    }
}

/**
 * Провайдер AI-моделей
 */
data class ProviderConfig(
    val id: String,
    val name: String,
    val status: ProviderStatus = ProviderStatus.CONNECTED,
    val currentModel: String? = null,
    val availableModels: List<String> = emptyList(),
    val hasApiKey: Boolean = false,
    val baseUrl: String? = null,
    val apiMode: ProviderApiMode = ProviderApiMode.OPENAI_COMPATIBLE,
    val isCustom: Boolean = false
) {
    val isConnected: Boolean get() = status == ProviderStatus.CONNECTED
}

/**
 * Статус соединения с агентом
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATING,
    READY,
    RECONNECTING,
    FAILED
}

/**
 * Элемент слэш-команды агента
 */
@Serializable
data class SlashCommandItem(
    val command: String,
    val title: String,
    val description: String,
    val example: String = command,
    val actionId: String = "",
    val isCustom: Boolean = false
)

