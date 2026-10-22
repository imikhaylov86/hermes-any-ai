package com.hermes.mobile.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.mobile.HermesApp
import com.hermes.mobile.domain.backup.ChatBackupManager
import com.hermes.mobile.domain.model.*
import com.hermes.mobile.domain.repository.ChatRepository
import com.hermes.mobile.domain.repository.SessionRepository
import com.hermes.mobile.persistence.datastore.AppSettingsManager
import com.hermes.mobile.security.KeystoreManager
import com.hermes.mobile.transport.websocket.MobileWebSocketClient
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val sessionRepository: SessionRepository,
    private val settingsManager: AppSettingsManager,
    private val keystoreManager: KeystoreManager,
    private val webSocketClient: MobileWebSocketClient,
    private val providerRepository: com.hermes.mobile.domain.repository.ProviderRepository,
    private val restClient: com.hermes.mobile.transport.rest.DashboardRestClient,
    initialSessionId: String? = null
) : ViewModel() {

    private val _activeSessionId = MutableStateFlow(initialSessionId ?: (settingsManager.getLastActiveSessionId() ?: ""))
    val activeSessionId: StateFlow<String> = _activeSessionId.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeSession: StateFlow<SessionInfo?> = _activeSessionId
        .flatMapLatest { id ->
            if (id.isBlank()) flowOf(null)
            else sessionRepository.getSessionsFlow().map { list -> list.find { it.id == id } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val connectionState: StateFlow<ConnectionState> = chatRepository.getConnectionStateFlow()
    val isDemoMode: StateFlow<Boolean> = settingsManager.isDemoMode
    val activeModel: StateFlow<String> = settingsManager.activeModel
    val activeProvider: StateFlow<String> = settingsManager.activeProvider
    val activeProviderName: StateFlow<String> = settingsManager.activeProviderName
    val fallbackModel: StateFlow<String> = settingsManager.fallbackModel
    val fallbackProvider: StateFlow<String> = settingsManager.fallbackProvider
    val serverUrl: StateFlow<String> = settingsManager.serverUrl

    fun setFallbackModel(providerId: String, model: String) {
        val provider = providers.value.find { it.id == providerId }
        val provName = provider?.name ?: providerId
        settingsManager.setFallbackModelAndProvider(model, providerId, provName)
    }

    fun clearFallbackModel() {
        settingsManager.clearFallbackModel()
    }

    val providers: StateFlow<List<ProviderConfig>> = providerRepository.providers
    val isProvidersLoading: StateFlow<Boolean> = providerRepository.isLoading
    val providersError: StateFlow<String?> = providerRepository.error

    init {
        refreshProviders()
        initActiveSession()
    }

    private fun initActiveSession() {
        viewModelScope.launch {
            val savedId = settingsManager.getLastActiveSessionId()
            if (!savedId.isNullOrBlank()) {
                val existing = sessionRepository.getSessionById(savedId)
                if (existing != null) {
                    _activeSessionId.value = existing.id
                    return@launch
                }
            }

            // Если сохранённой сессии нет или она была удалена, берем последнюю из базы
            val latest = sessionRepository.getLatestSession()
            if (latest != null) {
                _activeSessionId.value = latest.id
                settingsManager.setLastActiveSessionId(latest.id)
            } else {
                // Если база сессий пуста — создаем новую сессию
                val newSess = sessionRepository.createSession("Новая беседа")
                _activeSessionId.value = newSess.id
                settingsManager.setLastActiveSessionId(newSess.id)
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<ChatMessage>> = _activeSessionId
        .flatMapLatest { sessionId -> chatRepository.getMessagesFlow(sessionId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingApprovals: StateFlow<List<ToolApprovalRequest>> = chatRepository.getPendingApprovalsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingClarifies: StateFlow<List<ClarifyPrompt>> = chatRepository.getPendingClarifiesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inputText = MutableStateFlow("")
    val attachedFiles = MutableStateFlow<List<Attachment>>(emptyList())

    // Хранилище неотправленных черновиков сообщений и вложений для каждой сессии
    private val sessionDrafts = mutableMapOf<String, Pair<String, List<Attachment>>>()

    fun selectSession(sessionId: String) {
        val prevSession = _activeSessionId.value
        if (prevSession.isNotBlank()) {
            // Сохраняем черновик предыдущей сессии
            sessionDrafts[prevSession] = inputText.value to attachedFiles.value
        }

        _activeSessionId.value = sessionId
        settingsManager.setLastActiveSessionId(sessionId)
        webSocketClient.resetHermesSession()

        // Восстанавливаем сохранённый черновик для выбранной сессии
        val draft = sessionDrafts[sessionId]
        inputText.value = draft?.first ?: ""
        attachedFiles.value = draft?.second ?: emptyList()
    }

    fun onActiveSessionDeleted() {
        viewModelScope.launch {
            val latest = sessionRepository.getLatestSession()
            if (latest != null) {
                selectSession(latest.id)
            } else {
                val newSess = sessionRepository.createSession("Новая беседа")
                selectSession(newSess.id)
            }
        }
    }

    fun sendMessage() {
        val text = inputText.value.trim()
        val attachments = attachedFiles.value
        val cleanCmd = text.trim().lowercase()
        if (cleanCmd == "/status" || cleanCmd == "/diagnose" || cleanCmd == "/диагностика") {
            inputText.value = ""
            showDiagnosticsDialog.value = true
            runDiagnostics()
            return
        }

        val currentSession = _activeSessionId.value
        sessionDrafts.remove(currentSession) // Очищаем черновик после отправки
        viewModelScope.launch {
            chatRepository.sendMessage(currentSession, text, attachments)
            inputText.value = ""
            attachedFiles.value = emptyList()
        }
    }

    fun retryMessage(messageId: String) {
        viewModelScope.launch {
            chatRepository.retryTurn(_activeSessionId.value, messageId)
        }
    }

    fun renameActiveSession(newTitle: String) {
        val currId = _activeSessionId.value
        val clean = newTitle.trim()
        if (currId.isNotBlank() && clean.isNotBlank()) {
            viewModelScope.launch {
                sessionRepository.updateSessionTitle(currId, clean)
            }
        }
    }

    val slashCommands: StateFlow<List<SlashCommandItem>> = settingsManager.slashCommands

    fun addSlashCommand(command: SlashCommandItem) {
        settingsManager.addSlashCommand(command)
    }

    fun updateSlashCommand(oldCommandStr: String, updated: SlashCommandItem) {
        settingsManager.updateSlashCommand(oldCommandStr, updated)
    }

    fun moveSlashCommand(fromIndex: Int, toIndex: Int) {
        settingsManager.moveSlashCommand(fromIndex, toIndex)
    }

    fun deleteSlashCommand(commandStr: String) {
        settingsManager.deleteSlashCommand(commandStr)
    }

    fun resetSlashCommandsToDefault() {
        settingsManager.resetSlashCommandsToDefault()
    }

    fun executeSlashCommand(command: SlashCommandItem) {
        when (command.actionId) {
            "model" -> {
                inputText.value = ""
                showProviderSheet.value = true
            }
            "clear" -> {
                inputText.value = ""
                clearCurrentChat()
            }
            "status", "diagnose" -> {
                inputText.value = ""
                showDiagnosticsDialog.value = true
                runDiagnostics()
            }
            "reset" -> {
                inputText.value = ""
                webSocketClient.resetHermesSession()
                viewModelScope.launch {
                    chatRepository.sendMessage(_activeSessionId.value, "/reset")
                }
            }
            else -> {
                val toInsert = if (command.example.endsWith(" ")) {
                    command.example
                } else if (command.example.isNotBlank()) {
                    "${command.example} "
                } else {
                    "${command.command} "
                }
                inputText.value = toInsert
            }
        }
    }

    fun addAttachment(attachment: Attachment) {
        attachedFiles.update { it + attachment }
    }

    fun removeAttachment(attachment: Attachment) {
        attachedFiles.update { list -> list.filterNot { it.uriOrPath == attachment.uriOrPath } }
    }

    fun stopGeneration() {
        viewModelScope.launch {
            chatRepository.stopGeneration(_activeSessionId.value)
        }
    }

    fun respondToApproval(requestId: String, decision: ApprovalDecision) {
        viewModelScope.launch {
            chatRepository.respondToApproval(requestId, decision)
        }
    }

    fun respondToClarify(promptId: String, answer: String) {
        viewModelScope.launch {
            chatRepository.respondToClarify(promptId, answer)
        }
    }

    fun setDemoMode(enabled: Boolean) {
        settingsManager.setDemoMode(enabled)
        if (!enabled) {
            webSocketClient.connect()
        }
        refreshProviders()
    }

    fun setServerUrl(newUrl: String) {
        settingsManager.setServerUrl(newUrl)
        webSocketClient.updateEndpoint(newUrl)
        restClient.updateBaseUrl(settingsManager.getRestUrl())
        restClient.updateSessionToken(settingsManager.getSessionToken())
        refreshProviders()
    }

    val appLanguage: StateFlow<String> = settingsManager.appLanguage

    fun setAppLanguage(lang: String) {
        settingsManager.setAppLanguage(lang)
    }

    val isFirstLaunch: Boolean get() = settingsManager.isFirstLaunch()

    fun setFirstLaunchDone() {
        settingsManager.setFirstLaunchDone()
    }

    fun isTermuxInstalled(context: android.content.Context): Boolean {
        return com.hermes.mobile.transport.termux.TermuxRunner.isTermuxInstalled(context)
    }

    fun startTermuxDaemon(context: android.content.Context, onResult: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            val res = com.hermes.mobile.transport.termux.TermuxRunner.startHermesDaemonInBackground(context)
            res.onSuccess {
                onResult?.invoke(true, null)
                kotlinx.coroutines.delay(1500)
                webSocketClient.connect()
            }.onFailure { err ->
                onResult?.invoke(false, err.message)
            }
        }
    }

    fun reconnect() {
        webSocketClient.connect()
    }

    fun refreshProviders() {
        viewModelScope.launch {
            providerRepository.refreshProviders()
        }
    }

    fun setActiveModel(model: String, providerId: String? = null, onResult: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            val res = providerRepository.setActiveModel(model, providerId)
            res.onSuccess {
                onResult?.invoke(true, null)
            }.onFailure { err ->
                onResult?.invoke(false, err.message)
            }
        }
    }

    fun saveApiKey(providerId: String, apiKey: String) {
        viewModelScope.launch {
            providerRepository.saveApiKey(providerId, apiKey)
        }
    }

    fun testProviderConnection(providerId: String, onResult: (Boolean, Long, String?) -> Unit) {
        viewModelScope.launch {
            val res = providerRepository.testConnection(providerId)
            res.onSuccess { latency ->
                onResult(true, latency, null)
            }.onFailure { err ->
                onResult(false, 0, err.message)
            }
        }
    }

    fun addCustomProvider(
        name: String,
        apiMode: ProviderApiMode,
        baseUrl: String,
        apiKey: String,
        models: List<String> = emptyList(),
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            val res = providerRepository.addCustomProvider(name, apiMode, baseUrl, apiKey, models)
            res.onSuccess {
                onResult(true, null)
            }.onFailure { err ->
                onResult(false, err.message)
            }
        }
    }

    fun deleteProvider(providerId: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val res = providerRepository.deleteProvider(providerId)
            res.onSuccess {
                onResult(true, null)
            }.onFailure { err ->
                onResult(false, err.message)
            }
        }
    }

    fun testCustomEndpoint(
        baseUrl: String,
        apiMode: ProviderApiMode,
        apiKey: String,
        name: String = "",
        onResult: (Boolean, Long, String?, List<String>) -> Unit
    ) {
        viewModelScope.launch {
            val res = providerRepository.testCustomEndpoint(baseUrl, apiMode, apiKey, name)
            res.onSuccess { data ->
                onResult(true, data.latencyMs, null, data.models)
            }.onFailure { err ->
                onResult(false, 0, err.message, emptyList())
            }
        }
    }

    fun getApiKey(keyName: String): String? {
        return keystoreManager.getDecrypted(keyName)
    }

    fun clearCurrentChat() {
        viewModelScope.launch {
            chatRepository.clearSessionMessages(_activeSessionId.value)
        }
    }

    val showProviderSheet = MutableStateFlow(false)
    val showDiagnosticsDialog = MutableStateFlow(false)

    private val _diagnosticsReport = MutableStateFlow<DiagnosticsReport?>(null)
    val diagnosticsReport: StateFlow<DiagnosticsReport?> = _diagnosticsReport.asStateFlow()

    private val _isDiagnosticsRunning = MutableStateFlow(false)
    val isDiagnosticsRunning: StateFlow<Boolean> = _isDiagnosticsRunning.asStateFlow()

    fun runDiagnostics() {
        viewModelScope.launch {
            _isDiagnosticsRunning.value = true
            try {
                val report = providerRepository.runDiagnostics()
                _diagnosticsReport.value = report
            } catch (e: Exception) {
                // Safe error capture
            } finally {
                _isDiagnosticsRunning.value = false
            }
        }
    }

    fun clearDiagnosticsReport() {
        _diagnosticsReport.value = null
    }

    /**
     * Экспорт текущей открытой беседы в Markdown через системный Share
     */
    fun exportCurrentSessionMarkdown(context: Context) {
        viewModelScope.launch {
            val sessId = _activeSessionId.value
            if (sessId.isBlank()) return@launch
            val session = sessionRepository.getSessionById(sessId) ?: return@launch
            val currentMessages = messages.value
            val md = ChatBackupManager.formatSessionToMarkdown(session, currentMessages)
            ChatBackupManager.shareMarkdown(context, session.title, md)
        }
    }

    /**
     * Экспорт полной резервной копии всех бесед и сообщений в JSON
     */
    fun exportFullBackup(context: Context) {
        viewModelScope.launch {
            try {
                val db = HermesApp.instance.database
                val jsonStr = ChatBackupManager.createFullBackupJson(db.sessionDao(), db.messageDao())
                ChatBackupManager.shareBackupJson(context, jsonStr)
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Ошибка бэкапа: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Восстановление базы бесед из JSON резервной копии
     */
    fun restoreBackupFromJson(context: Context, jsonString: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val db = HermesApp.instance.database
                val (sessionsCount, messagesCount) = ChatBackupManager.restoreFromJson(
                    backupJson = jsonString,
                    sessionDao = db.sessionDao(),
                    messageDao = db.messageDao()
                )
                val latest = sessionRepository.getLatestSession()
                if (latest != null) {
                    _activeSessionId.value = latest.id
                }
                onResult(true, "Восстановлено $sessionsCount бесед ($messagesCount сообщений)")
            } catch (e: Exception) {
                onResult(false, "Ошибка импорта: ${e.message}")
            }
        }
    }
}

class SessionViewModel(
    private val sessionRepository: SessionRepository
) : ViewModel() {

    val searchQuery = MutableStateFlow("")

    val sessions: StateFlow<List<SessionInfo>> = combine(
        sessionRepository.getSessionsFlow(),
        searchQuery
    ) { list, query ->
        if (query.isBlank()) list
        else list.filter { it.title.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createSession(title: String = "Новая беседа", onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val session = sessionRepository.createSession(title)
            onCreated(session.id)
        }
    }

    fun togglePin(sessionId: String) {
        viewModelScope.launch { sessionRepository.togglePinSession(sessionId) }
    }

    fun toggleArchive(sessionId: String) {
        viewModelScope.launch { sessionRepository.toggleArchiveSession(sessionId) }
    }

    fun renameSession(sessionId: String, newTitle: String) {
        val clean = newTitle.trim()
        if (clean.isNotBlank()) {
            viewModelScope.launch { sessionRepository.updateSessionTitle(sessionId, clean) }
        }
    }

    fun deleteSession(sessionId: String, onDeleted: (() -> Unit)? = null) {
        viewModelScope.launch {
            sessionRepository.deleteSession(sessionId)
            onDeleted?.invoke()
        }
    }

    fun exportSessionMarkdown(context: Context, sessionId: String) {
        viewModelScope.launch {
            val session = sessionRepository.getSessionById(sessionId) ?: return@launch
            val db = HermesApp.instance.database
            val msgs = db.messageDao().getMessagesListForSession(sessionId).map { it.toDomain() }
            val md = ChatBackupManager.formatSessionToMarkdown(session, msgs)
            ChatBackupManager.shareMarkdown(context, session.title, md)
        }
    }
}
