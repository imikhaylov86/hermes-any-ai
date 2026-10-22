package com.hermes.mobile.domain.repository

import com.hermes.mobile.domain.model.*
import com.hermes.mobile.persistence.database.MessageDao
import com.hermes.mobile.persistence.database.MessageEntity
import com.hermes.mobile.persistence.database.SessionDao
import com.hermes.mobile.persistence.datastore.AppSettingsManager
import com.hermes.mobile.transport.dto.AttachmentDto
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
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : ChatRepository {

    private val _pendingApprovals = MutableStateFlow<List<ToolApprovalRequest>>(emptyList())
    private val _pendingClarifies = MutableStateFlow<List<ClarifyPrompt>>(emptyList())

    private val streamingBuffers = mutableMapOf<String, StringBuilder>()
    private val thinkingBuffers = mutableMapOf<String, StringBuilder>()
    private val stoppedSessionIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val stoppedMessageIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var demoStreamingJob: kotlinx.coroutines.Job? = null

    init {
        observeIncomingEvents()
        scope.launch {
            messageDao.completeAllStreamingMessages()
        }
        if (!settingsManager.isDemoMode.value) {
            webSocketClient.connect()
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

    override fun getConnectionStateFlow(): StateFlow<ConnectionState> {
        val state = MutableStateFlow(
            if (settingsManager.isDemoMode.value) ConnectionState.READY else webSocketClient.connectionState.value
        )
        scope.launch {
            combine(settingsManager.isDemoMode, webSocketClient.connectionState) { isDemo, wsState ->
                if (isDemo) ConnectionState.READY else wsState
            }.collect {
                state.value = it
            }
        }
        return state.asStateFlow()
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
