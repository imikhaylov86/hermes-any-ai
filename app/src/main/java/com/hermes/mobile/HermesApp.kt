package com.hermes.mobile

import android.app.Application
import com.hermes.mobile.domain.repository.ChatRepository
import com.hermes.mobile.domain.repository.ChatRepositoryImpl
import com.hermes.mobile.domain.repository.SessionRepository
import com.hermes.mobile.domain.repository.SessionRepositoryImpl
import com.hermes.mobile.persistence.database.AppDatabase
import com.hermes.mobile.persistence.datastore.AppSettingsManager
import com.hermes.mobile.security.KeystoreManager
import com.hermes.mobile.transport.rest.DashboardRestClient
import com.hermes.mobile.transport.websocket.MobileWebSocketClient

class HermesApp : Application() {

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

    override fun onCreate() {
        super.onCreate()
        instance = this

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

        webSocketClient = MobileWebSocketClient(endpointUrl = settingsManager.serverUrl.value)
        // G3 (03 §D6): токен в заголовке WS upgrade, не в URL
        webSocketClient.updateSessionToken(settingsManager.getSessionToken())
        // G4 (03 §D9г): восстановление серверной сессии для resume при onOpen
        webSocketClient.restoreHermesSession(settingsManager.hermesSessionId.value)
        webSocketClient.setActiveModel(settingsManager.activeModel.value, settingsManager.activeProvider.value)

        restClient = DashboardRestClient(
            initialBaseUrl = settingsManager.getRestUrl(),
            sessionToken = settingsManager.getSessionToken()
        )

        // Fallback: если сервер поднялся без env-патча (случайный токен), WS-клиент
        // заберёт живой токен из HTML dashboard и синхронизирует его с REST-клиентом
        webSocketClient.onSessionTokenRecovered = { token ->
            restClient.updateSessionToken(token)
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
            restClient = restClient
        )

        sessionRepository = SessionRepositoryImpl(
            sessionDao = database.sessionDao(),
            restClient = restClient,
            webSocketClient = webSocketClient,
            settingsManager = settingsManager,
            messageDao = database.messageDao()
        )

        providerRepository = com.hermes.mobile.domain.repository.ProviderRepositoryImpl(
            restClient = restClient,
            webSocketClient = webSocketClient,
            settingsManager = settingsManager,
            keystoreManager = keystoreManager
        )

        updateManager = com.hermes.mobile.domain.update.UpdateManager(this)
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

            val sourceDb = when {
                migrationDir != null && java.io.File(migrationDir, "hermes_mobile_database").exists() ->
                    java.io.File(migrationDir, "hermes_mobile_database")
                java.io.File(getExternalFilesDir(null), "hermes_mobile_database").exists() ->
                    java.io.File(getExternalFilesDir(null), "hermes_mobile_database")
                java.io.File("/sdcard/Android/data/com.hermes.mobile.debug/files/hermes_mobile_database").exists() ->
                    java.io.File("/sdcard/Android/data/com.hermes.mobile.debug/files/hermes_mobile_database")
                java.io.File("/data/local/tmp/hermes_mobile_database").exists() ->
                    java.io.File("/data/local/tmp/hermes_mobile_database")
                java.io.File("/data/local/tmp/db").exists() ->
                    java.io.File("/data/local/tmp/db")
                else -> null
            }

            if (sourceDb != null && sourceDb.exists() && sourceDb.length() > 0) {
                if (!dbTarget.exists() || dbTarget.length() < 25000) {
                    dbTarget.parentFile?.mkdirs()
                    sourceDb.copyTo(dbTarget, overwrite = true)
                    java.io.File(dbTarget.parentFile, "hermes_mobile_database-wal").delete()
                    java.io.File(dbTarget.parentFile, "hermes_mobile_database-shm").delete()
                    try { sourceDb.delete() } catch (_: Exception) {}
                    android.util.Log.i("HermesApp", "Migration: Successfully imported database (${dbTarget.length()} bytes)")
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
    }
}
