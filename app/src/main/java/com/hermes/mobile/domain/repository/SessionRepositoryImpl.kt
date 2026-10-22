package com.hermes.mobile.domain.repository

import com.hermes.mobile.domain.model.SessionInfo
import com.hermes.mobile.domain.model.generateSessionTitle
import com.hermes.mobile.persistence.database.MessageDao
import com.hermes.mobile.persistence.database.SessionDao
import com.hermes.mobile.persistence.database.SessionEntity
import com.hermes.mobile.persistence.datastore.AppSettingsManager
import com.hermes.mobile.transport.rest.DashboardRestClient
import com.hermes.mobile.transport.websocket.MobileWebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

class SessionRepositoryImpl(
    private val sessionDao: SessionDao,
    private val restClient: DashboardRestClient,
    private val webSocketClient: MobileWebSocketClient? = null,
    private val settingsManager: AppSettingsManager? = null,
    private val messageDao: MessageDao? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : SessionRepository {

    init {
        syncSessionsFromRemote()
    }

    private fun syncSessionsFromRemote() {
        scope.launch {
            val remoteSessions = restClient.getSessions()
            if (remoteSessions.isNotEmpty()) {
                remoteSessions.forEach { dto ->
                    val entity = SessionEntity(
                        id = dto.id,
                        title = dto.title,
                        createdAtEpochMs = dto.createdAt,
                        updatedAtEpochMs = dto.updatedAt,
                        model = dto.model ?: "Hermes 3",
                        isPinned = dto.isPinned,
                        isArchived = dto.isArchived,
                        lastMessagePreview = dto.lastMessagePreview ?: ""
                    )
                    sessionDao.insertSession(entity)
                }
                settingsManager?.setFirstLaunchDone()
            } else if (settingsManager?.isFirstLaunch() == true) {
                // Если сессий нет в БД и это первый запуск, создаем приветственную по умолчанию
                val defaultSession = SessionEntity(
                    id = "sess_welcome",
                    title = "Диалог с Hermes Agent",
                    createdAtEpochMs = System.currentTimeMillis(),
                    updatedAtEpochMs = System.currentTimeMillis(),
                    model = "Hermes 3 (Llama-3.1 8B)",
                    isPinned = true,
                    isArchived = false,
                    lastMessagePreview = "Готов к работе в Termux"
                )
                sessionDao.insertSession(defaultSession)
                settingsManager.setFirstLaunchDone()
            }

            // Ретроспективно именуем ранее безымянные сессии по первому сообщению пользователя
            if (messageDao != null) {
                try {
                    val unnamed = sessionDao.getUnnamedSessions()
                    unnamed.forEach { sess ->
                        val firstUserMsg = messageDao.getFirstUserMessage(sess.id)
                        if (firstUserMsg != null && firstUserMsg.content.isNotBlank()) {
                            val autoTitle = generateSessionTitle(firstUserMsg.content, firstUserMsg.toDomain().attachments)
                            sessionDao.updateTitleAndPreview(
                                sess.id,
                                autoTitle,
                                firstUserMsg.content.take(100),
                                sess.updatedAtEpochMs
                            )
                        }
                    }
                } catch (e: Exception) {
                    // Ignore auto-naming sync errors
                }
            }
        }
    }

    override fun getSessionsFlow(): Flow<List<SessionInfo>> {
        return sessionDao.getAllSessions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getSessionById(sessionId: String): SessionInfo? {
        return sessionDao.getSessionById(sessionId)?.toDomain()
    }

    override suspend fun getLatestSession(): SessionInfo? {
        return sessionDao.getLatestSession()?.toDomain()
    }

    override suspend fun createSession(title: String, model: String): SessionInfo {
        val currentModel = if (model != "Hermes 3") model else (settingsManager?.activeModel?.value ?: model)
        val currentProvider = settingsManager?.activeProvider?.value ?: ""
        val newId = "sess_${UUID.randomUUID().toString().take(8)}"
        val session = SessionInfo(
            id = newId,
            title = title,
            createdAtEpochMs = System.currentTimeMillis(),
            updatedAtEpochMs = System.currentTimeMillis(),
            model = currentModel,
            isPinned = false,
            isArchived = false,
            lastMessagePreview = ""
        )
        sessionDao.insertSession(SessionEntity.fromDomain(session))
        restClient.createSession(title, currentModel)
        webSocketClient?.setActiveModel(currentModel, currentProvider)
        return session
    }

    override suspend fun updateSessionTitle(sessionId: String, newTitle: String) {
        sessionDao.updateTitle(sessionId, newTitle, System.currentTimeMillis())
    }

    override suspend fun togglePinSession(sessionId: String) {
        sessionDao.togglePin(sessionId)
    }

    override suspend fun toggleArchiveSession(sessionId: String) {
        sessionDao.toggleArchive(sessionId)
    }

    override suspend fun deleteSession(sessionId: String) {
        sessionDao.deleteSession(sessionId)
        messageDao?.deleteMessagesForSession(sessionId)
        if (settingsManager?.getLastActiveSessionId() == sessionId) {
            settingsManager.setLastActiveSessionId(null)
        }
        try {
            restClient.deleteSession(sessionId)
        } catch (e: Exception) {
            // Safe ignore remote errors
        }
    }
}
