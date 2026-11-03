package com.hermes.mobile.transport.websocket

import android.util.Log
import com.hermes.mobile.domain.model.ApprovalDecision
import com.hermes.mobile.domain.model.ClarifyPrompt
import com.hermes.mobile.domain.model.ConnectionState
import com.hermes.mobile.domain.model.RiskLevel
import com.hermes.mobile.domain.model.ToolApprovalRequest
import com.hermes.mobile.transport.dto.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
import okhttp3.*
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed class WebSocketIncomingEvent {
    data class Ready(val sessionId: String, val activeModel: String) : WebSocketIncomingEvent()
    data class StreamChunk(val messageId: String, val sessionId: String, val delta: String, val sequence: Int) : WebSocketIncomingEvent()
    data class ThinkingChunk(val messageId: String, val sessionId: String, val delta: String, val sequence: Int) : WebSocketIncomingEvent()
    data class Complete(val messageId: String, val sessionId: String, val finalContent: String, val finalThinking: String? = null) : WebSocketIncomingEvent()
    data class Approval(val request: ToolApprovalRequest) : WebSocketIncomingEvent()
    data class Clarify(val prompt: ClarifyPrompt) : WebSocketIncomingEvent()
    data class Error(val code: String, val message: String) : WebSocketIncomingEvent()
}

