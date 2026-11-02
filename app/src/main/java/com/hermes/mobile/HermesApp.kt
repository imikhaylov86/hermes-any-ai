package com.hermes.mobile

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.hermes.mobile.presentation.service.NotificationHelper
import com.hermes.mobile.domain.repository.ChatRepository
import com.hermes.mobile.domain.repository.ChatRepositoryImpl
import com.hermes.mobile.domain.repository.SessionRepository
import com.hermes.mobile.domain.repository.SessionRepositoryImpl
import com.hermes.mobile.persistence.database.AppDatabase
import com.hermes.mobile.persistence.datastore.AppSettingsManager
import com.hermes.mobile.security.KeystoreManager
import com.hermes.mobile.transport.rest.DashboardRestClient
import com.hermes.mobile.transport.websocket.MobileWebSocketClient
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class HermesApp : Application(), coil.ImageLoaderFactory {

    override fun newImageLoader(): coil.ImageLoader {
        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val url = original.url.toString()
                if (url.contains(":8000") || url.contains("/api/files/download") || url.contains("/api/attachments/upload")) {
                    val token = try { settingsManager.getSessionToken() } catch (e: Exception) { "hermes_mobile_local" }
                    val newReq = original.newBuilder()
                        .header("X-Hermes-Session-Token", token)
                        .header("Authorization", "Bearer $token")
                        .build()
                    chain.proceed(newReq)
                } else {
                    chain.proceed(original)
                }
            }
            .build()

        return coil.ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .crossfade(true)
            .build()
    }

    lateinit var database: AppDatabase
        private set
    lateinit var keystoreManager: KeystoreManager
        private set
    lateinit var settingsManager: AppSettingsManager
        private set

    lateinit var chatRepository: ChatRepository
        private set

    lateinit var sessionRepository: SessionRepository
        private set

    lateinit var webSocketClient: MobileWebSocketClient
        private set

    lateinit var restClient: DashboardRestClient
        private set

    lateinit var providerRepository: com.hermes.mobile.domain.repository.ProviderRepository
        private set

    lateinit var updateManager: com.hermes.mobile.domain.update.UpdateManager
        private set

    private val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        instance = this

        NotificationHelper.createNotificationChannels(this)

        var resumedActivities = 0
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                resumedActivities++
                isAppInForeground = resumedActivities > 0
            }

            override fun onActivityPaused(activity: Activity) {
                resumedActivities = (resumedActivities - 1).coerceAtLeast(0)
                isAppInForeground = resumedActivities > 0
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        checkAndPerformDataMigration()
        database = AppDatabase.getDatabase(this)
        keystoreManager = KeystoreManager(this)
        settingsManager = AppSettingsManager(this)

        if (packageName.endsWith(".debug")) {
            // G1 (docs/07 §3): экспорт ключей — только debug-сборка И явный флаг
            // разработчика в настройках. По умолчанию выключен. В релизе экспорта нет.
            if (settingsManager.isDebugKeysExportEnabled.value) {
                exportDebugCredentials()
            }
        } else {
            importKeystoreMigration()
        }

        if (settingsManager.fallbackModel.value == "gpt-6-luna") {
            settingsManager.clearFallbackModel()
        }
        if (settingsManager.activeModel.value == "gpt-6-luna") {
            settingsManager.setActiveModelAndProvider("glm-5.3-flash", "basisweb", "basisweb")
        }
        val currentActiveProv = settingsManager.activeProvider.value
        if (currentActiveProv.startsWith("custom:") || currentActiveProv.startsWith("custom_")) {
            val clean = currentActiveProv.removePrefix("custom:").removePrefix("custom_")
            settingsManager.setActiveModelAndProvider(
                settingsManager.activeModel.value,
                clean,
                clean
            )
        }
        val savedCustom = settingsManager.getSavedCustomProviders()
        if (savedCustom.none { it.name.equals("basisweb", ignoreCase = true) }) {
            settingsManager.saveCustomProvider(
                com.hermes.mobile.persistence.datastore.SavedCustomProvider(
                    id = "basisweb",
                    name = "basisweb",
                    baseUrl = "https://api.basisweb.ru/v1",
                    apiMode = "openai_compatible",
                    models = listOf("glm-5.3-flash"),
                    currentModel = "glm-5.3-flash"
                )
            )
        }
        if (savedCustom.none { it.name.equals("z.ai", ignoreCase = true) || it.name.equals("zai", ignoreCase = true) }) {
            settingsManager.saveCustomProvider(
                com.hermes.mobile.persistence.datastore.SavedCustomProvider(
                    id = "z.ai",
                    name = "z.ai",
                    baseUrl = "https://api.z.ai/api/coding/paas/v4",
                    apiMode = "openai_compatible",
                    models = listOf("glm-5.3-flash"),
                    currentModel = "glm-5.3-flash"
                )
            )
        }

        webSocketClient = MobileWebSocketClient(endpointUrl = settingsManager.serverUrl.value)
        // G3 (03 §D6): токен в заголовке WS upgrade, не в URL
        webSocketClient.updateSessionToken(settingsManager.getSessionToken())
        // G4 (03 §D9г): восстановление серверной сессии для resume при onOpen
        webSocketClient.restoreHermesSession(settingsManager.hermesSessionId.value)
        val lastChatId = settingsManager.getLastActiveSessionId()
        if (!lastChatId.isNullOrBlank()) {
            appScope.launch {
                val lastSession = database.sessionDao().getSessionById(lastChatId)
                if (!lastSession?.hermesSessionId.isNullOrBlank()) {
                    webSocketClient.restoreHermesSession(lastSession!!.hermesSessionId, lastChatId)
                }
            }
        }
        webSocketClient.setActiveModel(settingsManager.activeModel.value, settingsManager.activeProvider.value)

        restClient = DashboardRestClient(
            initialBaseUrl = settingsManager.getRestUrl(),
            sessionToken = settingsManager.getSessionToken()
        )

        // Fallback: если сервер поднялся без env-патча (случайный токен), WS-клиент
        // заберёт живой токен из HTML dashboard и синхронизирует его с REST-клиентом
        webSocketClient.onSessionTokenRecovered = { token ->
            restClient.updateSessionToken(token)
            settingsManager.updateSessionToken(token)
        }

        // G4 (03 §D9г): персист серверного идентификатора сессии для resume
        webSocketClient.onHermesSessionEstablished = { sessionId ->
            settingsManager.setHermesSessionId(sessionId)
        }

        chatRepository = ChatRepositoryImpl(
            messageDao = database.messageDao(),
            sessionDao = database.sessionDao(),
            webSocketClient = webSocketClient,
            settingsManager = settingsManager,
            restClient = restClient,
            archivedPayloadDao = database.archivedSessionPayloadDao()
        )

        sessionRepository = SessionRepositoryImpl(
            sessionDao = database.sessionDao(),
            restClient = restClient,
            webSocketClient = webSocketClient,
            settingsManager = settingsManager,
            messageDao = database.messageDao(),
            archivedPayloadDao = database.archivedSessionPayloadDao(),
            database = database
        )

        // G17 (03 §D10): персист hermesSessionId в Room для конкретного чата
        webSocketClient.onHermesSessionCreatedForChat = { chatId, sid ->
            appScope.launch {
                sessionRepository.updateHermesSessionId(chatId, sid)
            }
        }

        providerRepository = com.hermes.mobile.domain.repository.ProviderRepositoryImpl(
            restClient = restClient,
            webSocketClient = webSocketClient,
            settingsManager = settingsManager,
            keystoreManager = keystoreManager
        )

        updateManager = com.hermes.mobile.domain.update.UpdateManager(this)

        registerNetworkCallback()
    }

    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null

    private fun registerNetworkCallback() {
        try {
            val connectivityManager = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return
            var lastChangeEpoch = 0L

            val callback = object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    val now = System.currentTimeMillis()
                    // Дебаунс 800 мс — против шторма событий при одновременном включении/выключении интерфейсов
                    if (now - lastChangeEpoch < 800L) return
                    lastChangeEpoch = now
                    android.util.Log.i("HermesApp", "Network onAvailable: $network -> refreshing connection pools and reconnecting")
                    appScope.launch {
                        kotlinx.coroutines.delay(600L) // Небольшая пауза для стабилизации сетевых маршрутов ОС
                        try {
                            restClient.evictConnectionPool()
                        } catch (e: Exception) {
                            android.util.Log.w("HermesApp", "evictConnectionPool failed: ${e.message}")
                        }
                        webSocketClient.reconnect(force = true)
                    }
                }

                override fun onLost(network: android.net.Network) {
                    android.util.Log.i("HermesApp", "Network onLost: $network")
                }
            }
            networkCallback = callback
            connectivityManager.registerDefaultNetworkCallback(callback)
            android.util.Log.i("HermesApp", "Default NetworkCallback registered successfully")
        } catch (e: Exception) {
            android.util.Log.e("HermesApp", "Failed to register NetworkCallback", e)
        }
    }

    /**
     * G1 (docs/07 §3): экспорт расшифрованных ключей для переноса между сборками.
     * Вызывается только в debug-сборке при включённом флаге разработчика
     * (Настройки → Параметры соединения → «Экспорт ключей»).
     */
    private fun exportDebugCredentials() {
        try {
            val prefs = getSharedPreferences("hermes_secure_storage", MODE_PRIVATE)
            val all = prefs.all
            if (all.isNotEmpty()) {
                val json = org.json.JSONObject()
                for (key in all.keys) {
                    val decrypted = keystoreManager.getDecrypted(key)
                    if (decrypted != null) {
                        json.put(key, decrypted)
                    }
                }
                val extDir = getExternalFilesDir(null)
                if (extDir != null) {
                    java.io.File(extDir, "keys_plain.json").writeText(json.toString())
                }
                android.util.Log.i("HermesApp", "Exported ${json.length()} credentials successfully")
            }
        } catch (e: Exception) {
            android.util.Log.e("HermesApp", "Export credentials error", e)
        }
    }

    /**
     * G7 (07 §G7): завершение приложения — явное освобождение ресурсов
     * transport/domain перед смертью процесса (onTerminate вызывается
     * системой только при полном завершении App-процесса).
     */
    override fun onTerminate() {
        android.util.Log.i("HermesApp", "onTerminate: shutting down repositories and WS client")
        try {
            networkCallback?.let {
                val cm = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                cm?.unregisterNetworkCallback(it)
            }
        } catch (e: Exception) {
            android.util.Log.w("HermesApp", "unregisterNetworkCallback failed: ${e.message}")
        }
        try {
            appScope.cancel()
        } catch (e: Exception) {
            android.util.Log.w("HermesApp", "appScope.cancel failed: ${e.message}")
        }
        try {
            chatRepository.shutdown()
        } catch (e: Exception) {
            android.util.Log.w("HermesApp", "chatRepository.shutdown failed: ${e.message}")
        }
        try {
            webSocketClient.shutdown()
        } catch (e: Exception) {
            android.util.Log.w("HermesApp", "webSocketClient.shutdown failed: ${e.message}")
        }
        super.onTerminate()
    }

    private fun importKeystoreMigration() {
        try {
            val migrationDir = getExternalFilesDir("migration")
            val sourceKeys = when {
                migrationDir != null && java.io.File(migrationDir, "keys_plain.json").exists() ->
                    java.io.File(migrationDir, "keys_plain.json")
                java.io.File(getExternalFilesDir(null), "keys_plain.json").exists() ->
                    java.io.File(getExternalFilesDir(null), "keys_plain.json")
                java.io.File("/sdcard/Android/data/com.hermes.mobile.debug/files/keys_plain.json").exists() ->
                    java.io.File("/sdcard/Android/data/com.hermes.mobile.debug/files/keys_plain.json")
                java.io.File("/data/local/tmp/keys_plain.json").exists() ->
                    java.io.File("/data/local/tmp/keys_plain.json")
                else -> null
            }

            if (sourceKeys != null && sourceKeys.exists()) {
                val json = org.json.JSONObject(sourceKeys.readText())
                val keys = json.keys()
                var count = 0
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = json.optString(key)
                    if (!value.isNullOrEmpty()) {
                        keystoreManager.encryptAndSave(key, value)
                        count++
                    }
                }
                try { sourceKeys.delete() } catch (_: Exception) {}
                android.util.Log.i("HermesApp", "Migration: Successfully imported $count Keystore credentials")
            }
        } catch (e: Exception) {
            android.util.Log.e("HermesApp", "Migration Keystore error", e)
        }
    }

    private fun checkAndPerformDataMigration() {
        try {
            val dbTarget = getDatabasePath("hermes_mobile_database")
            val migrationDir = getExternalFilesDir("migration")

            val candidatePaths = listOfNotNull(
                migrationDir?.let { java.io.File(it, "hermes_mobile_database") },
                java.io.File(getExternalFilesDir(null), "hermes_mobile_database"),
                java.io.File("/sdcard/HermesVault/hermes_mobile_database"),
                java.io.File("/sdcard/Download/hermes_mobile_database"),
                java.io.File("/sdcard/HermesVault/tmp/hermes_mobile_database"),
                java.io.File("/sdcard/Android/data/com.hermes.mobile.debug/files/hermes_mobile_database"),
                java.io.File("/data/local/tmp/hermes_mobile_database"),
                java.io.File("/data/local/tmp/db")
            )
            val sourceDb = candidatePaths.firstOrNull { it.exists() && it.length() > 0 }

            if (sourceDb != null && sourceDb.exists() && sourceDb.length() > 0) {
                if (!dbTarget.exists() || dbTarget.length() < 35000 || sourceDb.length() > dbTarget.length() + 10000) {
                    dbTarget.parentFile?.mkdirs()
                    sourceDb.copyTo(dbTarget, overwrite = true)
                    java.io.File(dbTarget.parentFile, "hermes_mobile_database-wal").delete()
                    java.io.File(dbTarget.parentFile, "hermes_mobile_database-shm").delete()
                    android.util.Log.i("HermesApp", "Migration: Successfully imported database (${dbTarget.length()} bytes)")
                }
            }

            // Проверка наличия JSON-бэкапа Hermes_Backup_*.json
            val jsonDirs = listOfNotNull(
                migrationDir,
                getExternalFilesDir(null),
                java.io.File("/sdcard/HermesVault"),
                java.io.File("/sdcard/Download")
            )
            val jsonCandidates = jsonDirs.flatMap { dir ->
                dir.listFiles { _, name -> name.startsWith("Hermes_Backup") && name.endsWith(".json") }?.toList().orEmpty()
            }
            val backupJsonFile = jsonCandidates.maxByOrNull { it.lastModified() }
            if (backupJsonFile != null && backupJsonFile.exists()) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        val sessionDao = database.sessionDao()
                        val messageDao = database.messageDao()
                        val localSessions = sessionDao.getAllSessionsList()
                        if (localSessions.isEmpty() || localSessions.all { it.id == "sess_welcome" }) {
                            val content = backupJsonFile.readText()
                            val (sCount, mCount) = com.hermes.mobile.domain.backup.ChatBackupManager.restoreFromJson(content, sessionDao, messageDao)
                            android.util.Log.i("HermesApp", "Migration: Successfully auto-imported JSON backup ($sCount sessions, $mCount messages) from ${backupJsonFile.name}")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("HermesApp", "Migration: Error auto-importing JSON backup", e)
                    }
                }
            }

            val prefsDir = java.io.File(applicationInfo.dataDir, "shared_prefs")
            val sourceSettings = when {
                migrationDir != null && java.io.File(migrationDir, "hermes_settings.xml").exists() ->
                    java.io.File(migrationDir, "hermes_settings.xml")
                java.io.File(getExternalFilesDir(null), "hermes_settings.xml").exists() ->
                    java.io.File(getExternalFilesDir(null), "hermes_settings.xml")
                java.io.File("/sdcard/Android/data/com.hermes.mobile.debug/files/hermes_settings.xml").exists() ->
                    java.io.File("/sdcard/Android/data/com.hermes.mobile.debug/files/hermes_settings.xml")
                java.io.File("/data/local/tmp/hermes_settings.xml").exists() ->
                    java.io.File("/data/local/tmp/hermes_settings.xml")
                else -> null
            }
            if (sourceSettings != null && sourceSettings.exists()) {
                val targetSettings = java.io.File(prefsDir, "hermes_settings.xml")
                if (!targetSettings.exists() || targetSettings.length() < 100) {
                    prefsDir.mkdirs()
                    sourceSettings.copyTo(targetSettings, overwrite = true)
                    try { sourceSettings.delete() } catch (_: Exception) {}
                    android.util.Log.i("HermesApp", "Migration: Successfully imported hermes_settings.xml")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("HermesApp", "Migration error", e)
        }
    }

    companion object {
        lateinit var instance: HermesApp
            private set

        @Volatile
        var isAppInForeground: Boolean = false
            private set
    }
}
