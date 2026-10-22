package com.hermes.mobile.domain.repository

import com.hermes.mobile.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ChatRepository {
    fun getMessagesFlow(sessionId: String): Flow<List<ChatMessage>>
    fun getConnectionStateFlow(): StateFlow<ConnectionState>
    fun getPendingApprovalsFlow(): Flow<List<ToolApprovalRequest>>
    fun getPendingClarifiesFlow(): Flow<List<ClarifyPrompt>>

    suspend fun sendMessage(sessionId: String, content: String, attachments: List<Attachment> = emptyList())
    suspend fun stopGeneration(sessionId: String)
    suspend fun respondToApproval(requestId: String, decision: ApprovalDecision)
    suspend fun respondToClarify(promptId: String, answer: String)
    suspend fun retryTurn(sessionId: String, messageId: String)
    suspend fun clearSessionMessages(sessionId: String)
}

interface SessionRepository {
    fun getSessionsFlow(): Flow<List<SessionInfo>>
    suspend fun getSessionById(sessionId: String): SessionInfo?
    suspend fun getLatestSession(): SessionInfo?
    suspend fun createSession(title: String = "Новая беседа", model: String = "Hermes 3"): SessionInfo
    suspend fun updateSessionTitle(sessionId: String, newTitle: String)
    suspend fun togglePinSession(sessionId: String)
    suspend fun toggleArchiveSession(sessionId: String)
    suspend fun deleteSession(sessionId: String)
}

interface RuntimeRepository {
    suspend fun checkHealth(): Boolean
    fun getRuntimeStatusFlow(): StateFlow<Boolean>
}

data class EndpointTestResult(
    val latencyMs: Long,
    val models: List<String> = emptyList()
)

interface ProviderRepository {
    val providers: StateFlow<List<ProviderConfig>>
    val isLoading: StateFlow<Boolean>
    val error: StateFlow<String?>

    suspend fun refreshProviders(): Result<List<ProviderConfig>>
    suspend fun setActiveModel(model: String, providerId: String? = null): Result<Unit>
    suspend fun saveApiKey(providerId: String, apiKey: String): Result<Unit>
    suspend fun testConnection(providerId: String): Result<Long>
    suspend fun addCustomProvider(
        name: String,
        apiMode: ProviderApiMode,
        baseUrl: String,
        apiKey: String,
        models: List<String> = emptyList()
    ): Result<ProviderConfig>
    suspend fun deleteProvider(providerId: String): Result<Unit>
    suspend fun testCustomEndpoint(
        baseUrl: String,
        apiMode: ProviderApiMode,
        apiKey: String,
        name: String = ""
    ): Result<EndpointTestResult>
    suspend fun runDiagnostics(): DiagnosticsReport
}
