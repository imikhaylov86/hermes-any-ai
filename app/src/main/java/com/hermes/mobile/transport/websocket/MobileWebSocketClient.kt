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
    private var endpointUrl: String = "ws://127.0.0.1:8000/api/ws?token=hermes_mobile_local",
    private val deviceId: String = "android_device_local",
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    fun updateEndpoint(newUrl: String) {
        endpointUrl = newUrl
        webSocket?.close(1000, "Endpoint changed")
        webSocket = null
        _connectionState.value = ConnectionState.CONNECTING
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

    private var webSocket: WebSocket? = null
    private var isIntentionallyClosed = false

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingEvents = MutableSharedFlow<WebSocketIncomingEvent>(extraBufferCapacity = 64)
    val incomingEvents: SharedFlow<WebSocketIncomingEvent> = _incomingEvents.asSharedFlow()

    private var currentMessageId: String? = null
    private var currentSessionId: String? = null
    private var hermesSessionId: String? = null
    private var activeStreamSequence: Int = 0
    private var pendingPromptText: String? = null
    private var pendingPromptAttachments: List<AttachmentDto> = emptyList()
    private var rpcCounter: Long = 100
    private var activeModel: String? = null
    private var activeProvider: String? = null

    @Volatile
    private var isGenerationStopped = false
    private val stoppedMessageIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun connect() {
        if (_connectionState.value == ConnectionState.READY || _connectionState.value == ConnectionState.CONNECTING) {
            return
        }
        isIntentionallyClosed = false
        startConnectionLoop()
    }

    private fun startConnectionLoop() {
        scope.launch {
            var backoffMs = 1000L
            while (!isIntentionallyClosed) {
                try {
                    _connectionState.value = ConnectionState.CONNECTING
                    val request = Request.Builder()
                        .url(endpointUrl)
                        .header("Sec-WebSocket-Protocol", "hermes.jsonrpc.v1")
                        .build()

                    val listener = object : WebSocketListener() {
                        override fun onOpen(ws: WebSocket, response: Response) {
                            webSocket = ws
                            _connectionState.value = ConnectionState.AUTHENTICATING
                            sendHello()
                            if (endpointUrl.contains("/api/ws")) {
                                sendSessionCreate()
                            }
                        }

                        override fun onMessage(ws: WebSocket, text: String) {
                            handleIncomingFrame(text)
                        }

                        override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                            ws.close(1000, null)
                        }

                        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                            webSocket = null
                            if (!isIntentionallyClosed) {
                                _connectionState.value = ConnectionState.RECONNECTING
                            } else {
                                _connectionState.value = ConnectionState.DISCONNECTED
                            }
                        }

                        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                            webSocket = null
                            if (!isIntentionallyClosed) {
                                _connectionState.value = ConnectionState.RECONNECTING
                            } else {
                                _connectionState.value = ConnectionState.FAILED
                            }
                        }
                    }

                    okHttpClient.newWebSocket(request, listener)

                    // Ожидаем в цикле состояния
                    while (_connectionState.value != ConnectionState.RECONNECTING && !isIntentionallyClosed) {
                        delay(500)
                    }

                } catch (e: Exception) {
                    _connectionState.value = ConnectionState.RECONNECTING
                }

                if (!isIntentionallyClosed) {
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(10000L)
                }
            }
        }
    }

    private fun sendHello() {
        val helloDto = ClientHelloDto(deviceId = deviceId)
        val frameText = json.encodeToString(ClientHelloDto.serializer(), helloDto)
        webSocket?.send(frameText)
    }

    private fun sendSessionCreate() {
        val createRpc = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", rpcCounter++)
            put("method", "session.create")
            put("params", buildJsonObject {
                val mod = activeModel
                val prov = activeProvider?.removePrefix("custom_")
                put("title", if (!mod.isNullOrBlank()) "Hermes - $mod" else "Hermes Mobile Session")
                if (!mod.isNullOrBlank()) {
                    put("model", mod)
                }
                if (!prov.isNullOrBlank()) {
                    put("provider", prov)
                }
            })
        }
        webSocket?.send(createRpc.toString())
    }

    private fun handleIncomingFrame(text: String) {
        scope.launch {
            try {
                android.util.Log.d("MobileWebSocketClient", "FRAME: $text")
                // 1. Проверяем JSON-RPC формат (Hermes Agent Gateway)
                if (text.contains("\"jsonrpc\"")) {
                    val root = json.parseToJsonElement(text).jsonObject

                    // Проверяем результат вызова session.create
                    if (root.containsKey("result")) {
                        val resultObj = root["result"]?.jsonObject
                        val newSid = resultObj?.get("session_id")?.jsonPrimitive?.contentOrNull
                        if (newSid != null) {
                            hermesSessionId = newSid
                            val pending = pendingPromptText
                            val pendingAtt = pendingPromptAttachments
                            if (pending != null || pendingAtt.isNotEmpty()) {
                                pendingPromptText = null
                                pendingPromptAttachments = emptyList()
                                submitPromptRpc(newSid, pending ?: "", pendingAtt)
                            }
                        }
                        return@launch
                    }

                    // Проверяем события event
                    val method = root["method"]?.jsonPrimitive?.contentOrNull
                    if (method == "event") {
                        val params = root["params"]?.jsonObject
                        val type = params?.get("type")?.jsonPrimitive?.contentOrNull ?: ""
                        val payload = params?.get("payload")?.jsonObject

                        when (type) {
                            "gateway.ready" -> {
                                _connectionState.value = ConnectionState.READY
                                _incomingEvents.emit(WebSocketIncomingEvent.Ready(sessionId = "", activeModel = ""))
                            }
                            "message.start" -> {
                                activeStreamSequence = 0
                            }
                            "thinking.delta", "reasoning.delta" -> {
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
                                    _incomingEvents.emit(
                                        WebSocketIncomingEvent.ThinkingChunk(
                                            messageId = msgId,
                                            sessionId = sessId,
                                            delta = delta,
                                            sequence = activeStreamSequence++
                                        )
                                    )
                                }
                            }
                            "message.delta" -> {
                                val msgId = currentMessageId ?: "msg_${System.currentTimeMillis()}"
                                if (isGenerationStopped || stoppedMessageIds.contains(msgId)) {
                                    return@launch
                                }
                                val delta = payload?.get("text")?.jsonPrimitive?.contentOrNull
                                    ?: payload?.get("delta")?.jsonPrimitive?.contentOrNull
                                if (!delta.isNullOrEmpty()) {
                                    val sessId = currentSessionId ?: "sess_main"
                                    _incomingEvents.emit(
                                        WebSocketIncomingEvent.StreamChunk(
                                            messageId = msgId,
                                            sessionId = sessId,
                                            delta = delta,
                                            sequence = activeStreamSequence++
                                        )
                                    )
                                }
                            }
                            "message.complete" -> {
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
                                val domainReq = ToolApprovalRequest(
                                    requestId = reqId,
                                    sessionId = currentSessionId ?: "sess_main",
                                    toolName = toolName,
                                    riskLevel = RiskLevel.CRITICAL,
                                    parametersPreview = preview,
                                    canAlwaysAllow = true,
                                    createdAtEpochMs = System.currentTimeMillis()
                                )
                                _incomingEvents.emit(WebSocketIncomingEvent.Approval(domainReq))
                            }
                        }
                        return@launch
                    }
                }

                // 2. Fallback для прямого формата событий (flat event format)
                val type = when {
                    text.contains("\"server_ready\"") -> "server_ready"
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
                Log.e("MobileWebSocketClient", "Error parsing incoming frame", e)
            }
        }
    }

    private fun submitPromptRpc(sessionId: String, content: String, attachments: List<AttachmentDto> = emptyList()) {
        scope.launch {
            val fileRefs = mutableListOf<String>()
            for (att in attachments) {
                val dUrl = att.dataUrl
                if (!dUrl.isNullOrBlank()) {
                    val attachRpc = buildJsonObject {
                        put("jsonrpc", "2.0")
                        put("id", rpcCounter++)
                        put("method", "file.attach")
                        put("params", buildJsonObject {
                            put("session_id", sessionId)
                            put("data_url", dUrl)
                            put("name", att.name)
                        })
                    }
                    webSocket?.send(attachRpc.toString())
                    fileRefs.add("@file:.hermes/attachments/${att.name}")
                }
            }

            var effectiveText = content.trim()
            if (fileRefs.isNotEmpty()) {
                val prefix = fileRefs.joinToString(" ")
                effectiveText = if (effectiveText.isBlank()) {
                    "$prefix Что находится на прикреплённом файле? Опиши подробно."
                } else {
                    "$prefix $effectiveText"
                }
            }

            val promptRpc = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", rpcCounter++)
                put("method", "prompt.submit")
                put("params", buildJsonObject {
                    put("session_id", sessionId)
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

        if (endpointUrl.contains("/api/ws")) {
            val sid = hermesSessionId
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

    fun stopGeneration(sessionId: String) {
        isGenerationStopped = true
        currentMessageId?.let { stoppedMessageIds.add(it) }

        if (endpointUrl.contains("/api/ws")) {
            val sid = hermesSessionId ?: sessionId
            val rpcInterrupt = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", rpcCounter++)
                put("method", "session.interrupt")
                put("params", buildJsonObject {
                    put("session_id", sid)
                })
            }
            webSocket?.send(rpcInterrupt.toString())

            val rpcCancel = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", rpcCounter++)
                put("method", "prompt.cancel")
                put("params", buildJsonObject {
                    put("session_id", sid)
                })
            }
            webSocket?.send(rpcCancel.toString())

            val rpcAbort = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", rpcCounter++)
                put("method", "session.abort")
                put("params", buildJsonObject {
                    put("session_id", sid)
                })
            }
            webSocket?.send(rpcAbort.toString())

            val rpcChatAbort = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", rpcCounter++)
                put("method", "chat.abort")
                put("params", buildJsonObject {
                    put("session_id", sid)
                })
            }
            webSocket?.send(rpcChatAbort.toString())
        }

        val dto = StopGenerationDto(sessionId = sessionId)
        val text = json.encodeToString(StopGenerationDto.serializer(), dto)
        webSocket?.send(text)
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
            val sid = hermesSessionId ?: sessionId
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
            activeProvider = provider.removePrefix("custom_")
        }
        hermesSessionId = null
        if (_connectionState.value == ConnectionState.READY) {
            sendSessionCreate()
        }
    }

    fun setModel(model: String) {
        setActiveModel(model, activeProvider ?: "")
    }

    fun resetHermesSession() {
        hermesSessionId = null
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
        isIntentionallyClosed = true
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}
