package com.hermes.mobile.persistence.datastore

import android.content.Context
import android.content.SharedPreferences
import com.hermes.mobile.domain.model.SlashCommandItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val DEFAULT_SLASH_COMMANDS = listOf(
    SlashCommandItem(
        command = "/reset",
        title = "Сброс контекста",
        description = "Начать диалог с чистого листа, сохранив историю",
        example = "/reset",
        actionId = "reset"
    ),
    SlashCommandItem(
        command = "/model",
        title = "Выбор модели",
        description = "Открыть шторку выбора активной модели и провайдера",
        example = "/model",
        actionId = "model"
    ),
    SlashCommandItem(
        command = "/clear",
        title = "Очистить чат",
        description = "Удалить все сообщения текущей беседы",
        example = "/clear",
        actionId = "clear"
    ),
    SlashCommandItem(
        command = "/status",
        title = "Статус и диагностика",
        description = "Проверка состояния локального демона и шлюзов",
        example = "/status",
        actionId = "status"
    ),
    SlashCommandItem(
        command = "/search",
        title = "Поиск информации",
        description = "Запросить поиск в интернете через агента",
        example = "/search ",
        actionId = "search"
    ),
    SlashCommandItem(
        command = "/approval",
        title = "Тест подтверждения",
        description = "Проверить карточку подтверждения действия агента",
        example = "/approval",
        actionId = "approval"
    ),
    SlashCommandItem(
        command = "/clarify",
        title = "Тест уточнения",
        description = "Проверить карточку уточняющего вопроса агента",
        example = "/clarify",
        actionId = "clarify"
    ),
    SlashCommandItem(
        command = "/help",
        title = "Справка",
        description = "Показать список возможностей и горячих клавиш",
        example = "/help",
        actionId = "help"
    )
)

class AppSettingsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("hermes_settings", Context.MODE_PRIVATE)

    private val defaultServerUrl = "ws://127.0.0.1:8000/api/ws?token=hermes_mobile_local"

    private val _serverUrl = MutableStateFlow(
        run {
            val saved = prefs.getString(KEY_SERVER_URL, defaultServerUrl) ?: defaultServerUrl
            if (saved == "ws://127.0.0.1:8000/ws") defaultServerUrl else saved
        }
    )
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _isDemoMode = MutableStateFlow(
        prefs.getBoolean(KEY_DEMO_MODE, false)
    )
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()

    private val _activeModel = MutableStateFlow(
        prefs.getString(KEY_ACTIVE_MODEL, "gpt-4o") ?: "gpt-4o"
    )
    val activeModel: StateFlow<String> = _activeModel.asStateFlow()

    private val _activeProvider = MutableStateFlow(
        (prefs.getString(KEY_ACTIVE_PROVIDER, "openai") ?: "openai").removePrefix("custom_")
    )
    val activeProvider: StateFlow<String> = _activeProvider.asStateFlow()

    private val _activeProviderName = MutableStateFlow(
        prefs.getString(KEY_ACTIVE_PROVIDER_NAME, "OpenAI") ?: "OpenAI"
    )
    val activeProviderName: StateFlow<String> = _activeProviderName.asStateFlow()

    private val _fallbackModel = MutableStateFlow(
        prefs.getString(KEY_FALLBACK_MODEL, "") ?: ""
    )
    val fallbackModel: StateFlow<String> = _fallbackModel.asStateFlow()

    private val _fallbackProvider = MutableStateFlow(
        prefs.getString(KEY_FALLBACK_PROVIDER, "") ?: ""
    )
    val fallbackProvider: StateFlow<String> = _fallbackProvider.asStateFlow()

    private val _fallbackProviderName = MutableStateFlow(
        prefs.getString(KEY_FALLBACK_PROVIDER_NAME, "") ?: ""
    )
    val fallbackProviderName: StateFlow<String> = _fallbackProviderName.asStateFlow()

    private val _appLanguage = MutableStateFlow(
        prefs.getString(KEY_APP_LANGUAGE, if (java.util.Locale.getDefault().language.startsWith("ru", ignoreCase = true)) "ru" else "en") ?: "ru"
    )
    val appLanguage: StateFlow<String> = _appLanguage.asStateFlow()

    fun setAppLanguage(lang: String) {
        val clean = if (lang.equals("en", ignoreCase = true)) "en" else "ru"
        prefs.edit().putString(KEY_APP_LANGUAGE, clean).apply()
        _appLanguage.value = clean
    }

    fun setFallbackModelAndProvider(model: String, providerId: String, providerName: String) {
        val cleanProvider = providerId.removePrefix("custom_")
        prefs.edit()
            .putString(KEY_FALLBACK_MODEL, model)
            .putString(KEY_FALLBACK_PROVIDER, cleanProvider)
            .putString(KEY_FALLBACK_PROVIDER_NAME, providerName)
            .apply()
        _fallbackModel.value = model
        _fallbackProvider.value = cleanProvider
        _fallbackProviderName.value = providerName
    }

    fun clearFallbackModel() {
        prefs.edit()
            .remove(KEY_FALLBACK_MODEL)
            .remove(KEY_FALLBACK_PROVIDER)
            .remove(KEY_FALLBACK_PROVIDER_NAME)
            .apply()
        _fallbackModel.value = ""
        _fallbackProvider.value = ""
        _fallbackProviderName.value = ""
    }

    private val _lastActiveSessionId = MutableStateFlow<String?>(
        prefs.getString(KEY_LAST_ACTIVE_SESSION, null)
    )
    val lastActiveSessionId: StateFlow<String?> = _lastActiveSessionId.asStateFlow()

    fun getLastActiveSessionId(): String? = _lastActiveSessionId.value

    fun setLastActiveSessionId(sessionId: String?) {
        val editor = prefs.edit()
        if (sessionId.isNullOrBlank()) {
            editor.remove(KEY_LAST_ACTIVE_SESSION)
        } else {
            editor.putString(KEY_LAST_ACTIVE_SESSION, sessionId)
        }
        editor.apply()
        _lastActiveSessionId.value = sessionId
    }

    fun isFirstLaunch(): Boolean = prefs.getBoolean(KEY_FIRST_LAUNCH, true)

    fun setFirstLaunchDone() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }

    fun setServerUrl(url: String) {
        val cleanUrl = url.trim()
        prefs.edit().putString(KEY_SERVER_URL, cleanUrl).apply()
        _serverUrl.value = cleanUrl
    }

    fun setDemoMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEMO_MODE, enabled).apply()
        _isDemoMode.value = enabled
    }

    fun setActiveModel(model: String) {
        prefs.edit().putString(KEY_ACTIVE_MODEL, model).apply()
        _activeModel.value = model
    }

    fun setActiveModelAndProvider(model: String, providerId: String, providerName: String) {
        val cleanProvider = providerId.removePrefix("custom_")
        prefs.edit()
            .putString(KEY_ACTIVE_MODEL, model)
            .putString(KEY_ACTIVE_PROVIDER, cleanProvider)
            .putString(KEY_ACTIVE_PROVIDER_NAME, providerName)
            .apply()
        _activeModel.value = model
        _activeProvider.value = cleanProvider
        _activeProviderName.value = providerName
    }

    fun getRestUrl(): String {
        val wsUrl = _serverUrl.value
        val cleanUrl = wsUrl.substringBefore("?")
            .replace("ws://", "http://")
            .replace("wss://", "https://")
            .replace("/api/ws", "")
            .replace("/ws", "")
            .trimEnd('/')
        return "$cleanUrl/api"
    }

    fun getSessionToken(): String {
        val wsUrl = _serverUrl.value
        if (wsUrl.contains("token=")) {
            return wsUrl.substringAfter("token=").substringBefore("&")
        }
        return "hermes_mobile_local"
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _slashCommands = MutableStateFlow<List<SlashCommandItem>>(loadSlashCommands())
    val slashCommands: StateFlow<List<SlashCommandItem>> = _slashCommands.asStateFlow()

    private fun loadSlashCommands(): List<SlashCommandItem> {
        val raw = prefs.getString(KEY_SLASH_COMMANDS, null)
        if (!raw.isNullOrBlank()) {
            try {
                val list = json.decodeFromString<List<SlashCommandItem>>(raw)
                if (list.isNotEmpty()) return list
            } catch (_: Exception) {}
        }
        return DEFAULT_SLASH_COMMANDS
    }

    fun saveSlashCommands(commands: List<SlashCommandItem>) {
        val jsonStr = json.encodeToString(commands)
        prefs.edit().putString(KEY_SLASH_COMMANDS, jsonStr).apply()
        _slashCommands.value = commands
    }

    fun addSlashCommand(command: SlashCommandItem) {
        val normalizedCmd = if (command.command.startsWith("/")) command.command.trim() else "/${command.command.trim()}"
        val updatedCmd = command.copy(
            command = normalizedCmd,
            example = if (command.example.isNotBlank()) command.example.trim() else normalizedCmd,
            isCustom = true
        )
        val current = _slashCommands.value.toMutableList()
        current.removeAll { it.command.equals(normalizedCmd, ignoreCase = true) }
        current.add(updatedCmd)
        saveSlashCommands(current)
    }

    fun updateSlashCommand(oldCommandStr: String, updated: SlashCommandItem) {
        val normalizedCmd = if (updated.command.startsWith("/")) updated.command.trim() else "/${updated.command.trim()}"
        val updatedCmd = updated.copy(
            command = normalizedCmd,
            example = if (updated.example.isNotBlank()) updated.example.trim() else normalizedCmd
        )
        val current = _slashCommands.value.toMutableList()
        val index = current.indexOfFirst { it.command.equals(oldCommandStr.trim(), ignoreCase = true) }
        if (index != -1) {
            current[index] = updatedCmd
        } else {
            current.removeAll { it.command.equals(normalizedCmd, ignoreCase = true) }
            current.add(updatedCmd)
        }
        saveSlashCommands(current)
    }

    fun moveSlashCommand(fromIndex: Int, toIndex: Int) {
        val current = _slashCommands.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices && fromIndex != toIndex) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            saveSlashCommands(current)
        }
    }

    fun deleteSlashCommand(commandStr: String) {
        val current = _slashCommands.value.filterNot { it.command.equals(commandStr.trim(), ignoreCase = true) }
        saveSlashCommands(current)
    }

    fun resetSlashCommandsToDefault() {
        saveSlashCommands(DEFAULT_SLASH_COMMANDS)
    }

    companion object {
        private const val KEY_SERVER_URL = "key_server_url"
        private const val KEY_DEMO_MODE = "key_demo_mode"
        private const val KEY_ACTIVE_MODEL = "key_active_model"
        private const val KEY_ACTIVE_PROVIDER = "key_active_provider"
        private const val KEY_ACTIVE_PROVIDER_NAME = "key_active_provider_name"
        private const val KEY_LAST_ACTIVE_SESSION = "key_last_active_session"
        private const val KEY_FIRST_LAUNCH = "key_first_launch"
        private const val KEY_SLASH_COMMANDS = "key_slash_commands"
        private const val KEY_FALLBACK_MODEL = "key_fallback_model"
        private const val KEY_FALLBACK_PROVIDER = "key_fallback_provider"
        private const val KEY_FALLBACK_PROVIDER_NAME = "key_fallback_provider_name"
        private const val KEY_APP_LANGUAGE = "key_app_language"
    }
}
