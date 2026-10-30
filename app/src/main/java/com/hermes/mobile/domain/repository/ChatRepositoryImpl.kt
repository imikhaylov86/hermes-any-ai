package com.hermes.mobile.domain.repository

import com.hermes.mobile.domain.model.*
import com.hermes.mobile.persistence.database.MessageDao
import com.hermes.mobile.persistence.database.MessageEntity
import com.hermes.mobile.persistence.database.SessionDao
import com.hermes.mobile.persistence.datastore.AppSettingsManager
import com.hermes.mobile.transport.dto.AttachmentDto
import com.hermes.mobile.transport.rest.DashboardRestClient
import com.hermes.mobile.transport.termux.TermuxRunner
import com.hermes.mobile.transport.websocket.MobileWebSocketClient
import com.hermes.mobile.transport.websocket.WebSocketIncomingEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class ChatRepositoryImpl(
    private val messageDao: MessageDao,
    private val sessionDao: SessionDao? = null,
    private val webSocketClient: MobileWebSocketClient,
    private val settingsManager: AppSettingsManager,
    private val restClient: DashboardRestClient? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : ChatRepository {

    private val _pendingApprovals = MutableStateFlow<List<ToolApprovalRequest>>(emptyList())
    private val _pendingClarifies = MutableStateFlow<List<ClarifyPrompt>>(emptyList())

    private val streamingBuffers = mutableMapOf<String, StringBuilder>()
    private val thinkingBuffers = mutableMapOf<String, StringBuilder>()
    private val stoppedSessionIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val stoppedMessageIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var demoStreamingJob: kotlinx.coroutines.Job? = null

    /**
     * G4 (03 §D9б): восстановление истории выполняется только при READY,
     * наступившем ПОСЛЕ разрыва (RECONNECTING/FAILED), а не при чистом старте
     * приложения — Room уже содержит актуальную историю.
     */
    private var hasConnectedOnce = false
    private var recoveryInFlight = false

    init {
        observeIncomingEvents()
        observeConnectionForRecovery()
        scope.launch {
            messageDao.completeAllStreamingMessages()
        }
        if (!settingsManager.isDemoMode.value) {
            webSocketClient.connect()
        }
    }

    /**
     * G4 (03 §D9б): при повторном установлении связи — восстановление истории
     * активной серверной сессии через REST и сверка с Room.
     */
    private fun observeConnectionForRecovery() {
        scope.launch {
            var previousState: ConnectionState? = null
            var failedAttempts = 0
            var autoStartTried = false
            webSocketClient.connectionState.collect { state ->
                val wasDisconnected = previousState == ConnectionState.RECONNECTING ||
                        previousState == ConnectionState.FAILED
                previousState = state
                if (state == ConnectionState.READY) {
                    if (hasConnectedOnce && wasDisconnected) {
                        recoverHistoryFromDaemon()
                    }
                    hasConnectedOnce = true
                    failedAttempts = 0
                    autoStartTried = false
                }
                if (state == ConnectionState.RECONNECTING) {
                    failedAttempts++
                }
                // G15 (полевой тест 30.10): демон Termux не переживает reboot телефона.
                // «Connection refused» на loopback = демона нет — приложение поднимает его
                // само (RUN_COMMAND от uid приложения — единственный рабочий канал;
                // adb/shell permission не имеет). Одна попытка на цикл, cooldown 3 попытки.
                if (failedAttempts >= 3 && !autoStartTried && isLoopbackEndpoint()) {
                    autoStartTried = true
                    android.util.Log.i("ChatRepository", "Daemon likely down (3 failed attempts, loopback) — auto-starting via Termux")
                    TermuxRunner.startHermesDaemonInBackground(
                        com.hermes.mobile.HermesApp.instance
                    )
                }
            }
        }
    }

    /** Loopback-эндпоинт: авто-старт демона допустим только для локального Termux-режима. */
    private fun isLoopbackEndpoint(): Boolean {
        val url = webSocketClient.connectionState.let { _ ->
            try {
                com.hermes.mobile.HermesApp.instance.settingsManager.serverUrl.value
            } catch (_: Exception) {
                "ws://127.0.0.1:8000/api/ws"
            }
        }
        return url.startsWith("ws://127.0.0.1") || url.startsWith("ws://localhost")
    }

    /**
     * G4 (03 §D9б): сверка Room ↔ REST-истории серверной сессии.
     * Room — source of truth для UI; отсутствующие серверные ответы добавляются,
     * локальные unsent без серверного подтверждения — повторная отправка.
     */
    private suspend fun recoverHistoryFromDaemon() {
        val rest = restClient ?: return
        val hermesSid = webSocketClient.currentHermesSessionId()
        val localSessionId = settingsManager.getLastActiveSessionId() ?: return
        if (hermesSid.isNullOrBlank() || recoveryInFlight) return
        recoveryInFlight = true
        try {
            val serverMessages = rest.getServerSessionMessages(hermesSid)
            if (serverMessages != null && serverMessages.isNotEmpty()) {
                val existingIds = messageDao.getMessagesListForSession(localSessionId)
                    .map { it.id }.toSet()
                var added = 0
                for (msg in serverMessages) {
                    val role = msg.role?.lowercase() ?: continue
                    if (role !in setOf("assistant", "user", "system")) continue
                    // Серверные assistant-ответы: идентификатор — серверный id
                    val serverId = msg.id
                    if (serverId != null && existingIds.contains(serverId)) continue
                    val content = msg.content ?: msg.text ?: continue
                    if (content.isBlank()) continue
                    val isAssistant = role == "assistant"
                    if (!isAssistant) continue // локальные user-копии уже в Room (02 §D4)
                    val entity = MessageEntity(
                        id = serverId ?: "srv_${msg.createdAt}_${added}",
                        sessionId = localSessionId,
                        role = MessageRole.ASSISTANT.name,
                        content = content,
                        thinkingContent = msg.thinking?.ifBlank { null },
                        timestampEpochMs = if (msg.createdAt > 0) msg.createdAt else System.currentTimeMillis(),
                        status = MessageStatus.COMPLETE.name
                    )
                    messageDao.insertMessage(entity)
                    added++
                }
                if (added > 0) {
                    android.util.Log.i("ChatRepository", "G4 recovery: restored $added server messages to session $localSessionId")
                }
            }
            // (в) повторная отправка unsent: локальные сообщения со статусом ERROR
            // от предыдущего разрыва — пользователь повторит вручную (retryTurn),
            // автоповтор исключён по 02 §Состояния п.1 (не создавать дубли)
        } catch (e: Exception) {
            android.util.Log.w("ChatRepository", "G4 recovery failed: ${e.message}")
        } finally {
            recoveryInFlight = false
        }
    }

    private fun extractThinkingAndContent(raw: String): Pair<String?, String> {
        val thinkRegex = Regex("<think>([\\s\\S]*?)(?:</think>|$)", RegexOption.IGNORE_CASE)
        val match = thinkRegex.find(raw)
        return if (match != null) {
            val thinking = match.groupValues[1].trim()
            val cleanContent = raw.replace(match.value, "").trim()
            (thinking.ifBlank { null }) to cleanContent
        } else {
            null to raw
        }
    }

    private fun observeIncomingEvents() {
        scope.launch {
            webSocketClient.incomingEvents.collect { event ->
                when (event) {
                    is WebSocketIncomingEvent.Ready -> {
                        // Соединение готово
                    }
                    is WebSocketIncomingEvent.ThinkingChunk -> {
                        if (stoppedSessionIds.contains(event.sessionId) || stoppedSessionIds.contains("sess_main") || stoppedMessageIds.contains(event.messageId)) {
                            return@collect
                        }
                        val tBuffer = thinkingBuffers.getOrPut(event.messageId) { StringBuilder() }
                        tBuffer.append(event.delta)
                        val aBuffer = streamingBuffers[event.messageId]?.toString() ?: ""

                        val entity = MessageEntity(
                            id = event.messageId,
                            sessionId = event.sessionId,
                            role = MessageRole.ASSISTANT.name,
                            content = aBuffer,
                            thinkingContent = tBuffer.toString(),
                            timestampEpochMs = System.currentTimeMillis(),
                            status = MessageStatus.STREAMING.name
                        )
                        messageDao.insertMessage(entity)
                    }
                    is WebSocketIncomingEvent.StreamChunk -> {
                        if (stoppedSessionIds.contains(event.sessionId) || stoppedSessionIds.contains("sess_main") || stoppedMessageIds.contains(event.messageId)) {
                            return@collect
                        }
                        val aBuffer = streamingBuffers.getOrPut(event.messageId) { StringBuilder() }
                        aBuffer.append(event.delta)

                        val rawContent = aBuffer.toString()
                        val (extractedThinking, cleanContent) = extractThinkingAndContent(rawContent)

                        val tBuffer = thinkingBuffers[event.messageId]
                        val activeThinking = when {
                            extractedThinking != null -> {
                                if (tBuffer != null && tBuffer.isNotBlank()) {
                                    "${tBuffer}\n$extractedThinking"
                                } else {
                                    extractedThinking
                                }
                            }
                            tBuffer != null -> tBuffer.toString()
                            else -> null
                        }

                        val entity = MessageEntity(
                            id = event.messageId,
                            sessionId = event.sessionId,
                            role = MessageRole.ASSISTANT.name,
                            content = cleanContent,
                            thinkingContent = activeThinking?.ifBlank { null },
                            timestampEpochMs = System.currentTimeMillis(),
                            status = MessageStatus.STREAMING.name
                        )
                        messageDao.insertMessage(entity)
                    }
                    is WebSocketIncomingEvent.Complete -> {
                        val wasStopped = stoppedSessionIds.contains(event.sessionId) || stoppedSessionIds.contains("sess_main") || stoppedMessageIds.contains(event.messageId)
                        val accumulatedThinking = thinkingBuffers.remove(event.messageId)?.toString()
                        streamingBuffers.remove(event.messageId)
                        if (wasStopped) {
                            return@collect
                        }

                        val (extractedThinking, cleanFinal) = extractThinkingAndContent(event.finalContent)
                        val finalThinking = event.finalThinking
                            ?: extractedThinking
                            ?: accumulatedThinking

                        val entity = MessageEntity(
                            id = event.messageId,
                            sessionId = event.sessionId,
                            role = MessageRole.ASSISTANT.name,
                            content = cleanFinal,
                            thinkingContent = finalThinking?.ifBlank { null },
                            timestampEpochMs = System.currentTimeMillis(),
                            status = MessageStatus.COMPLETE.name
                        )
                        messageDao.insertMessage(entity)
                    }
                    is WebSocketIncomingEvent.Approval -> {
                        _pendingApprovals.update { it + event.request }
                    }
                    is WebSocketIncomingEvent.Clarify -> {
                        _pendingClarifies.update { it + event.prompt }
                    }
                    is WebSocketIncomingEvent.Error -> {
                        val activeSessId = settingsManager?.getLastActiveSessionId() ?: "sess_main"
                        val fbModel = settingsManager?.fallbackModel?.value
                        val fbProvider = settingsManager?.fallbackProvider?.value
                        val activeModel = settingsManager?.activeModel?.value ?: ""

                        if (!fbModel.isNullOrBlank() && fbModel != activeModel) {
                            val notice = MessageEntity(
                                id = "fb_notice_${System.currentTimeMillis()}",
                                sessionId = activeSessId,
                                role = MessageRole.SYSTEM.name,
                                content = "⚠️ Основная модель ($activeModel) недоступна: ${event.message}.\nЗадействована резервная модель: $fbModel",
                                timestampEpochMs = System.currentTimeMillis(),
                                status = MessageStatus.COMPLETE.name
                            )
                            messageDao.insertMessage(notice)

                            settingsManager?.setActiveModel(fbModel)
                            webSocketClient.setActiveModel(fbModel, fbProvider ?: "")
                        } else {
                            val errEntity = MessageEntity(
                                id = "err_${System.currentTimeMillis()}",
                                sessionId = activeSessId,
                                role = MessageRole.ASSISTANT.name,
                                content = "⚠️ Ошибка агента: ${event.message}",
                                timestampEpochMs = System.currentTimeMillis(),
                                status = MessageStatus.ERROR.name,
                                errorMessage = event.message
                            )
                            messageDao.insertMessage(errEntity)
                        }
                    }
                }
            }
        }
    }

    override fun getMessagesFlow(sessionId: String): Flow<List<ChatMessage>> {
        return messageDao.getMessagesForSession(sessionId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * G6 (07 §G6): единый StateFlow состояния соединения через stateIn —
     * один коллектор, авто-отписка при отсутствии подписчиков (5с grace),
     * промежуточные состояния не теряются (в отличие от локального
     * MutableStateFlow с подпиской без отписки на каждый вызов).
     */
    private val connectionStateFlow: StateFlow<ConnectionState> =
        combine(settingsManager.isDemoMode, webSocketClient.connectionState) { isDemo, wsState ->
            if (isDemo) ConnectionState.READY else wsState
        }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = if (settingsManager.isDemoMode.value) ConnectionState.READY
            else webSocketClient.connectionState.value
        )

    override fun getConnectionStateFlow(): StateFlow<ConnectionState> = connectionStateFlow

    /**
     * G7 (07 §G7): явный жизненный цикл репозитория. Финализирует
     * STREAMING-сообщения (чтобы UI не застыл на «стриминге» после остановки)
     * и отменяет scope — все коллекторы (events, connection, stateIn) снимаются.
     */
    override fun shutdown() {
        if (scope.isActive) {
            scope.launch {
                try {
                    messageDao.completeAllStreamingMessages()
                } catch (e: Exception) {
                    android.util.Log.w("ChatRepository", "shutdown: finalize streaming failed: ${e.message}")
                }
            }
            // Даём финализации выполниться до отмены scope
            scope.launch {
                kotlinx.coroutines.delay(300)
                scope.cancel()
            }
        }
    }

    override fun getPendingApprovalsFlow(): Flow<List<ToolApprovalRequest>> {
        return _pendingApprovals.asStateFlow()
    }

    override fun getPendingClarifiesFlow(): Flow<List<ClarifyPrompt>> {
        return _pendingClarifies.asStateFlow()
    }

    override suspend fun sendMessage(sessionId: String, content: String, attachments: List<Attachment>) {
        stoppedSessionIds.remove(sessionId)
        stoppedSessionIds.remove("sess_main")
        stoppedMessageIds.clear()
        val userMsgId = UUID.randomUUID().toString()
        val attachJson = if (attachments.isNotEmpty()) {
            try {
                Json.encodeToString(attachments)
            } catch (e: Exception) {
                null
            }
        } else null

        val displayContent = if (content.isBlank() && attachments.isNotEmpty()) {
            attachments.joinToString(separator = "\n") { "📎 ${it.name}" }
        } else {
            content
        }

        val userEntity = MessageEntity(
            id = userMsgId,
            sessionId = sessionId,
            role = MessageRole.USER.name,
            content = displayContent,
            timestampEpochMs = System.currentTimeMillis(),
            status = MessageStatus.SENT.name,
            attachmentsJson = attachJson
        )
        messageDao.insertMessage(userEntity)

        if (sessionDao != null) {
            scope.launch {
                try {
                    val session = sessionDao.getSessionById(sessionId)
                    if (session != null && (session.title.isBlank() || session.title == "Новая беседа" || session.title.startsWith("sess_"))) {
                        val autoTitle = generateSessionTitle(content, attachments)
                        val preview = if (content.isNotBlank()) content.take(100) else (attachments.firstOrNull()?.name ?: "Вложение")
                        sessionDao.updateTitleAndPreview(
                            sessionId = sessionId,
                            newTitle = autoTitle,
                            preview = preview,
                            timestamp = System.currentTimeMillis()
                        )
                    }
                } catch (e: Exception) {
                    // Ignore auto-naming errors
                }
            }
        }

        if (settingsManager.isDemoMode.value) {
            handleDemoModeMessage(sessionId, displayContent)
            return
        }

        val attachDtos = attachments.map {
            AttachmentDto(
                name = it.name,
                mimeType = it.mimeType,
                uriOrPath = it.uriOrPath,
                sizeBytes = it.sizeBytes,
                dataUrl = it.dataUrl
            )
        }
        val assistantMsgId = "msg_asst_${System.currentTimeMillis()}"
        webSocketClient.sendMessage(
            messageId = assistantMsgId,
            sessionId = sessionId,
            content = content,
            attachments = attachDtos
        )
    }

    private fun handleDemoModeMessage(sessionId: String, content: String) {
        val lower = content.lowercase().trim()
        val asstId = "msg_asst_${System.currentTimeMillis()}"

        demoStreamingJob?.cancel()
        demoStreamingJob = scope.launch {
            delay(300)
            if (stoppedSessionIds.contains(sessionId)) return@launch

            when {
                lower.contains("/approval") || lower.contains("команда") || lower.contains("rm") -> {
                    val req = ToolApprovalRequest(
                        requestId = "demo_req_${System.currentTimeMillis()}",
                        sessionId = sessionId,
                        toolName = "run_terminal_command",
                        riskLevel = RiskLevel.CRITICAL,
                        parametersPreview = "rm -rf ~/.hermes/cache && pip install --upgrade nous-hermes",
                        canAlwaysAllow = true,
                        createdAtEpochMs = System.currentTimeMillis()
                    )
                    _pendingApprovals.update { it + req }
                }
                lower.contains("/clarify") || lower.contains("уточнение") || lower.contains("выбор") -> {
                    val prompt = ClarifyPrompt(
                        promptId = "demo_clr_${System.currentTimeMillis()}",
                        sessionId = sessionId,
                        question = "В каком режиме настроить автономную работу агента?",
                        options = listOf(
                            "Фоновая служба (максимальная автономность)",
                            "Энергосберегающий режим (только активный экран)",
                            "Только по расписанию crontab"
                        ),
                        isMultiChoice = false,
                        createdAtEpochMs = System.currentTimeMillis()
                    )
                    _pendingClarifies.update { it + prompt }
                }
                else -> {
                    val thinkingSteps = listOf(
                        "Анализирую входящий контекст и системные параметры...\n",
                        "Проверяю доступные инструменты и статус окружения...\n",
                        "Готовлю структурированный ответ пользователю."
                    )
                    var accumulatedThinking = ""
                    for (tStep in thinkingSteps) {
                        if (stoppedSessionIds.contains(sessionId)) return@launch
                        accumulatedThinking += tStep
                        val entity = MessageEntity(
                            id = asstId,
                            sessionId = sessionId,
                            role = MessageRole.ASSISTANT.name,
                            content = "",
                            thinkingContent = accumulatedThinking,
                            timestampEpochMs = System.currentTimeMillis(),
                            status = MessageStatus.STREAMING.name
                        )
                        messageDao.insertMessage(entity)
                        delay(120)
                    }

                    val chunks = listOf(
                        "Здравствуйте! Я автономный агент **Hermes**, работающий прямо на этом устройстве.\n\n",
                        "### Режим: Автономный офлайн-симулятор\n",
                        "- **Модель:** ${settingsManager.activeModel.value}\n",
                        "- **Шифрование:** Android Keystore (AES-256 GCM)\n",
                        "- **Локальная база:** SQLite Room (кэш сообщений)\n\n",
                        "```kotlin\n// Пример вызова агента в коде\nval agent = HermesAgent.getInstance()\nagent.execute(prompt = \"$content\")\n```\n\n",
                        "Все системы функционируют в штатном режиме! Чем могу помочь?"
                    )
                    var accumulated = ""
                    for (chunk in chunks) {
                        if (stoppedSessionIds.contains(sessionId)) {
                            return@launch
                        }
                        accumulated += chunk
                        val entity = MessageEntity(
                            id = asstId,
                            sessionId = sessionId,
                            role = MessageRole.ASSISTANT.name,
                            content = accumulated,
                            thinkingContent = accumulatedThinking,
                            timestampEpochMs = System.currentTimeMillis(),
                            status = MessageStatus.STREAMING.name
                        )
                        messageDao.insertMessage(entity)
                        delay(70)
                        if (stoppedSessionIds.contains(sessionId)) {
                            return@launch
                        }
                    }
                    if (stoppedSessionIds.contains(sessionId)) {
                        return@launch
                    }
                    val finalEntity = MessageEntity(
                        id = asstId,
                        sessionId = sessionId,
                        role = MessageRole.ASSISTANT.name,
                        content = accumulated,
                        thinkingContent = accumulatedThinking,
                        timestampEpochMs = System.currentTimeMillis(),
                        status = MessageStatus.COMPLETE.name
                    )
                    messageDao.insertMessage(finalEntity)
                }
            }
        }
    }

    override suspend fun stopGeneration(sessionId: String) {
        stoppedSessionIds.add(sessionId)
        stoppedSessionIds.add("sess_main")
        demoStreamingJob?.cancel()
        demoStreamingJob = null
        streamingBuffers.keys.toList().forEach { stoppedMessageIds.add(it) }
        streamingBuffers.clear()
        messageDao.completeStreamingMessages(sessionId)
        messageDao.completeStreamingMessages("sess_main")
        messageDao.completeAllStreamingMessages()
        if (!settingsManager.isDemoMode.value) {
            webSocketClient.stopGeneration(sessionId)
        }
    }

    override suspend fun respondToApproval(requestId: String, decision: ApprovalDecision) {
        val request = _pendingApprovals.value.find { it.requestId == requestId } ?: return

        // Фиксируем решение пользователя и время ответа прямо в карточке
        val updatedRequest = request.copy(
            isResponded = true,
            userDecision = decision,
            respondedAtEpochMs = System.currentTimeMillis()
        )
        _pendingApprovals.update { list ->
            list.map { if (it.requestId == requestId) updatedRequest else it }
        }

        if (settingsManager.isDemoMode.value) {
            val asstId = "msg_asst_${System.currentTimeMillis()}"
            val messageText = when (decision) {
                ApprovalDecision.APPROVE ->
                    "✓ Действие разрешено вами.\nКоманда `${request.parametersPreview}` выполнена успешно."
                ApprovalDecision.ALWAYS_ALLOW ->
                    "✓ Действие разрешено (всегда для этой команды).\nПравило для `${request.toolName}` сохранено."
                ApprovalDecision.DENY ->
                    "✕ Действие отклонено вами.\nВыполнение команды отменено."
            }
            val entity = MessageEntity(
                id = asstId,
                sessionId = request.sessionId,
                role = MessageRole.ASSISTANT.name,
                content = messageText,
                timestampEpochMs = System.currentTimeMillis(),
                status = MessageStatus.COMPLETE.name
            )
            messageDao.insertMessage(entity)
            return
        }

        webSocketClient.respondToApproval(requestId, request.sessionId, decision)
    }

    override suspend fun respondToClarify(promptId: String, answer: String) {
        val prompt = _pendingClarifies.value.find { it.promptId == promptId } ?: return
        _pendingClarifies.update { list -> list.filterNot { it.promptId == promptId } }

        if (settingsManager.isDemoMode.value) {
            val asstId = "msg_asst_${System.currentTimeMillis()}"
            val entity = MessageEntity(
                id = asstId,
                sessionId = prompt.sessionId,
                role = MessageRole.ASSISTANT.name,
                content = "✓ Принято: Выбран режим «$answer». Конфигурация успешно сохранена.",
                timestampEpochMs = System.currentTimeMillis(),
                status = MessageStatus.COMPLETE.name
            )
            messageDao.insertMessage(entity)
            return
        }

        webSocketClient.respondToClarify(promptId, prompt.sessionId, answer)
    }

    override suspend fun retryTurn(sessionId: String, messageId: String) {
        val targetMsg = messageDao.getMessageById(messageId)?.toDomain()
        val userMsg = if (targetMsg != null && targetMsg.role == MessageRole.USER) {
            targetMsg
        } else {
            messageDao.getLastUserMessage(sessionId)?.toDomain()
        }

        // Удаляем неудавшееся сообщение ассистента с ошибкой, чтобы не дублировать
        if (targetMsg != null && targetMsg.role == MessageRole.ASSISTANT && targetMsg.status == MessageStatus.ERROR) {
            messageDao.deleteMessage(targetMsg.id)
        }

        if (userMsg != null) {
            sendMessage(sessionId, userMsg.content, userMsg.attachments)
        }
    }

    override suspend fun clearSessionMessages(sessionId: String) {
        messageDao.deleteMessagesForSession(sessionId)
        _pendingApprovals.update { list -> list.filterNot { it.sessionId == sessionId } }
        _pendingClarifies.update { list -> list.filterNot { it.sessionId == sessionId } }
    }
}