class MobileWebSocketClient(
    @Volatile private var endpointUrl: String = "ws://127.0.0.1:8000/api/ws",
    private val deviceId: String = "android_device_local",
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val tag = "MobileWebSocketClient"

    /** Вызывается, когда реальный сессионный токен получен из dashboard (fallback при 403). */
    var onSessionTokenRecovered: ((String) -> Unit)? = null

    /**
     * G4 (03 §D9г): вызывается composition root'ом при установлении новой серверной
     * сессии — для персиста hermesSessionId в хранилище.
     */
    var onHermesSessionEstablished: ((String) -> Unit)? = null

    /**
     * G17 (03 §D10): вызывается при создании или сбросе серверной сессии для
     * конкретного локального чата (chatId) — для персиста hermesSessionId в Room.
     */
    var onHermesSessionCreatedForChat: ((chatId: String, hermesSessionId: String?) -> Unit)? = null

    /**
     * G3 (docs/03 §D6): сессионный токен передаётся заголовком X-Hermes-Session-Token
     * в WS upgrade, а не в URL. URL хранится без query-токена; query-fallback
     * применяется только для loopback при отказе handshake.
     */
    @Volatile
    private var sessionToken: String = "hermes_mobile_local"

    /** loopback-эндпоинт (query-fallback допустим только для него, 03 §D6 п.3). */
    private fun isLoopback(): Boolean {
        val u = endpointUrl.lowercase()
        return u.startsWith("ws://127.0.0.1") || u.startsWith("ws://localhost")
    }

    fun updateSessionToken(token: String) {
        if (token.isNotBlank() && token != sessionToken) {
            Log.i(tag, "Session token updated (header-auth)")
            sessionToken = token
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // infinite for WS
        .build()

    @Volatile
    private var webSocket: WebSocket? = null
    @Volatile
    private var isIntentionallyClosed = false

    private var connectionJob: Job? = null
    private val connectionLock = Any()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingEvents = MutableSharedFlow<WebSocketIncomingEvent>(extraBufferCapacity = 64)
    val incomingEvents: SharedFlow<WebSocketIncomingEvent> = _incomingEvents.asSharedFlow()

    private var currentMessageId: String? = null
    private var currentSessionId: String? = null
    private var hermesSessionId: String? = null
    private val chatToServerSessionMap = java.util.concurrent.ConcurrentHashMap<String, String>()
    private var activeStreamSequence: Int = 0
    private var pendingPromptText: String? = null
    private var pendingPromptAttachments: List<AttachmentDto> = emptyList()
    private var lastSubmittedPromptText: String? = null
    private var lastSubmittedAttachments: List<AttachmentDto> = emptyList()
    private var rpcCounter: Long = 100
    private var activeModel: String? = null
    private var activeProvider: String? = null

    @Volatile
    private var isGenerationStopped = false
    private val stoppedMessageIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /**
     * G4 (03 §D9а): дедупликация повторно доставленных chunk-событий.
     * Ключ — messageId, значение — последний применённый sequence;
     * sequence <= последнего → дубль (после повторной доставки при reconnect) → пропуск.
     */
    private val lastAppliedSequence = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /** Проверка дубля для (messageId, sequence). true = уже доставлено. */
    private fun isDuplicateChunk(messageId: String, sequence: Int): Boolean {
        val last = lastAppliedSequence[messageId] ?: -1
        return sequence <= last
    }

    private fun markSequenceApplied(messageId: String, sequence: Int) {
        val last = lastAppliedSequence[messageId] ?: -1
        if (sequence > last) {
            lastAppliedSequence[messageId] = sequence
        }
    }

    fun updateEndpoint(newUrl: String) {
        Log.i(tag, "Updating endpoint URL: ${cleanUrlForLog(newUrl)} (was ${cleanUrlForLog(endpointUrl)})")
        endpointUrl = newUrl
        reconnect(force = true)
    }

    /** Лог без токена (03 §Безопасность: no secrets in logs). */
    private fun cleanUrlForLog(url: String): String {
        return url.substringBefore("?token=").substringBefore("&token=")
    }

    fun connect() {
        if (_connectionState.value == ConnectionState.READY) {
            Log.d(tag, "connect() ignored: already READY")
            return
        }
        if (connectionJob?.isActive == true && _connectionState.value == ConnectionState.CONNECTING) {
            Log.d(tag, "connect() ignored: already CONNECTING")
            return
        }
        reconnect(force = false)
    }

    /**
     * G7 (07 §G7): явный жизненный цикл клиента. Останавливает reconnect-цикл
     * (isIntentionallyClosed — существующая механика корректного закрытия),
     * закрывает WS и отменяет scope (collectors/launchers).
     */
    fun shutdown() {
        Log.i(tag, "shutdown(): closing connection and cancelling scope")
        synchronized(connectionLock) {
            isIntentionallyClosed = true
            connectionJob?.cancel()
            connectionJob = null
            try {
                webSocket?.close(1000, "App shutdown")
            } catch (e: Exception) {
                Log.w(tag, "shutdown: close failed: ${e.message}")
            }
            webSocket = null
        }
        scope.cancel()
    }

    fun evictConnectionPool() {
        try {
            okHttpClient.connectionPool.evictAll()
        } catch (_: Exception) {
        }
    }

    fun reconnect(force: Boolean = true) {
        synchronized(connectionLock) {
            Log.i(tag, "reconnect(force=$force) called, canceling active connection job if any")
            isIntentionallyClosed = false
            connectionJob?.cancel()
            connectionJob = null

            try {
                // cancel() немедленно прерывает зависший сокет/чтение (особенно при смене сети),
                // а close() корректно завершает handshake, если соединение ещё живо
                webSocket?.cancel()
                webSocket?.close(1000, if (force) "Forced reconnect" else "Reconnecting")
            } catch (e: Exception) {
                Log.w(tag, "Error closing previous websocket: ${e.message}")
            }
            webSocket = null
            evictConnectionPool()

            connectionJob = scope.launch {
                runConnectionLoop(forceImmediate = force)
            }
        }
    }

    private suspend fun runConnectionLoop(forceImmediate: Boolean) {
        var backoffMs = if (forceImmediate) 500L else 1000L
        // G3 (03 §D6): после отказа header-auth повторяем с query-токеном (loopback only),
        // успешный fallback запоминаем до смены endpoint/токена.
        var useQueryFallback = false

        while (!isIntentionallyClosed && currentCoroutineContext().isActive) {
            val disconnectSignal = CompletableDeferred<Unit>()

            try {
                val urlForAttempt = if (useQueryFallback) withQueryToken() else cleanEndpointUrl()
                Log.i(tag, "Attempting connection (auth=${if (useQueryFallback) "query-fallback" else "header"}) to $urlForAttempt")
                _connectionState.value = ConnectionState.CONNECTING

                val requestBuilder = Request.Builder()
                    .url(urlForAttempt)
                    .header("Sec-WebSocket-Protocol", "hermes.jsonrpc.v1")
                if (!useQueryFallback) {
                    // D6 п.1: токен в заголовке upgrade-запроса
                    requestBuilder.header("X-Hermes-Session-Token", sessionToken)
                }
                val request = requestBuilder.build()

                val listener = object : WebSocketListener() {
                    override fun onOpen(ws: WebSocket, response: Response) {
                        Log.i(tag, "WebSocket onOpen (auth=${if (useQueryFallback) "query-fallback" else "header"}) to ${cleanUrlForLog(endpointUrl)}")
                        webSocket = ws
                        backoffMs = 1000L
                        if (endpointUrl.contains("/api/ws")) {
                            val targetChat = currentSessionId ?: "sess_main"
                            val sid = chatToServerSessionMap[targetChat] ?: hermesSessionId
                            if (!sid.isNullOrBlank()) {
                                Log.i(tag, "Resuming hermes session $sid for chat $targetChat on open")
                                _connectionState.value = ConnectionState.AUTHENTICATING
                                sendSessionResume(sid)
                            } else {
                                Log.i(tag, "No existing hermes session for chat $targetChat, creating fresh session")
                                _connectionState.value = ConnectionState.AUTHENTICATING
                                sendSessionCreate()
                            }
                        } else {
                            _connectionState.value = ConnectionState.AUTHENTICATING
                            sendHello()
                        }
                    }

                    override fun onMessage(ws: WebSocket, text: String) {
                        handleIncomingFrame(text)
                    }

                    override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                        Log.i(tag, "WebSocket onClosing: code=$code, reason=$reason")
                        ws.close(1000, null)
                    }

                    override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                        Log.i(tag, "WebSocket onClosed: code=$code, reason=$reason, intentional=$isIntentionallyClosed")
                        webSocket = null
                        // G3 (03 §D6 п.2): закрытие до установления чата с 4401/403
                        // = отказ auth-механики. Loopback → повтор с query-токеном.
                        if ((code == 4401 || code == 403) && isLoopback() && !useQueryFallback) {
                            Log.w(tag, "Auth rejected (header), falling back to query token for loopback")
                            useQueryFallback = true
                        }
                        if (!isIntentionallyClosed) {
                            _connectionState.value = ConnectionState.RECONNECTING
                        } else {
                            _connectionState.value = ConnectionState.DISCONNECTED
                        }
                        disconnectSignal.complete(Unit)
                    }

                    override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                        Log.w(tag, "WebSocket onFailure: ${t.message} (code=${response?.code})")
                        webSocket = null
                        // G3 (03 §D6 п.2): HTTP-отказ upgrade 4401/403 при header-механике
                        // и loopback → query-fallback. Remote wss:// — без fallback (ошибка auth).
                        if ((response?.code == 403 || response?.code == 401) && isLoopback() && !useQueryFallback) {
                            Log.w(tag, "Auth rejected (header, HTTP ${response.code}), falling back to query token for loopback")
                            useQueryFallback = true
                        }
                        // 403 = отклонён токен: сервер, вероятно, поднялся без env-патча
                        // (будущая версия hermes-agent). Пробуем забрать реальный токен из HTML
                        // SPA dashboard и подключиться с ним.
                        if (response?.code == 403) {
                            fetchSessionTokenFromDashboardAndRetry()
                        } else if (!isIntentionallyClosed) {
                            _connectionState.value = ConnectionState.RECONNECTING
                        } else {
                            _connectionState.value = ConnectionState.FAILED
                        }
                        disconnectSignal.complete(Unit)
                    }
                }

                webSocket = okHttpClient.newWebSocket(request, listener)

                // Безопасное асинхронное ожидание разрыва соединения без холостых циклов
                disconnectSignal.await()

            } catch (e: CancellationException) {
                Log.i(tag, "runConnectionLoop cancelled")
                throw e
            } catch (e: Exception) {
                Log.e(tag, "Error during connection attempt", e)
                webSocket = null
                _connectionState.value = ConnectionState.RECONNECTING
            }

            if (!isIntentionallyClosed && currentCoroutineContext().isActive) {
                _connectionState.value = ConnectionState.RECONNECTING
                Log.d(tag, "Waiting backoff ${backoffMs}ms before reconnecting...")
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(10000L)
            }
        }
    }

    private fun sendHello() {
        val helloDto = ClientHelloDto(deviceId = deviceId)
        val frameText = json.encodeToString(ClientHelloDto.serializer(), helloDto)
        webSocket?.send(frameText)
    }

    /**
     * G3 (03 §D6): endpoint без query-токена — токен передаётся заголовком.
     */
    private fun cleanEndpointUrl(): String {
        return endpointUrl.substringBefore("?token=").substringBefore("&token=").trimEnd('&')
    }

    /** Query-fallback (loopback only): токен в URL как последний механизм совместимости. */
    private fun withQueryToken(): String {
        val base = cleanEndpointUrl()
        val separator = if (base.contains("?")) "&" else "?"
        return "$base${separator}token=$sessionToken"
    }

    /**
     * Fallback при 403: GET корня dashboard, извлечение сессионного токена
     * из инжекта window.__HERMES_SESSION_TOKEN__="..." и обновление токена
     * (header-auth). URL при этом не содержит токен. Если токен не найден —
     * обычный цикл переподключения продолжится.
     */
    private fun fetchSessionTokenFromDashboardAndRetry() {
        scope.launch {
            try {
                val httpUrl = endpointUrl
                    .replace("ws://", "http://")
                    .replace("wss://", "https://")
                    .substringBefore("/api/ws")
                    .substringBefore("?")
                    .trimEnd('/')
                val request = Request.Builder().url("$httpUrl/").build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful && response.body != null) {
                        val html = response.body!!.string()
                        val m = Regex("window\\.__HERMES_SESSION_TOKEN__=\"([^\"]+)\"").find(html)
                        val token = m?.groupValues?.get(1)
                        if (!token.isNullOrBlank() && token != sessionToken) {
                            Log.i(tag, "Fetched live session token from dashboard, switching header token")
                            updateSessionToken(token)
                            onSessionTokenRecovered?.invoke(token)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Session token fetch failed: ${e.message}")
            }
            if (!isIntentionallyClosed) {
                _connectionState.value = ConnectionState.RECONNECTING
            }
        }
    }

    /**
     * G4 (03 §D9г): восстановление серверной сессии при onOpen вместо
     * безусловного session.create. Вызывается composition root'ом до connect().
     */
    fun restoreHermesSession(sessionId: String?, chatId: String? = null) {
        if (!sessionId.isNullOrBlank()) {
            hermesSessionId = sessionId
            if (!chatId.isNullOrBlank()) {
                chatToServerSessionMap[chatId] = sessionId
            }
            Log.i(tag, "Restored hermes session for resume: ${sessionId.take(8)}… (chat: $chatId)")
        }
    }

    /** Текущий серверный идентификатор сессии (для персиста из репозитория). */
    fun currentHermesSessionId(chatId: String? = null): String? {
        return if (!chatId.isNullOrBlank()) {
            chatToServerSessionMap[chatId]
        } else {
            currentSessionId?.let { chatToServerSessionMap[it] } ?: hermesSessionId
        }
    }

    /**
     * G17 (03 §D10): переключение активного чата без сброса серверной сессии.
     * Привязываем чат к его hermesSessionId, резюмируя его на сервере.
     * Если у чата ещё нет серверной сессии — отправляем session.create.
     */
    fun switchSession(sessionId: String, hermesSid: String?) {
        currentSessionId = sessionId
        if (!hermesSid.isNullOrBlank()) {
            chatToServerSessionMap[sessionId] = hermesSid
            hermesSessionId = hermesSid
        } else {
            hermesSessionId = chatToServerSessionMap[sessionId]
        }
        val targetSid = hermesSessionId
        Log.i(tag, "Switched session to chat=$sessionId, hermesSessionId=${targetSid?.take(8) ?: "null"}")
        if (webSocket != null && endpointUrl.contains("/api/ws")) {
            if (targetSid != null) {
                _connectionState.value = ConnectionState.AUTHENTICATING
                sendSessionResume(targetSid)
            } else {
                _connectionState.value = ConnectionState.AUTHENTICATING
                sendSessionCreate()
            }
        }
    }

    private fun sendSessionResume(targetSessionId: String) {
        val resumeRpc = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", rpcCounter++)
            put("method", "session.resume")
            put("params", buildJsonObject {
                put("session_id", targetSessionId)
                put("cols", 80)
            })
        }
        Log.i(tag, "Sending session.resume for $targetSessionId: $resumeRpc")
        webSocket?.send(resumeRpc.toString())
    }

    private fun sendSessionCreate() {
        val createRpc = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", rpcCounter++)
            put("method", "session.create")
            put("params", buildJsonObject {
                val mod = activeModel
                val prov = activeProvider?.removePrefix("custom:")?.removePrefix("custom_")
                put("title", if (!mod.isNullOrBlank()) "Hermes - $mod" else "Hermes Mobile Session")
                if (!mod.isNullOrBlank()) {
                    put("model", mod)
                }
                if (!prov.isNullOrBlank()) {
                    put("provider", prov)
                }
            })
        }
        Log.i(tag, "Sending session.create: $createRpc")
        webSocket?.send(createRpc.toString())
    }

    private fun handleIncomingFrame(text: String) {
        scope.launch {
            try {
                Log.i(tag, "FRAME: $text")

                // 1. Проверяем JSON-RPC формат (Hermes Agent Gateway)
                if (text.contains("\"jsonrpc\"")) {
                    val root = json.parseToJsonElement(text).jsonObject

                    // Проверяем ошибку JSON-RPC (G17 / 03 §D10 п.4: fallback при 4001/4007)
                    if (root.containsKey("error")) {
                        val errObj = root["error"]?.jsonObject
                        val errCode = errObj?.get("code")?.jsonPrimitive?.intOrNull ?: 0
                        val errMsg = errObj?.get("message")?.jsonPrimitive?.contentOrNull ?: ""
                        Log.w(tag, "JSON-RPC error received: code=$errCode, message=$errMsg")

                        // Ошибка 4001 / 4007: Session not found / expired on daemon
                        if (errCode == 4001 || errCode == 4007 || errMsg.contains("session not found", ignoreCase = true) || errMsg.contains("session expired", ignoreCase = true)) {
                            Log.w(tag, "Hermes session expired/not found on daemon (code $errCode, msg=$errMsg). Performing automatic fallback & creation...")
                            val targetChat = currentSessionId ?: "sess_main"
                            hermesSessionId = null
                            chatToServerSessionMap.remove(targetChat)
                            onHermesSessionCreatedForChat?.invoke(targetChat, null)

                            val retryContent = lastSubmittedPromptText
                            val retryAtt = lastSubmittedAttachments
                            lastSubmittedPromptText = null
                            lastSubmittedAttachments = emptyList()

                            if (retryContent != null || retryAtt.isNotEmpty()) {
                                pendingPromptText = retryContent
                                pendingPromptAttachments = retryAtt
                            }
                            _connectionState.value = ConnectionState.AUTHENTICATING
                            sendSessionCreate()
                            return@launch
                        } else if (errCode == 4009 || errMsg.contains("session busy", ignoreCase = true)) {
                            // Ошибка 4009: Session busy (зависший воркер или незавершённая команда)
                            Log.w(tag, "Hermes session is busy (code 4009). Sending session.interrupt and creating fresh session...")
                            val targetChat = currentSessionId ?: "sess_main"
                            val staleSid = hermesSessionId ?: targetChat

                            // Принудительно прерываем зависшую задачу на демоне
                            val rpcInterrupt = buildJsonObject {
                                put("jsonrpc", "2.0")
                                put("id", rpcCounter++)
                                put("method", "session.interrupt")
                                put("params", buildJsonObject {
                                    put("session_id", staleSid)
                                })
                            }
                            webSocket?.send(rpcInterrupt.toString())

                            // Сбрасываем идентификатор зависшей сессии
                            hermesSessionId = null
                            onHermesSessionCreatedForChat?.invoke(targetChat, null)

                            val retryContent = lastSubmittedPromptText
                            val retryAtt = lastSubmittedAttachments
                            lastSubmittedPromptText = null
                            lastSubmittedAttachments = emptyList()

                            if (!retryContent.isNullOrBlank() || retryAtt.isNotEmpty()) {
                                pendingPromptText = retryContent
                                pendingPromptAttachments = retryAtt
                            }
                            // Запрашиваем создание чистой сессии
                            sendSessionCreate()
                            return@launch
                        } else {
                            _incomingEvents.emit(WebSocketIncomingEvent.Error(code = errCode.toString(), message = errMsg.ifBlank { "RPC error ($errCode)" }))
                            return@launch
                        }
                    }

                    // Проверяем результат вызова session.create или session.resume
                    if (root.containsKey("result")) {
                        val resultElement = root["result"]
                        val resultObj = resultElement as? JsonObject
                        val newSid = resultObj?.get("session_id")?.jsonPrimitive?.contentOrNull
                        val resumedSid = resultObj?.get("resumed")?.jsonPrimitive?.contentOrNull
                        val targetChat = currentSessionId ?: "sess_main"
                        val effectiveSid = newSid ?: hermesSessionId ?: chatToServerSessionMap[targetChat]
                        if (effectiveSid != null) {
                            hermesSessionId = effectiveSid
                            chatToServerSessionMap[targetChat] = effectiveSid
                            val persistentSid = resumedSid ?: effectiveSid
                            onHermesSessionCreatedForChat?.invoke(targetChat, persistentSid)
                            // G4 (03 §D9г): персист для resume при будущих reconnect
                            onHermesSessionEstablished?.invoke(persistentSid)
                        }
                        _connectionState.value = ConnectionState.READY
                        _incomingEvents.emit(WebSocketIncomingEvent.Ready(sessionId = effectiveSid ?: "", activeModel = activeModel ?: ""))
                        val pending = pendingPromptText
                        val pendingAtt = pendingPromptAttachments
                        if (pending != null || pendingAtt.isNotEmpty()) {
                            pendingPromptText = null
                            pendingPromptAttachments = emptyList()
                            val sendSid = effectiveSid ?: targetChat
                            submitPromptRpc(sendSid, pending ?: "", pendingAtt)
                        }
                        return@launch
                    }

                    // Проверяем события event или прямые методы
                    val method = root["method"]?.jsonPrimitive?.contentOrNull
                    val params = root["params"]?.jsonObject
                    val eventType = if (method == "event") {
                        params?.get("type")?.jsonPrimitive?.contentOrNull ?: ""
                    } else {
                        method ?: ""
                    }
                    val payload = if (method == "event") {
                        params?.get("payload")?.jsonObject ?: params
                    } else {
                        params
                    }

                    if (method == "event" || eventType.isNotBlank()) {
                        when (eventType) {
                            "gateway.ready" -> {
                                _connectionState.value = ConnectionState.READY
                                _incomingEvents.emit(WebSocketIncomingEvent.Ready(sessionId = hermesSessionId ?: "", activeModel = activeModel ?: ""))
                            }
                            "session.created", "session.resumed", "session.info" -> {
                                val sid = payload?.get("session_id")?.jsonPrimitive?.contentOrNull
                                    ?: params?.get("session_id")?.jsonPrimitive?.contentOrNull
                                val targetChat = currentSessionId ?: "sess_main"
                                val effectiveSid = sid ?: hermesSessionId ?: chatToServerSessionMap[targetChat]
                                if (effectiveSid != null) {
                                    hermesSessionId = effectiveSid
                                    chatToServerSessionMap[targetChat] = effectiveSid
                                    onHermesSessionCreatedForChat?.invoke(targetChat, effectiveSid)
                                    onHermesSessionEstablished?.invoke(effectiveSid)
                                }
                                _connectionState.value = ConnectionState.READY
                                _incomingEvents.emit(WebSocketIncomingEvent.Ready(sessionId = effectiveSid ?: "", activeModel = activeModel ?: ""))
                            }
                            "message.start" -> {
                                if (_connectionState.value != ConnectionState.READY) {
                                    _connectionState.value = ConnectionState.READY
                                }
                                activeStreamSequence = 0
                            }
                            "thinking.delta", "reasoning.delta" -> {
                                if (_connectionState.value != ConnectionState.READY) {
                                    _connectionState.value = ConnectionState.READY
                                }
                                val msgId = currentMessageId ?: "msg_${System.currentTimeMillis()}"
                                if (isGenerationStopped || stoppedMessageIds.contains(msgId)) {
                                    return@launch
                                }
                                val delta = payload?.get("text")?.jsonPrimitive?.contentOrNull
                                    ?: payload?.get("delta")?.jsonPrimitive?.contentOrNull
                                    ?: payload?.get("thinking")?.jsonPrimitive?.contentOrNull
                                    ?: payload?.get("reasoning")?.jsonPrimitive?.contentOrNull
                                if (!delta.isNullOrEmpty()) {
                                    val sessId = currentSessionId ?: "sess_main"
                                    val seq = activeStreamSequence++
                                    // G4 (03 §D9а): дедуп — повторная доставка после reconnect
                                    if (isDuplicateChunk(msgId, seq)) {
                                        return@launch
                                    }
                                    markSequenceApplied(msgId, seq)
                                    _incomingEvents.emit(
                                        WebSocketIncomingEvent.ThinkingChunk(
                                            messageId = msgId,
                                            sessionId = sessId,
                                            delta = delta,
                                            sequence = seq
                                        )
                                    )
                                }
                            }
                            "message.delta" -> {
                                if (_connectionState.value != ConnectionState.READY) {
                                    _connectionState.value = ConnectionState.READY
                                }
                                val msgId = currentMessageId ?: "msg_${System.currentTimeMillis()}"
                                if (isGenerationStopped || stoppedMessageIds.contains(msgId)) {
                                    return@launch
                                }
                                val delta = payload?.get("text")?.jsonPrimitive?.contentOrNull
                                    ?: payload?.get("delta")?.jsonPrimitive?.contentOrNull
                                if (!delta.isNullOrEmpty()) {
                                    val sessId = currentSessionId ?: "sess_main"
                                    val seq = activeStreamSequence++
                                    // G4 (03 §D9а): дедуп — повторная доставка после reconnect
                                    if (isDuplicateChunk(msgId, seq)) {
                                        return@launch
                                    }
                                    markSequenceApplied(msgId, seq)
                                    _incomingEvents.emit(
                                        WebSocketIncomingEvent.StreamChunk(
                                            messageId = msgId,
                                            sessionId = sessId,
                                            delta = delta,
                                            sequence = seq
                                        )
                                    )
                                }
                            }
                            "message.complete" -> {
                                if (_connectionState.value != ConnectionState.READY) {
                                    _connectionState.value = ConnectionState.READY
                                }
                                val msgId = currentMessageId ?: "msg_${System.currentTimeMillis()}"
                                if (isGenerationStopped || stoppedMessageIds.contains(msgId)) {
                                    return@launch
                                }
                                val finalText = payload?.get("text")?.jsonPrimitive?.contentOrNull
                                    ?: payload?.get("content")?.jsonPrimitive?.contentOrNull
                                    ?: ""
                                val finalThinking = payload?.get("thinking")?.jsonPrimitive?.contentOrNull
                                    ?: payload?.get("reasoning")?.jsonPrimitive?.contentOrNull
                                val sessId = currentSessionId ?: "sess_main"
                                _incomingEvents.emit(
                                    WebSocketIncomingEvent.Complete(
                                        messageId = msgId,
                                        sessionId = sessId,
                                        finalContent = finalText,
                                        finalThinking = finalThinking
                                    )
                                )
                            }
                            "approval.pending", "approval.received" -> {
                                val reqId = payload?.get("request_id")?.jsonPrimitive?.contentOrNull ?: UUID.randomUUID().toString()
                                val toolName = payload?.get("tool")?.jsonPrimitive?.contentOrNull
                                    ?: payload?.get("tool_name")?.jsonPrimitive?.contentOrNull
                                    ?: "action"
                                val preview = payload?.get("params_preview")?.jsonPrimitive?.contentOrNull
                                    ?: payload?.get("preview")?.jsonPrimitive?.contentOrNull
                                    ?: payload?.get("parameters")?.toString()
                                    ?: ""
                                // G9 (07 §G9): уровень риска из payload, не хардкод.
                                // Кандидаты полей — по образцу flat-ветки ToolApprovalRequestDto.
                                val riskStr = payload?.get("risk_level")?.jsonPrimitive?.contentOrNull
                                    ?: payload?.get("risk")?.jsonPrimitive?.contentOrNull
                                    ?: payload?.get("level")?.jsonPrimitive?.contentOrNull
                                val risk = when (riskStr?.lowercase()) {
                                    "critical" -> RiskLevel.CRITICAL
                                    "medium" -> RiskLevel.MEDIUM
                                    "low" -> RiskLevel.LOW
                                    else -> RiskLevel.LOW // отсутствие уровня ≠ критический риск
                                }
                                val domainReq = ToolApprovalRequest(
                                    requestId = reqId,
                                    sessionId = currentSessionId ?: "sess_main",
                                    toolName = toolName,
                                    riskLevel = risk,
                                    parametersPreview = preview,
                                    canAlwaysAllow = true,
                                    createdAtEpochMs = System.currentTimeMillis()
                                )
                                _incomingEvents.emit(WebSocketIncomingEvent.Approval(domainReq))
                            }
                        }
                        if (eventType.isNotBlank()) {
                            return@launch
                        }
                    }
                }

                // 2. Fallback для прямого формата событий (flat event format)
                val type = when {
                    text.contains("\"server_ready\"") || text.contains("\"gateway.ready\"") -> "server_ready"
                    text.contains("\"stream_chunk\"") -> "stream_chunk"
                    text.contains("\"message_complete\"") -> "message_complete"
                    text.contains("\"tool_approval_request\"") -> "tool_approval_request"
                    text.contains("\"clarify_prompt\"") -> "clarify_prompt"
                    else -> ""
                }

                when (type) {
                    "server_ready" -> {
                        val ready = json.decodeFromString<ServerReadyDto>(text)
                        _connectionState.value = ConnectionState.READY
                        _incomingEvents.emit(WebSocketIncomingEvent.Ready(ready.sessionId, ready.activeModel))
                    }
                    "stream_chunk" -> {
                        val chunk = json.decodeFromString<StreamChunkDto>(text)
                        if (!isGenerationStopped && !stoppedMessageIds.contains(chunk.messageId)) {
                            // G4 (03 §D9а): дедуп — повторная доставка после reconnect
                            if (isDuplicateChunk(chunk.messageId, chunk.sequence)) {
                                return@launch
                            }
                            markSequenceApplied(chunk.messageId, chunk.sequence)
                            _incomingEvents.emit(WebSocketIncomingEvent.StreamChunk(chunk.messageId, chunk.sessionId, chunk.delta, chunk.sequence))
                        }
                    }
                    "message_complete" -> {
                        val complete = json.decodeFromString<MessageCompleteDto>(text)
                        if (!isGenerationStopped && !stoppedMessageIds.contains(complete.messageId)) {
                            _incomingEvents.emit(WebSocketIncomingEvent.Complete(complete.messageId, complete.sessionId, complete.finalContent, complete.thinking))
                        }
                    }
                    "tool_approval_request" -> {
                        val req = json.decodeFromString<ToolApprovalRequestDto>(text)
                        val risk = when (req.riskLevel.lowercase()) {
                            "critical" -> RiskLevel.CRITICAL
                            "medium" -> RiskLevel.MEDIUM
                            else -> RiskLevel.LOW
                        }
                        val domainReq = ToolApprovalRequest(
                            requestId = req.requestId,
                            sessionId = req.sessionId,
                            toolName = req.toolName,
                            riskLevel = risk,
                            parametersPreview = req.parametersPreview,
                            canAlwaysAllow = req.canAlwaysAllow,
                            createdAtEpochMs = req.createdAtEpochMs
                        )
                        _incomingEvents.emit(WebSocketIncomingEvent.Approval(domainReq))
                    }
                    "clarify_prompt" -> {
                        val clr = json.decodeFromString<ClarifyPromptDto>(text)
                        val domainClr = ClarifyPrompt(
                            promptId = clr.promptId,
                            sessionId = clr.sessionId,
                            question = clr.question,
                            options = clr.options,
                            isMultiChoice = clr.isMultiChoice,
                            createdAtEpochMs = clr.createdAtEpochMs
                        )
                        _incomingEvents.emit(WebSocketIncomingEvent.Clarify(domainClr))
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error parsing incoming frame", e)
            }
        }
    }

    private fun submitPromptRpc(sessionId: String, content: String, attachments: List<AttachmentDto> = emptyList()) {
        lastSubmittedPromptText = content
        lastSubmittedAttachments = attachments
        scope.launch {
            val fileRefs = mutableListOf<String>()
            for (att in attachments) {
                // G15 (03 §D11 п.1): файл уже загружен через REST /api/attachments/upload
                fileRefs.add("@file:.hermes/attachments/${att.name}")
            }

            val appEnvDirective = "[Инструкция среды: Клиент Hermes Any AI. Веди диалог напрямую здесь. НЕ отправляй сообщения или файлы через Telegram send_message без явной просьбы пользователя. Если генерируешь или создаёшь файл, документ или изображение, верни его путь отдельной строкой формата MEDIA:/полный/путь/к/файлу или ссылкой формата [имя_файла](MEDIA:/полный/путь/к/файлу)]"

            val userText = content.trim()
            val effectiveText = when {
                fileRefs.isNotEmpty() && userText.isBlank() ->
                    "${fileRefs.joinToString(" ")}\n$appEnvDirective\nЧто находится на прикреплённом файле? Опиши подробно."
                fileRefs.isNotEmpty() ->
                    "${fileRefs.joinToString(" ")}\n$appEnvDirective\n$userText"
                else ->
                    "$appEnvDirective\n$userText"
            }

            val targetSid = hermesSessionId ?: chatToServerSessionMap[currentSessionId ?: ""] ?: sessionId
            val promptRpc = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", rpcCounter++)
                put("method", "prompt.submit")
                put("params", buildJsonObject {
                    put("session_id", targetSid)
                    put("text", effectiveText)
                })
            }
            webSocket?.send(promptRpc.toString())
        }
    }

    fun sendMessage(messageId: String, sessionId: String, content: String, attachments: List<AttachmentDto> = emptyList()) {
        isGenerationStopped = false
        stoppedMessageIds.clear()
        currentMessageId = messageId
        currentSessionId = sessionId
        activeStreamSequence = 0
        // G4 (03 §D9а): новый запрос — новый курсор дедупа для этого messageId
        lastAppliedSequence.remove(messageId)

        if (endpointUrl.contains("/api/ws")) {
            val sid = chatToServerSessionMap[sessionId] ?: hermesSessionId
            if (sid != null) {
                submitPromptRpc(sid, content, attachments)
            } else {
                pendingPromptText = content
                pendingPromptAttachments = attachments
                sendSessionCreate()
            }
        } else {
            val dto = SendMessageDto(
                messageId = messageId,
                sessionId = sessionId,
                content = content,
                attachments = attachments
            )
            val text = json.encodeToString(SendMessageDto.serializer(), dto)
            webSocket?.send(text)
        }
    }

    /**
     * D8 (03 §D8): единственный верифицированный метод остановки —
     * `session.interrupt` (см. contracts/hermes-agent-0.15.2/ws-rpc-verdicts.md:
     * prompt.cancel/session.abort/chat.abort → -32601 unknown method).
     * Flat DTO отправляется только для legacy-эндпоинтов (без /api/ws).
     */
    fun stopGeneration(sessionId: String) {
        isGenerationStopped = true
        currentMessageId?.let { stoppedMessageIds.add(it) }

        if (endpointUrl.contains("/api/ws")) {
            val sid = chatToServerSessionMap[sessionId] ?: hermesSessionId ?: sessionId
            val rpcInterrupt = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", rpcCounter++)
                put("method", "session.interrupt")
                put("params", buildJsonObject {
                    put("session_id", sid)
                })
            }
            webSocket?.send(rpcInterrupt.toString())
        } else {
            val dto = StopGenerationDto(sessionId = sessionId)
            val text = json.encodeToString(StopGenerationDto.serializer(), dto)
            webSocket?.send(text)
        }
    }

    fun respondToApproval(requestId: String, sessionId: String, decision: ApprovalDecision) {
        val decStr = when (decision) {
            ApprovalDecision.APPROVE -> "approve"
            ApprovalDecision.DENY -> "deny"
            ApprovalDecision.ALWAYS_ALLOW -> "always_allow"
        }

        if (endpointUrl.contains("/api/ws")) {
            val rpc = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", rpcCounter++)
                put("method", "approval.respond")
                put("params", buildJsonObject {
                    put("request_id", requestId)
                    put("decision", decStr)
                })
            }
            webSocket?.send(rpc.toString())
        } else {
            val dto = ToolApprovalResponseDto(
                requestId = requestId,
                sessionId = sessionId,
                decision = decStr
            )
            val text = json.encodeToString(ToolApprovalResponseDto.serializer(), dto)
            webSocket?.send(text)
        }
    }

    fun respondToClarify(promptId: String, sessionId: String, answer: String) {
        if (endpointUrl.contains("/api/ws")) {
            val sid = chatToServerSessionMap[sessionId] ?: hermesSessionId ?: sessionId
            submitPromptRpc(sid, answer)
        } else {
            val dto = ClarifyResponseDto(
                promptId = promptId,
                sessionId = sessionId,
                answer = answer
            )
            val text = json.encodeToString(ClarifyResponseDto.serializer(), dto)
            webSocket?.send(text)
        }
    }

    fun setActiveModel(model: String, provider: String = "") {
        activeModel = model
        if (provider.isNotEmpty()) {
            activeProvider = provider.removePrefix("custom:").removePrefix("custom_")
        }
        // G17 (03 §D10): hermesSessionId НЕ зануляем при установке/смене модели!
        // Это предотвращает потерю контекста диалога при старте приложения и смене модели.
    }

    fun setModel(model: String) {
        setActiveModel(model, activeProvider ?: "")
    }

    fun resetHermesSession() {
        val currentChat = currentSessionId ?: "sess_main"
        chatToServerSessionMap.remove(currentChat)
        hermesSessionId = null
        onHermesSessionCreatedForChat?.invoke(currentChat, null)
        if (_connectionState.value == ConnectionState.READY) {
            sendSessionCreate()
        }
    }

    fun saveApiKey(slug: String, apiKey: String) {
        val rpc = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", rpcCounter++)
            put("method", "model.save_key")
            put("params", buildJsonObject {
                put("slug", slug)
                put("api_key", apiKey)
            })
        }
        webSocket?.send(rpc.toString())
    }

    fun disconnect() {
        Log.i(tag, "disconnect() called by user")
        isIntentionallyClosed = true
        synchronized(connectionLock) {
            connectionJob?.cancel()
            connectionJob = null
            try {
                webSocket?.close(1000, "User disconnected")
            } catch (e: Exception) {
                Log.w(tag, "Error during disconnect close: ${e.message}")
            }
            webSocket = null
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }
}
