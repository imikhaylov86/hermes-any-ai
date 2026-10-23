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

        database = AppDatabase.getDatabase(this)
        keystoreManager = KeystoreManager(this)
        settingsManager = AppSettingsManager(this)

        webSocketClient = MobileWebSocketClient(endpointUrl = settingsManager.serverUrl.value)
        webSocketClient.setActiveModel(settingsManager.activeModel.value, settingsManager.activeProvider.value)

        restClient = DashboardRestClient(
            initialBaseUrl = settingsManager.getRestUrl(),
            sessionToken = settingsManager.getSessionToken()
        )

        chatRepository = ChatRepositoryImpl(
            messageDao = database.messageDao(),
            sessionDao = database.sessionDao(),
            webSocketClient = webSocketClient,
            settingsManager = settingsManager
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

    companion object {
        lateinit var instance: HermesApp
            private set
    }
}
