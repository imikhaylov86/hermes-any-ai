package com.hermes.mobile.domain.repository

import com.hermes.mobile.domain.model.*
import com.hermes.mobile.persistence.datastore.AppSettingsManager
import com.hermes.mobile.security.KeystoreManager
import com.hermes.mobile.transport.rest.DashboardRestClient
import com.hermes.mobile.transport.websocket.MobileWebSocketClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProviderRepositoryImpl(
    private val restClient: DashboardRestClient,
    private val webSocketClient: MobileWebSocketClient,
    private val settingsManager: AppSettingsManager,
    private val keystoreManager: KeystoreManager
) : ProviderRepository {

    private val _providers = MutableStateFlow<List<ProviderConfig>>(emptyList())
    override val providers: StateFlow<List<ProviderConfig>> = _providers.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()

    override suspend fun refreshProviders(): Result<List<ProviderConfig>> {
        _isLoading.value = true
        _error.value = null
        try {
            val dtos = restClient.getProviders()
            val customEndpoints = restClient.getCustomEndpoints()
            val currentInfo = restClient.getModelInfo()

            val standardList = dtos.map { dto ->
                val status = when (dto.status.lowercase()) {
                    "connected" -> ProviderStatus.CONNECTED
                    "auth_required" -> ProviderStatus.AUTH_REQUIRED
                    "error" -> ProviderStatus.ERROR
                    else -> ProviderStatus.DISCONNECTED
                }
                val hasKey = dto.hasKey || keystoreManager.hasKey(dto.id) || keystoreManager.hasKey(dto.name)
                ProviderConfig(
                    id = dto.id,
                    name = dto.name,
                    status = status,
                    currentModel = dto.currentModel ?: dto.availableModels.firstOrNull(),
                    availableModels = dto.availableModels,
                    hasApiKey = hasKey,
                    baseUrl = dto.baseUrl,
                    apiMode = ProviderApiMode.fromId(dto.apiMode),
                    isCustom = dto.isCustom
                )
            }.toMutableList()

            // Дополняем и обогащаем пользовательскими эндпоинтами из конфига агента
            customEndpoints.forEach { ce ->
                val customId = "custom_${ce.name.lowercase().replace(Regex("[^a-z0-9_]"), "_")}"
                val existingIndex = standardList.indexOfFirst {
                    it.id == customId || it.id == ce.name || it.name.equals(ce.name, ignoreCase = true)
                }
                val envKeyName = ce.apiKeyPreview?.let {
                    val m = Regex("""\$\{([A-Za-z0-9_]+)\}""").find(it)
                    m?.groupValues?.get(1)
                }
                if (!keystoreManager.hasKey(customId) && !keystoreManager.hasKey(ce.name) && envKeyName != null) {
                    val revealed = restClient.revealEnvVar(envKeyName)
                    if (!revealed.isNullOrBlank()) {
                        keystoreManager.encryptAndSave(customId, revealed)
                        keystoreManager.encryptAndSave(ce.name, revealed)
                    }
                }
                val hasKey = ce.hasApiKey || !ce.apiKey.isNullOrBlank() || keystoreManager.hasKey(customId) || keystoreManager.hasKey(ce.name)
                val effectiveModels = if (ce.models.isNotEmpty()) ce.models else listOf(ce.name)

                if (existingIndex != -1) {
                    val existing = standardList[existingIndex]
                    standardList[existingIndex] = existing.copy(
                        baseUrl = ce.baseUrl,
                        apiMode = ProviderApiMode.fromId(ce.apiMode),
                        isCustom = true,
                        hasApiKey = existing.hasApiKey || hasKey,
                        availableModels = if (existing.availableModels.isNotEmpty()) existing.availableModels else effectiveModels
                    )
                } else {
                    standardList.add(
                        ProviderConfig(
                            id = customId,
                            name = ce.name,
                            status = if (hasKey) ProviderStatus.CONNECTED else ProviderStatus.AUTH_REQUIRED,
                            currentModel = ce.models.firstOrNull() ?: ce.name,
                            availableModels = effectiveModels,
                            hasApiKey = hasKey,
                            baseUrl = ce.baseUrl,
                            apiMode = ProviderApiMode.fromId(ce.apiMode),
                            isCustom = true
                        )
                    )
                }
            }

            if (standardList.isNotEmpty()) {
                _providers.value = standardList
                if (currentInfo?.model != null && currentInfo.provider != null) {
                    val model = currentInfo.model!!
                    val provider = currentInfo.provider!!
                    val matchingProv = standardList.firstOrNull { it.id == provider || it.name == provider }
                    val provName = matchingProv?.name ?: provider
                    settingsManager.setActiveModelAndProvider(model, provider, provName)
                    webSocketClient.setActiveModel(model, provider)
                }
                _isLoading.value = false
                return Result.success(standardList)
            } else {
                if (settingsManager.isDemoMode.value) {
                    val demoList = getDemoProviders()
                    _providers.value = demoList
                    _isLoading.value = false
                    return Result.success(demoList)
                }
                _error.value = "Hermes runtime не вернул список провайдеров. Убедитесь, что сервер запущен."
                _isLoading.value = false
                return Result.failure(Exception("Пустой ответ от сервера"))
            }
        } catch (e: Exception) {
            if (settingsManager.isDemoMode.value) {
                val demoList = getDemoProviders()
                _providers.value = demoList
                _isLoading.value = false
                return Result.success(demoList)
            }
            val err = "Ошибка загрузки провайдеров: ${e.message}"
            _error.value = err
            _isLoading.value = false
            return Result.failure(e)
        }
    }

    override suspend fun setActiveModel(model: String, providerId: String?): Result<Unit> {
        val prov = _providers.value.firstOrNull { it.id == providerId || it.name.equals(providerId, ignoreCase = true) }
            ?: _providers.value.firstOrNull { it.id.equals(providerId?.removePrefix("custom_"), ignoreCase = true) }
            ?: _providers.value.firstOrNull { it.availableModels.contains(model) }
        val targetProvider = prov?.let { if (it.isCustom) it.name else it.id.removePrefix("custom_") }
            ?: providerId?.removePrefix("custom_") ?: ""
        val targetProviderName = prov?.name ?: targetProvider

        if (prov != null && (!prov.hasApiKey || prov.status == ProviderStatus.AUTH_REQUIRED)) {
            return Result.failure(Exception("Провайдер ${prov.name} не подключен: укажите API-ключ перед выбором модели"))
        }

        // Синхронизация активной модели с runtime Hermes через REST
        val restResult = restClient.setModel(model, targetProvider)
        if (restResult.isFailure) {
            return Result.failure(restResult.exceptionOrNull() ?: Exception("Не удалось переключить модель в Hermes"))
        }

        // Синхронизация с WebSocket сессией (модель + провайдер)
        webSocketClient.setActiveModel(model, targetProvider)
        settingsManager.setActiveModelAndProvider(model, targetProvider, targetProviderName)

        // Обновляем состояние в локальном потоке
        val currentList = _providers.value
        _providers.value = currentList.map { p ->
            if (p.id == targetProvider || p.name.equals(targetProvider, ignoreCase = true) || p.id == prov?.id) {
                p.copy(currentModel = model)
            } else {
                p
            }
        }
        return Result.success(Unit)
    }

    /**
     * Env-имена переменных для известных провайдеров (маппинг slug → имя ключа
     * в daemon env, как их ожидает Hermes Agent в ~/.hermes/.env).
     */
    private fun envKeyCandidatesFor(providerId: String, providerName: String): List<String> {
        val id = providerId.lowercase()
        val name = providerName.lowercase()
        val candidates = mutableListOf<String>()
        when {
            id.contains("zai") || name.contains("z.ai") || name.contains("zai") || name.contains("glm") ->
                candidates.addAll(listOf("ZAI_API_KEY", "GLM_API_KEY", "ZAI_CODING_API_KEY"))
            id.contains("openai") || name.contains("openai") ->
                candidates.add("OPENAI_API_KEY")
            id.contains("anthropic") || name.contains("anthropic") || name.contains("claude") ->
                candidates.add("ANTHROPIC_API_KEY")
            id.contains("openrouter") || name.contains("openrouter") ->
                candidates.add("OPENROUTER_API_KEY")
            id.contains("groq") || name.contains("groq") ->
                candidates.add("GROQ_API_KEY")
            id.contains("qwen") || name.contains("qwen") || name.contains("alibaba") || name.contains("dashscope") ->
                candidates.addAll(listOf("DASHSCOPE_API_KEY", "QWEN_API_KEY"))
            id.contains("deepseek") || name.contains("deepseek") ->
                candidates.add("DEEPSEEK_API_KEY")
            id.contains("google") || name.contains("gemini") ->
                candidates.add("GEMINI_API_KEY")
        }
        val cleanName = providerName.uppercase().replace(Regex("[^A-Z0-9_]"), "_")
        val cleanId = providerId.uppercase().replace(Regex("[^A-Z0-9_]"), "_")
        candidates.add("HERMES_CUSTOM_${cleanName}_API_KEY")
        candidates.add("${cleanName}_API_KEY")
        candidates.add("HERMES_CUSTOM_${cleanId}_API_KEY")
        candidates.add("${cleanId}_API_KEY")
        return candidates.distinct()
    }

    /**
     * Каноническое env-имя ключа для кастомного провайдера демона:
     * провайдер в config.yaml ссылается на него через key_env.
     */
    private fun keyEnvNameFor(providerName: String): String {
        val clean = providerName.uppercase().replace(Regex("[^A-Z0-9_]"), "_")
        return "HERMES_CUSTOM_${clean}_API_KEY"
    }

    override suspend fun saveApiKey(providerId: String, apiKey: String): Result<Unit> {
        val prov = _providers.value.firstOrNull { it.id == providerId || it.name == providerId }
        val provName = prov?.name ?: providerId.removePrefix("custom_")

        // 1. Сохранение в аппаратный Android Keystore
        keystoreManager.encryptAndSave(providerId, apiKey)
        keystoreManager.encryptAndSave(provName, apiKey)
        keystoreManager.encryptAndSave("custom_${provName.lowercase()}", apiKey)

        // 2. Регистрация провайдера в config.yaml демона (PUT /api/config) с key_env,
        //    если провайдер кастомный и у него есть baseUrl
        var configRegistered = false
        val baseUrl = prov?.baseUrl?.takeIf { it.isNotBlank() }
        if (baseUrl != null) {
            val model = prov.currentModel ?: prov.availableModels.firstOrNull() ?: trimmedNameFallback(provName)
            configRegistered = restClient.registerProviderInConfig(
                name = provName,
                baseUrl = baseUrl,
                model = model,
                keyEnv = keyEnvNameFor(provName)
            )
        }

        // 3. Запись ключа в env демона через PUT /api/env (persist в ~/.hermes/.env)
        var envApplied = false
        val appliedEnvNames = mutableListOf<String>()
        if (configRegistered) {
            if (restClient.putEnvVar(keyEnvNameFor(provName), apiKey)) {
                envApplied = true
                appliedEnvNames.add(keyEnvNameFor(provName))
            }
        }
        // Совместимость со стандартными провайдерами hermes (zai, openai, ...)
        for (envName in envKeyCandidatesFor(providerId, provName)) {
            if (restClient.putEnvVar(envName, apiKey)) {
                envApplied = true
                appliedEnvNames.add(envName)
            }
        }
        android.util.Log.i("ProviderRepository", "saveApiKey: configRegistered=$configRegistered env applied=$envApplied names=$appliedEnvNames")

        // 4. Отправка ключа в Hermes WebSocket (устаревший канал, best-effort)
        webSocketClient.saveApiKey(provName, apiKey)

        // 5. Обновляем флаг hasApiKey для провайдера
        _providers.value = _providers.value.map { p ->
            if (p.id == providerId || p.name == provName || p.id == provName) {
                p.copy(hasApiKey = true, status = ProviderStatus.CONNECTED)
            } else {
                p
            }
        }

        return if (envApplied || configRegistered) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Не удалось сохранить ключ: демон недоступен или отклонил PUT /api/config и PUT /api/env"))
        }
    }

    private fun trimmedNameFallback(provName: String): String = provName.take(16)

    override suspend fun addCustomProvider(
        name: String,
        apiMode: ProviderApiMode,
        baseUrl: String,
        apiKey: String,
        models: List<String>
    ): Result<ProviderConfig> {
        val trimmedName = name.trim().take(16)
        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("Название провайдера не может быть пустым"))
        }
        val customId = trimmedName
        val discoveredModels = if (models.isNotEmpty()) models else restClient.probeDirectModels(baseUrl, apiKey)
        val effectiveModels = if (discoveredModels.isNotEmpty()) discoveredModels else listOf(trimmedName)

        // 1. Сохраняем API-ключ в аппаратный Android Keystore (если указан)
        if (apiKey.isNotBlank()) {
            keystoreManager.encryptAndSave(customId, apiKey)
            keystoreManager.encryptAndSave(trimmedName, apiKey)
            keystoreManager.encryptAndSave("custom_${trimmedName.lowercase()}", apiKey)
        }

        // 2. Регистрируем провайдера в config.yaml демона: PUT /api/config
        //    (providers.<name> = {base_url, key_env, model}) + ключ через PUT /api/env.
        //    Старый POST /api/providers/custom-endpoints в hermes-agent НЕ существует
        //    (SPA-fallback возвращал HTML 200 и маскировал провал).
        val keyEnv = keyEnvNameFor(trimmedName)
        val defaultModel = effectiveModels.firstOrNull() ?: trimmedName
        val configOk = restClient.registerProviderInConfig(
            name = trimmedName,
            baseUrl = baseUrl.trim(),
            model = defaultModel,
            keyEnv = keyEnv
        )
        var keyOk = false
        if (configOk && apiKey.isNotBlank()) {
            keyOk = restClient.putEnvVar(keyEnv, apiKey)
        }

        val newProvider = ProviderConfig(
            id = customId,
            name = trimmedName,
            status = if (apiKey.isNotBlank()) ProviderStatus.CONNECTED else ProviderStatus.AUTH_REQUIRED,
            currentModel = effectiveModels.firstOrNull(),
            availableModels = effectiveModels,
            hasApiKey = apiKey.isNotBlank(),
            baseUrl = baseUrl.trim(),
            apiMode = apiMode,
            isCustom = true
        )

        // 3. Обновляем список локально
        val updatedList = _providers.value.filterNot {
            it.id == customId || it.name.equals(trimmedName, ignoreCase = true) || it.id == "custom_${trimmedName.lowercase()}"
        } + newProvider
        _providers.value = updatedList

        if (!configOk) {
            // Провайдер сохранён локально, но демон его не принял — сообщаем пользователю
            return Result.failure(Exception(
                "Провайдер сохранён в приложении, но не добавлен в агент: демон недоступен или отклонил конфиг. " +
                        "При подключении агента провайдер будет синхронизирован автоматически."
            ))
        }
        return Result.success(newProvider)
    }

    override suspend fun deleteProvider(providerId: String): Result<Unit> {
        val prov = _providers.value.firstOrNull { it.id == providerId || it.name.equals(providerId, ignoreCase = true) }
            ?: return Result.failure(Exception("Провайдер не найден"))

        // 1. Удаляем из конфига агента Hermes
        val restResult = restClient.deleteCustomEndpoint(prov.name)

        // 2. Удаляем ключи из Android Keystore
        keystoreManager.removeKey(prov.id)
        keystoreManager.removeKey(prov.name)
        keystoreManager.removeKey("custom_${prov.name.lowercase()}")

        // 3. Удаляем из локального списка
        val updated = _providers.value.filterNot { 
            it.id == providerId || it.name.equals(prov.name, ignoreCase = true) || it.id == "custom_${prov.name.lowercase()}" 
        }
        _providers.value = updated

        // 4. Если удалённый провайдер был активным — переключаем на первый доступный
        val currentActive = settingsManager.activeProvider.value.removePrefix("custom_")
        if (currentActive == providerId || currentActive == prov.name || settingsManager.activeProviderName.value == prov.name) {
            val fallback = updated.firstOrNull { it.isConnected } ?: updated.firstOrNull()
            if (fallback != null) {
                val fallbackModel = fallback.currentModel ?: fallback.availableModels.firstOrNull() ?: "Hermes 3"
                setActiveModel(fallbackModel, fallback.id)
            }
        }

        return Result.success(Unit)
    }

    override suspend fun testCustomEndpoint(
        baseUrl: String,
        apiMode: ProviderApiMode,
        apiKey: String,
        name: String
    ): Result<EndpointTestResult> {
        val response = restClient.validateCustomEndpoint(
            name = name,
            baseUrl = baseUrl,
            apiMode = apiMode.id,
            apiKey = apiKey
        )
        return if (response.success) {
            Result.success(EndpointTestResult(latencyMs = response.latencyMs, models = response.models))
        } else {
            Result.failure(Exception(response.errorMessage ?: "Сбой соединения с эндпоинтом"))
        }
    }

    override suspend fun testConnection(providerId: String): Result<Long> {
        val prov = _providers.value.firstOrNull { it.id == providerId }
            ?: return Result.failure(Exception("Провайдер не найден"))

        if (prov.isCustom && !prov.baseUrl.isNullOrBlank()) {
            val key = keystoreManager.getDecrypted(prov.id) ?: keystoreManager.getDecrypted(prov.name) ?: ""
            val testRes = testCustomEndpoint(prov.baseUrl, prov.apiMode, key, prov.name)
            return if (testRes.isSuccess) {
                val data = testRes.getOrNull()!!
                if (data.models.isNotEmpty() && (prov.availableModels.size <= 1 || prov.availableModels != data.models)) {
                    val updated = prov.copy(
                        availableModels = data.models,
                        currentModel = if (prov.currentModel in data.models) prov.currentModel else data.models.firstOrNull()
                    )
                    _providers.value = _providers.value.map { if (it.id == prov.id) updated else it }
                }
                Result.success(data.latencyMs)
            } else {
                Result.failure(testRes.exceptionOrNull() ?: Exception("Сбой соединения с эндпоинтом"))
            }
        }

        if (!prov.hasApiKey || prov.status == ProviderStatus.AUTH_REQUIRED) {
            return Result.failure(Exception("Провайдер не подключен: отсутствует API-ключ"))
        }
        val response = restClient.testProviderConnection(providerId)
        return if (response.success) {
            Result.success(response.latencyMs)
        } else {
            Result.failure(Exception(response.errorMessage ?: "Провайдер недоступен"))
        }
    }

    private fun getDemoProviders(): List<ProviderConfig> {
        return listOf(
            ProviderConfig(
                id = "prov_hermes_local",
                name = "Локальный Hermes",
                status = ProviderStatus.CONNECTED,
                currentModel = "Hermes 3 (Llama-3.1 8B)",
                availableModels = listOf("Hermes 3 (Llama-3.1 8B)", "Qwen 2.5 Coder 7B"),
                hasApiKey = true,
                isCustom = false
            ),
            ProviderConfig(
                id = "prov_anthropic",
                name = "Anthropic Claude",
                status = ProviderStatus.AUTH_REQUIRED,
                currentModel = "Claude 3.5 Sonnet",
                availableModels = listOf("Claude 3.5 Sonnet", "Claude 3.5 Haiku", "Claude 3 Opus"),
                hasApiKey = keystoreManager.hasKey("prov_anthropic"),
                isCustom = false
            ),
            ProviderConfig(
                id = "prov_openai",
                name = "OpenAI",
                status = ProviderStatus.CONNECTED,
                currentModel = "gpt-4o",
                availableModels = listOf("gpt-4o", "gpt-4o-mini", "o1-preview"),
                hasApiKey = true,
                isCustom = false
            )
        )
    }

    /**
     * Авто-синхронизация ключей и модели с демоном Hermes после подключения.
     *
     * 1. Регистрирует кастомных провайдеров из локального списка в config.yaml
     *    демона (PUT /api/config, providers.<name> с key_env), если их там нет.
     * 2. Переносит ключи из Android Keystore в env демона (PUT /api/env),
     *    пропуская уже установленные.
     * 3. Если model/info пуст — выбирает модель из настроек приложения.
     *
     * Вызывается при живом WS-соединении (throttle 30 сек в ChatViewModel).
     */
    override suspend fun syncCredentialsToDaemon(): Result<Unit> {
        try {
            // Живость демона проверяем model/info: /api/health на dashboard
            // может отвечать SPA HTML, а model/info отдаёт JSON только у живого API.
            val alive = restClient.getModelInfo()
            if (alive == null) {
                return Result.failure(Exception("Демон Hermes недоступен"))
            }

            var appliedCount = 0

            // 0. Локальный список провайдеров (если пуст — подтянем из демона)
            val providersToSync = _providers.value.ifEmpty {
                try {
                    restClient.getProviders().map { dto ->
                        ProviderConfig(
                            id = dto.id,
                            name = dto.name,
                            status = ProviderStatus.CONNECTED,
                            currentModel = dto.currentModel,
                            availableModels = dto.availableModels,
                            hasApiKey = true,
                            baseUrl = dto.baseUrl,
                            apiMode = ProviderApiMode.fromId(dto.apiMode),
                            isCustom = dto.isCustom
                        )
                    }
                } catch (_: Exception) { emptyList() }
            }

            // 1. Кастомные провайдеры: (пере)регистрируем в config.yaml с каноническим
            //    key_env — даже если провайдер уже есть: конфиг мог быть создан с другим
            //    key_env, тогда демон ищет ключ не под тем именем. PUT идемпотентен.
            val configProviders = restClient.getConfigProviders()
            val customProviders = providersToSync.filter { it.isCustom && !it.baseUrl.isNullOrBlank() }
            if (configProviders != null) {
                for (prov in customProviders) {
                    val model = prov.currentModel ?: prov.availableModels.firstOrNull() ?: prov.name
                    if (restClient.registerProviderInConfig(
                            name = prov.name,
                            baseUrl = prov.baseUrl!!,
                            model = model,
                            keyEnv = keyEnvNameFor(prov.name)
                        )
                    ) {
                        appliedCount++
                        android.util.Log.i("ProviderRepository", "sync: (re)registered provider '${prov.name}' in daemon config")
                    }
                }
            }

            // 2. Перенос ключей из Keystore в env демона.
            //    D4 (docs/08 §2.3): null = /api/env недоступен — пропускаем перенос
            //    (как шаг 1 при null от getConfigProviders), а не считаем env пустым.
            val envState = restClient.getEnvKeys() ?: return Result.success(Unit)
            val seenValues = mutableSetOf<String>()
            for (prov in providersToSync) {
                val apiKey = keystoreManager.getDecrypted(prov.id)
                    ?: keystoreManager.getDecrypted(prov.name)
                    ?: keystoreManager.getDecrypted("custom_${prov.name.lowercase()}")
                    ?: continue
                if (apiKey.isBlank() || !seenValues.add(apiKey)) continue

                // Для кастомных провайдеров — канонический key_env + все совместимые имена
                val candidates = mutableListOf<String>()
                if (prov.isCustom) candidates.add(keyEnvNameFor(prov.name))
                candidates.addAll(envKeyCandidatesFor(prov.id, prov.name))
                val alreadySet = candidates.any { envState[it] == true }
                if (alreadySet) continue

                for (envName in candidates.distinct()) {
                    if (restClient.putEnvVar(envName, apiKey)) {
                        appliedCount++
                        android.util.Log.i("ProviderRepository", "sync: pushed key as $envName for '${prov.name}'")
                        break
                    }
                }
            }

            // 3. Выбор модели при пустом model/info
            //    (конфиг-PUT мог сбросить секцию model — восстанавливаем и в этом случае)
            val modelInfo = restClient.getModelInfo() ?: return Result.success(Unit)
            var modelApplied = false
            if (modelInfo.model.isNullOrBlank() || modelInfo.provider.isNullOrBlank()) {
                val activeModel = settingsManager.activeModel.value
                val activeProvider = settingsManager.activeProvider.value
                if (activeModel.isNotBlank()) {
                    val res = restClient.setModel(activeModel, activeProvider)
                    modelApplied = res.isSuccess
                }
                if (!modelApplied) {
                    // Fallback: первая модель из опций демона
                    val fallbackProv = try {
                        restClient.getProviders().firstOrNull()
                    } catch (_: Exception) { null }
                    val fallbackModel = fallbackProv?.currentModel?.takeIf { it.isNotBlank() }
                    if (fallbackModel != null) {
                        val fallbackProvId = fallbackProv.id.removePrefix("custom_")
                        val res = restClient.setModel(fallbackModel, fallbackProvId)
                        modelApplied = res.isSuccess
                        if (modelApplied) {
                            settingsManager.setActiveModelAndProvider(fallbackModel, fallbackProvId, fallbackModel)
                            webSocketClient.setActiveModel(fallbackModel, fallbackProvId)
                        }
                    }
                }
            }
            android.util.Log.i("ProviderRepository", "syncCredentialsToDaemon: applied=$appliedCount modelApplied=$modelApplied")

            return Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.w("ProviderRepository", "syncCredentialsToDaemon failed: ${e.message}")
            return Result.failure(e)
        }
    }

    override suspend fun runDiagnostics(): DiagnosticsReport {
        if (settingsManager.isDemoMode.value) {
            val localGw = GatewayCheckResult(
                name = "Hermes Runtime (Автономный демо-режим)",
                url = "http://127.0.0.1:8000/api",
                isReachable = true,
                latencyMs = 2L
            )
            val demoResults = _providers.value.ifEmpty { getDemoProviders() }.map { p ->
                val randomM = p.availableModels.randomOrNull() ?: p.currentModel ?: "Hermes 3"
                ProviderDiagnosticResult(
                    providerId = p.id,
                    providerName = p.name,
                    apiMode = p.apiMode,
                    gateway = GatewayCheckResult(
                        name = p.name,
                        url = p.baseUrl ?: p.apiMode.defaultUrl,
                        isReachable = true,
                        latencyMs = (35..110).random().toLong()
                    ),
                    testedModel = ModelCheckResult(
                        modelName = randomM,
                        isSuccess = true,
                        latencyMs = (60..240).random().toLong(),
                        tokensUsed = 1
                    ),
                    totalModelsCount = p.availableModels.size
                )
            }
            return DiagnosticsReport(localGw, demoResults)
        }

        // 1. Проверяем локальный шлюз Hermes в Termux
        val localStart = System.currentTimeMillis()
        val isLocalHealthy = try {
            kotlinx.coroutines.withTimeoutOrNull(2500) { restClient.getHealth() } ?: false
        } catch (_: Exception) {
            false
        }
        val localLatency = System.currentTimeMillis() - localStart
        val localUrl = settingsManager.getRestUrl()
        val localGateway = GatewayCheckResult(
            name = "Hermes Runtime (Termux)",
            url = localUrl,
            isReachable = isLocalHealthy,
            latencyMs = localLatency,
            errorMessage = if (!isLocalHealthy) "Локальный агент не отвечает. Убедитесь, что сервер запущен в Termux." else null
        )

        // Обновляем список провайдеров из Termux только если локальный шлюз доступен
        if (isLocalHealthy && _providers.value.isEmpty()) {
            try {
                kotlinx.coroutines.withTimeoutOrNull(3000) { refreshProviders() }
            } catch (_: Exception) {}
        }

        val currentProviders = _providers.value
        val activeEnvKeys = if (isLocalHealthy) {
            try {
                kotlinx.coroutines.withTimeoutOrNull(3000) {
                    // D4: null = /api/env недоступен → в диагностике это "нет данных"
                    restClient.getEnvKeys()?.filterValues { it }?.keys ?: emptySet()
                } ?: emptySet()
            } catch (_: Exception) {
                emptySet()
            }
        } else {
            emptySet()
        }

        // Диагностируем только настроенные провайдеры (пользовательские, с ключом или активные)
        val configuredProviders = currentProviders.filter { provider ->
            provider.isCustom ||
            provider.hasApiKey ||
            provider.status == ProviderStatus.CONNECTED ||
            keystoreManager.hasKey(provider.id) ||
            keystoreManager.hasKey(provider.name) ||
            provider.id.equals(settingsManager.activeProvider.value, ignoreCase = true) ||
            provider.name.equals(settingsManager.activeProviderName.value, ignoreCase = true) ||
            activeEnvKeys.any { key ->
                val upperName = provider.name.uppercase().replace(Regex("[^A-Z0-9_]"), "_")
                val upperId = provider.id.uppercase().replace(Regex("[^A-Z0-9_]"), "_")
                key.contains(upperName) || key.contains(upperId)
            }
        }.ifEmpty { currentProviders }

        val providerResults = coroutineScope {
            configuredProviders.map { provider ->
                async {
                    try {
                        kotlinx.coroutines.withTimeoutOrNull(5000) {
                            checkProvider(provider, activeEnvKeys)
                        } ?: ProviderDiagnosticResult(
                            providerId = provider.id,
                            providerName = provider.name,
                            apiMode = provider.apiMode,
                            gateway = GatewayCheckResult(
                                name = provider.name,
                                url = provider.baseUrl ?: provider.apiMode.defaultUrl,
                                isReachable = false,
                                latencyMs = 5000,
                                errorMessage = "Таймаут шлюза (5с)"
                            ),
                            testedModel = ModelCheckResult(
                                modelName = provider.currentModel ?: "default",
                                isSuccess = false,
                                latencyMs = 5000,
                                tokensUsed = 0,
                                errorMessage = "Таймаут модели (5с)"
                            ),
                            totalModelsCount = provider.availableModels.size
                        )
                    } catch (e: Exception) {
                        ProviderDiagnosticResult(
                            providerId = provider.id,
                            providerName = provider.name,
                            apiMode = provider.apiMode,
                            gateway = GatewayCheckResult(
                                name = provider.name,
                                url = provider.baseUrl ?: provider.apiMode.defaultUrl,
                                isReachable = false,
                                latencyMs = 0,
                                errorMessage = e.localizedMessage ?: "Сбой соединения"
                            ),
                            testedModel = null,
                            totalModelsCount = provider.availableModels.size
                        )
                    }
                }
            }.awaitAll()
        }

        return DiagnosticsReport(
            localGateway = localGateway,
            providerResults = providerResults
        )
    }

    private suspend fun checkProvider(provider: ProviderConfig, activeEnvKeys: Set<String>): ProviderDiagnosticResult {
        var apiKey = keystoreManager.getDecrypted(provider.id)
            ?: keystoreManager.getDecrypted(provider.name)
            ?: keystoreManager.getDecrypted("custom_${provider.name.lowercase()}")
            ?: ""

        if (apiKey.isBlank()) {
            val candidates = mutableListOf<String>()
            val cleanName = provider.name.uppercase().replace(Regex("[^A-Z0-9_]"), "_")
            val cleanId = provider.id.uppercase().replace(Regex("[^A-Z0-9_]"), "_")

            if (provider.id.contains("zai", ignoreCase = true) || provider.name.contains("zai", ignoreCase = true) || provider.name.contains("glm", ignoreCase = true)) {
                candidates.add("ZAI_API_KEY")
                candidates.add("GLM_API_KEY")
            }
            candidates.add("HERMES_CUSTOM_${cleanName}_API_KEY")
            candidates.add("${cleanName}_API_KEY")
            candidates.add("HERMES_CUSTOM_${cleanId}_API_KEY")
            candidates.add("${cleanId}_API_KEY")
            if (provider.id.contains("qwen", ignoreCase = true) || provider.name.contains("qwen", ignoreCase = true) || provider.name.contains("alibaba", ignoreCase = true)) {
                candidates.add("DASHSCOPE_API_KEY")
                candidates.add("QWEN_API_KEY")
            }
            if (provider.id.contains("groq", ignoreCase = true) || provider.name.contains("groq", ignoreCase = true)) {
                candidates.add("GROQ_API_KEY")
            }
            if (provider.id.contains("openai", ignoreCase = true) || provider.name.contains("openai", ignoreCase = true)) {
                candidates.add("OPENAI_API_KEY")
            }
            if (provider.id.contains("anthropic", ignoreCase = true) || provider.name.contains("anthropic", ignoreCase = true)) {
                candidates.add("ANTHROPIC_API_KEY")
            }

            // Ищем точное совпадение среди реально установленных в Hermes переменных окружения
            val matchingKey = candidates.firstOrNull { it in activeEnvKeys }
            if (matchingKey != null) {
                val revealed = restClient.revealEnvVar(matchingKey)
                if (!revealed.isNullOrBlank()) {
                    apiKey = revealed
                    keystoreManager.encryptAndSave(provider.id, revealed)
                    keystoreManager.encryptAndSave(provider.name, revealed)
                    keystoreManager.encryptAndSave("custom_${provider.name.lowercase()}", revealed)
                }
            }
        }

        // Корректный baseUrl: для Z.AI используем официальный эндпоинт из Hermes или coding api
        var baseUrl = provider.baseUrl?.takeIf { it.isNotBlank() && !it.contains("api.openai.com") }
        if (provider.id.contains("zai", ignoreCase = true) || provider.name.contains("zai", ignoreCase = true) || provider.name.contains("glm", ignoreCase = true)) {
            val revealedGlm = restClient.revealEnvVar("GLM_BASE_URL")
            baseUrl = revealedGlm ?: "https://api.z.ai/api/coding/paas/v4"
        } else if (baseUrl.isNullOrBlank()) {
            baseUrl = provider.apiMode.defaultUrl
        }

        // Фильтрация моделей: исключаем генераторы картинок и транскрипцию аудио
        val chatModels = provider.availableModels.filterNot { m ->
            val lower = m.lowercase()
            lower.contains("-image") || lower.contains("wan2.") || lower.contains("transcribe") ||
            lower.contains("whisper") || lower.contains("banana") || lower.contains("embed")
        }
        val fastPreferred = chatModels.filter { m ->
            val lower = m.lowercase()
            lower.contains("flash") || lower.contains("turbo") || lower.contains("glm-5") ||
            lower.contains("qwen") || lower.contains("deepseek") || lower.contains("mini")
        }
        val candidatePool = if (fastPreferred.isNotEmpty()) fastPreferred else if (chatModels.isNotEmpty()) chatModels else provider.availableModels
        val isCurrentActiveProvider = settingsManager.activeProvider.value.equals(provider.id, ignoreCase = true) ||
                settingsManager.activeProviderName.value.equals(provider.name, ignoreCase = true)
        val activeChatModel = if (isCurrentActiveProvider) settingsManager.activeModel.value.takeIf { it.isNotBlank() } else null
        val preferredModel = activeChatModel ?: provider.currentModel?.takeIf { it.isNotBlank() }

        // Тестируем в первую очередь активную рабочую модель (ту, которая прямо сейчас используется в чате)
        val modelToTest = if (!preferredModel.isNullOrBlank() && (preferredModel in provider.availableModels || provider.availableModels.isEmpty())) {
            preferredModel
        } else {
            candidatePool.randomOrNull() ?: preferredModel ?: provider.currentModel
        }

        // Тестирование модели вне чата с минимальным расходом токенов (1 токен)
        val testedModel: ModelCheckResult? = if (!modelToTest.isNullOrBlank()) {
            if (apiKey.isBlank() && provider.apiMode != ProviderApiMode.OLLAMA_NATIVE) {
                ModelCheckResult(
                    modelName = modelToTest,
                    isSuccess = false,
                    latencyMs = 0,
                    tokensUsed = 0,
                    errorMessage = "Не указан API-ключ в настройках"
                )
            } else {
                restClient.probeModelCompletion(
                    baseUrl = baseUrl,
                    apiMode = provider.apiMode.id,
                    apiKey = apiKey,
                    model = modelToTest
                )
            }
        } else null

        // Проверка доступности шлюза
        val gatewayCheck = if (testedModel != null) {
            if (testedModel.isSuccess || testedModel.errorMessage?.contains("401") == true ||
                testedModel.errorMessage?.contains("403") == true ||
                testedModel.errorMessage?.contains("404") == true ||
                testedModel.errorMessage?.contains("429") == true
            ) {
                GatewayCheckResult(
                    name = provider.name,
                    url = baseUrl,
                    isReachable = true,
                    latencyMs = testedModel.latencyMs
                )
            } else {
                GatewayCheckResult(
                    name = provider.name,
                    url = baseUrl,
                    isReachable = false,
                    latencyMs = testedModel.latencyMs,
                    errorMessage = testedModel.errorMessage
                )
            }
        } else {
            val gw = restClient.probeGatewayReachability(baseUrl, apiKey)
            gw.copy(name = provider.name)
        }

        return ProviderDiagnosticResult(
            providerId = provider.id,
            providerName = provider.name,
            apiMode = provider.apiMode,
            gateway = gatewayCheck,
            testedModel = testedModel,
            totalModelsCount = provider.availableModels.size
        )
    }
}
