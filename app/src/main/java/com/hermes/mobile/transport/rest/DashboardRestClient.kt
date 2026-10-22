package com.hermes.mobile.transport.rest

import com.hermes.mobile.domain.model.GatewayCheckResult
import com.hermes.mobile.domain.model.ModelCheckResult
import com.hermes.mobile.domain.model.ProviderApiMode
import com.hermes.mobile.transport.dto.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class DashboardRestClient(
    initialBaseUrl: String = "http://127.0.0.1:8000/api",
    private var sessionToken: String = "hermes_mobile_local"
) {
    private var baseUrl: String = normalizeBaseUrl(initialBaseUrl)

    fun updateBaseUrl(newUrl: String) {
        baseUrl = normalizeBaseUrl(newUrl)
    }

    fun updateSessionToken(token: String) {
        sessionToken = token
    }

    private fun normalizeBaseUrl(url: String): String {
        val trimmed = url.trim().trimEnd('/')
        return if (trimmed.endsWith("/api")) trimmed else "$trimmed/api"
    }

    private fun newRequestBuilder(path: String): Request.Builder {
        val url = if (path.startsWith("/")) "$baseUrl$path" else "$baseUrl/$path"
        return Request.Builder()
            .url(url)
            .header("X-Hermes-Session-Token", sessionToken)
            .header("Authorization", "Bearer $sessionToken")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(14, TimeUnit.SECONDS)
        .build()

    suspend fun revealEnvVar(keyName: String): String? = withContext(Dispatchers.IO) {
        try {
            val jsonPayload = """{"key": "$keyName"}"""
            val body = jsonPayload.toRequestBody("application/json".toMediaType())
            val request = newRequestBuilder("/env/reveal").post(body).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    val bodyString = response.body!!.string()
                    val elem = json.parseToJsonElement(bodyString)
                    if (elem is JsonObject) {
                        elem["value"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    } else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getEnvKeys(): Map<String, Boolean> = withContext(Dispatchers.IO) {
        try {
            val request = newRequestBuilder("/env").get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    val bodyString = response.body!!.string()
                    val elem = json.parseToJsonElement(bodyString)
                    if (elem is JsonObject) {
                        return@withContext elem.mapValues { (_, v) ->
                            val isSet = (v as? JsonObject)?.get("is_set")?.jsonPrimitive?.contentOrNull
                            isSet?.equals("true", ignoreCase = true) == true
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        emptyMap()
    }

    suspend fun getHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = newRequestBuilder("/health").get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    val bodyString = response.body!!.string()
                    val health = json.decodeFromString<HealthResponseDto>(bodyString)
                    health.ok || health.status == "ok"
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getModelInfo(): ModelInfoDto? = withContext(Dispatchers.IO) {
        try {
            val request = newRequestBuilder("/model/info").get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    val bodyString = response.body!!.string()
                    json.decodeFromString<ModelInfoDto>(bodyString)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getSessions(): List<SessionItemDto> = withContext(Dispatchers.IO) {
        try {
            val request = newRequestBuilder("/sessions").get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    val bodyString = response.body!!.string()
                    val res = json.decodeFromString<SessionListResponseDto>(bodyString)
                    res.sessions
                } else {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun createSession(title: String, model: String): SessionItemDto? = withContext(Dispatchers.IO) {
        try {
            val jsonPayload = """{"title": "$title", "model": "$model"}"""
            val body = jsonPayload.toRequestBody("application/json".toMediaType())
            val request = newRequestBuilder("/sessions").post(body).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    val bodyString = response.body!!.string()
                    json.decodeFromString<SessionItemDto>(bodyString)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun deleteSession(sessionId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = newRequestBuilder("/sessions/$sessionId").delete().build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getProviders(): List<ProviderItemDto> = withContext(Dispatchers.IO) {
        val currentInfo = getModelInfo()

        // 1. Попытка запроса через каталог моделей Hermes: /api/model/options
        try {
            val request = newRequestBuilder("/model/options").get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    val bodyString = response.body!!.string()
                    val options = json.decodeFromString<ModelOptionsResponseDto>(bodyString)

                    if (options.providers.isNotEmpty()) {
                        return@withContext options.providers.map { p ->
                            val isCurrentProvider = p.isCurrent || (currentInfo?.provider != null && (p.slug == currentInfo.provider || p.name == currentInfo.provider))
                            val activeModel = if (isCurrentProvider) {
                                currentInfo?.model ?: p.models.firstOrNull()
                            } else {
                                p.models.firstOrNull()
                            }
                            val hasRealKey = p.isUserDefined || p.source == "user-config" || (p.usage != null && p.usage.toString().contains("accounts"))
                            val status = if (hasRealKey) "connected" else "auth_required"
                            ProviderItemDto(
                                id = p.slug,
                                name = p.name,
                                status = status,
                                currentModel = activeModel,
                                availableModels = p.models,
                                hasKey = hasRealKey
                            )
                        }
                    } else if (options.models.isNotEmpty()) {
                        return@withContext listOf(
                            ProviderItemDto(
                                id = currentInfo?.provider ?: "hermes_runtime",
                                name = currentInfo?.provider ?: "Hermes Agent Runtime",
                                status = "connected",
                                currentModel = currentInfo?.model ?: options.current ?: options.models.firstOrNull(),
                                availableModels = options.models,
                                hasKey = true
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Резервная попытка запроса через /api/providers
        try {
            val request = newRequestBuilder("/providers").get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    val bodyString = response.body!!.string()
                    try {
                        return@withContext json.decodeFromString<ProvidersResponseDto>(bodyString).providers
                    } catch (_: Exception) {
                        return@withContext json.decodeFromString<List<ProviderItemDto>>(bodyString)
                    }
                }
            }
        } catch (_: Exception) {}

        emptyList()
    }

    suspend fun setModel(model: String, provider: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
        val targetProvider = if (provider.isNotEmpty()) {
            provider
        } else {
            getModelInfo()?.provider ?: ""
        }

        val requestDto = SetModelRequestDto(
            scope = "main",
            provider = targetProvider,
            model = model
        )
        val payload = json.encodeToString(SetModelRequestDto.serializer(), requestDto)
        val body = payload.toRequestBody("application/json".toMediaType())

        try {
            val request = newRequestBuilder("/model/set").post(body).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    val errBody = response.body?.string() ?: ""
                    val detail = try {
                        val obj = json.parseToJsonElement(errBody)
                        if (obj is kotlinx.serialization.json.JsonObject) {
                            obj["detail"]?.toString()?.trim('"') ?: errBody
                        } else errBody
                    } catch (_: Exception) {
                        errBody
                    }
                    Result.failure(Exception(if (detail.isNotBlank()) detail else "Ошибка сервера: код ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveCredential(providerId: String, apiKey: String): Boolean = withContext(Dispatchers.IO) {
        val payload = json.encodeToString(
            SaveKeyRequestDto.serializer(),
            SaveKeyRequestDto(providerId = providerId, apiKey = apiKey)
        )
        val body = payload.toRequestBody("application/json".toMediaType())

        // 1. Попытка POST /api/credentials/save
        try {
            val request = newRequestBuilder("/credentials/save").post(body).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) return@withContext true
            }
        } catch (_: Exception) {}

        // 2. Попытка POST /api/credentials/pool
        try {
            val request = newRequestBuilder("/credentials/pool").post(body).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) return@withContext true
            }
        } catch (_: Exception) {}

        false
    }

    suspend fun testProviderConnection(providerId: String): TestConnectionResponseDto = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val request = newRequestBuilder("/model/info").get().build()
            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - start
                if (response.isSuccessful) {
                    TestConnectionResponseDto(success = true, latencyMs = latency)
                } else {
                    TestConnectionResponseDto(success = false, errorMessage = "Ошибка ответа сервера: код ${response.code}")
                }
            }
        } catch (e: Exception) {
            TestConnectionResponseDto(success = false, errorMessage = "Ошибка связи: ${e.message}")
        }
    }

    suspend fun getCustomEndpoints(): List<CustomEndpointDto> = withContext(Dispatchers.IO) {
        try {
            val request = newRequestBuilder("/providers/custom-endpoints").get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    val bodyString = response.body!!.string()
                    try {
                        return@withContext json.decodeFromString<CustomEndpointsResponseDto>(bodyString).endpoints
                    } catch (_: Exception) {
                        return@withContext json.decodeFromString<List<CustomEndpointDto>>(bodyString)
                    }
                }
            }
        } catch (_: Exception) {}
        emptyList()
    }

    data class ValidationResult(
        val success: Boolean,
        val latencyMs: Long,
        val errorMessage: String? = null,
        val models: List<String> = emptyList()
    )

    suspend fun probeDirectModels(baseUrl: String, apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val cleanUrl = baseUrl.trim().trimEnd('/')
        val modelsUrl = if (cleanUrl.endsWith("/models")) cleanUrl else "$cleanUrl/models"
        val reqBuilder = Request.Builder().url(modelsUrl).get()
        if (apiKey.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer $apiKey")
            reqBuilder.header("x-api-key", apiKey)
        }

        try {
            client.newCall(reqBuilder.build()).execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    val bodyStr = response.body!!.string()
                    try {
                        val parsed = json.decodeFromString<OpenAIModelsResponseDto>(bodyStr)
                        val list = parsed.data.map { it.id }.filter { it.isNotBlank() }
                        if (list.isNotEmpty()) return@withContext list
                    } catch (_: Exception) {}

                    try {
                        val elem = json.parseToJsonElement(bodyStr)
                        if (elem is JsonObject) {
                            if (elem.containsKey("data") && elem["data"] is JsonArray) {
                                val list = elem["data"]!!.jsonArray.mapNotNull {
                                    (it as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull
                                }.filter { it.isNotBlank() }
                                if (list.isNotEmpty()) return@withContext list
                            }
                            if (elem.containsKey("models") && elem["models"] is JsonArray) {
                                val list = elem["models"]!!.jsonArray.mapNotNull {
                                    (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
                                        ?: (it as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull
                                        ?: (it as? JsonPrimitive)?.contentOrNull
                                }.filter { it.isNotBlank() }
                                if (list.isNotEmpty()) return@withContext list
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
        emptyList()
    }

    suspend fun validateCustomEndpoint(
        name: String,
        baseUrl: String,
        apiMode: String,
        apiKey: String
    ): ValidationResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()

        // 1. Попытка валидации через Hermes Agent: POST /api/providers/custom-endpoints/validate
        try {
            val validateDto = ValidateEndpointRequestDto(
                name = name.ifBlank { "test" },
                baseUrl = baseUrl.trim(),
                model = "default",
                apiKey = apiKey.ifBlank { null },
                apiMode = apiMode,
                discoverModels = true
            )
            val payload = json.encodeToString(ValidateEndpointRequestDto.serializer(), validateDto)
            val body = payload.toRequestBody("application/json".toMediaType())
            val request = newRequestBuilder("/providers/custom-endpoints/validate").post(body).build()

            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - start
                if (response.isSuccessful && response.body != null) {
                    val respString = response.body!!.string()
                    val result = json.decodeFromString<ValidateEndpointResponseDto>(respString)
                    val models = if (result.models.isNotEmpty()) {
                        result.models
                    } else {
                        probeDirectModels(baseUrl, apiKey)
                    }

                    if (result.ok || result.reachable || models.isNotEmpty()) {
                        return@withContext ValidationResult(
                            success = true,
                            latencyMs = latency,
                            errorMessage = if (result.ok || models.isNotEmpty()) null else result.message,
                            models = models
                        )
                    } else if (!result.message.isNullOrBlank()) {
                        return@withContext ValidationResult(
                            success = false,
                            latencyMs = latency,
                            errorMessage = result.message,
                            models = emptyList()
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Резервный прямой опрос эндпоинта модели: GET {baseUrl}/models
        try {
            val directModels = probeDirectModels(baseUrl, apiKey)
            val latency = System.currentTimeMillis() - start
            if (directModels.isNotEmpty()) {
                return@withContext ValidationResult(
                    success = true,
                    latencyMs = latency,
                    models = directModels
                )
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            return@withContext ValidationResult(
                success = false,
                latencyMs = latency,
                errorMessage = "Сбой соединения: ${e.message}"
            )
        }

        val latency = System.currentTimeMillis() - start
        ValidationResult(
            success = false,
            latencyMs = latency,
            errorMessage = "Не удалось подтвердить эндпоинт или получить список моделей. Проверьте Base URL и API-ключ."
        )
    }

    suspend fun addCustomEndpoint(
        name: String,
        apiMode: String,
        baseUrl: String,
        apiKey: String,
        models: List<String> = emptyList()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val trimmedName = name.trim().take(16)
        val discoveredModels = if (models.isNotEmpty()) models else probeDirectModels(baseUrl, apiKey)
        val defaultModel = discoveredModels.firstOrNull() ?: trimmedName

        val dto = CustomEndpointUpdateDto(
            id = trimmedName,
            name = trimmedName,
            baseUrl = baseUrl.trim(),
            model = defaultModel,
            apiKey = apiKey.ifBlank { null },
            apiMode = apiMode,
            discoverModels = true,
            models = if (discoveredModels.isNotEmpty()) discoveredModels else listOf(trimmedName)
        )
        val payload = json.encodeToString(CustomEndpointUpdateDto.serializer(), dto)
        val body = payload.toRequestBody("application/json".toMediaType())

        try {
            val request = newRequestBuilder("/providers/custom-endpoints").post(body).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    val err = response.body?.string() ?: "Код ошибки ${response.code}"
                    Result.failure(Exception(err))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCustomEndpoint(name: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
            val request = newRequestBuilder("/providers/custom-endpoints/$encodedName").delete().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Не удалось удалить провайдера: код ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun probeGatewayReachability(baseUrl: String, apiKey: String): GatewayCheckResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val cleanBase = baseUrl.trim().trimEnd('/')
        val probeUrl = if (cleanBase.endsWith("/models")) cleanBase else "$cleanBase/models"

        try {
            val reqBuilder = Request.Builder().url(probeUrl).get()
            if (apiKey.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer $apiKey")
                reqBuilder.header("x-api-key", apiKey)
            }
            probeClient.newCall(reqBuilder.build()).execute().use { response ->
                val latency = System.currentTimeMillis() - start
                if (response.isSuccessful || response.code == 401 || response.code == 403) {
                    GatewayCheckResult(
                        name = "Шлюз",
                        url = cleanBase,
                        isReachable = true,
                        latencyMs = latency,
                        errorMessage = if (response.code == 401) "Требуется авторизация" else null
                    )
                } else {
                    GatewayCheckResult(
                        name = "Шлюз",
                        url = cleanBase,
                        isReachable = false,
                        latencyMs = latency,
                        errorMessage = "Ответ шлюза: код ${response.code}"
                    )
                }
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            GatewayCheckResult(
                name = "Шлюз",
                url = cleanBase,
                isReachable = false,
                latencyMs = latency,
                errorMessage = e.localizedMessage ?: "Сбой соединения со шлюзом"
            )
        }
    }

    suspend fun probeModelCompletion(
        baseUrl: String,
        apiMode: String,
        apiKey: String,
        model: String
    ): ModelCheckResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val cleanBase = baseUrl.trim().trimEnd('/')

        val (url, jsonPayload) = when (apiMode) {
            ProviderApiMode.ANTHROPIC_MESSAGES.id -> {
                val u = if (cleanBase.endsWith("/messages")) cleanBase else "$cleanBase/messages"
                val p = """{"model": "$model", "max_tokens": 1, "messages": [{"role": "user", "content": "1"}]}"""
                u to p
            }
            ProviderApiMode.OLLAMA_NATIVE.id -> {
                val u = if (cleanBase.endsWith("/chat")) cleanBase else "$cleanBase/chat"
                val p = """{"model": "$model", "messages": [{"role": "user", "content": "1"}], "stream": false, "options": {"num_predict": 1}}"""
                u to p
            }
            ProviderApiMode.GOOGLE_GEMINI.id -> {
                val u = "$cleanBase/models/$model:generateContent?key=$apiKey"
                val p = """{"contents": [{"parts":[{"text": "1"}]}], "generationConfig": {"maxOutputTokens": 1}}"""
                u to p
            }
            else -> { // OPENAI_COMPATIBLE
                val u = when {
                    cleanBase.endsWith("/chat/completions") -> cleanBase
                    cleanBase.endsWith("/v1") -> "$cleanBase/chat/completions"
                    else -> "$cleanBase/chat/completions"
                }
                val p = """{"model": "$model", "messages": [{"role": "user", "content": "1"}], "max_tokens": 1, "stream": false}"""
                u to p
            }
        }

        try {
            val body = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
            val reqBuilder = Request.Builder().url(url).post(body)

            if (apiKey.isNotBlank() && apiMode != ProviderApiMode.GOOGLE_GEMINI.id) {
                reqBuilder.header("Authorization", "Bearer $apiKey")
                reqBuilder.header("x-api-key", apiKey)
            }
            if (apiMode == ProviderApiMode.ANTHROPIC_MESSAGES.id) {
                reqBuilder.header("anthropic-version", "2023-06-01")
            }

            probeClient.newCall(reqBuilder.build()).execute().use { response ->
                val latency = System.currentTimeMillis() - start
                if (response.isSuccessful) {
                    var tokensUsed = 1
                    try {
                        val bodyStr = response.body?.string() ?: ""
                        val elem = json.parseToJsonElement(bodyStr)
                        if (elem is JsonObject && elem.containsKey("usage")) {
                            val usage = elem["usage"] as? JsonObject
                            val total = usage?.get("total_tokens")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                                ?: usage?.get("completion_tokens")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                            if (total != null && total > 0) tokensUsed = total
                        }
                    } catch (_: Exception) {}

                    ModelCheckResult(
                        modelName = model,
                        isSuccess = true,
                        latencyMs = latency,
                        tokensUsed = tokensUsed
                    )
                } else {
                    val errBody = try { response.body?.string()?.take(150) } catch (_: Exception) { null }
                    val errorMsg = when (response.code) {
                        401 -> "Неверный API-ключ (401)"
                        403 -> "Доступ запрещен (403)"
                        404 -> "Модель '$model' не найдена (404)"
                        429 -> "Лимит запросов исчерпан (429)"
                        else -> "Ошибка сервера: код ${response.code}${if (!errBody.isNullOrBlank()) " ($errBody)" else ""}"
                    }
                    ModelCheckResult(
                        modelName = model,
                        isSuccess = false,
                        latencyMs = latency,
                        tokensUsed = 0,
                        errorMessage = errorMsg
                    )
                }
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            ModelCheckResult(
                modelName = model,
                isSuccess = false,
                latencyMs = latency,
                tokensUsed = 0,
                errorMessage = e.localizedMessage ?: "Сбой соединения с моделью"
            )
        }
    }
}
