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

import com.hermes.mobile.persistence.database.ArchivedSessionPayloadDao
import com.hermes.mobile.persistence.database.ArchivedSessionPayloadEntity
import com.hermes.mobile.persistence.database.SessionCompressionHelper
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

class SessionRepositoryImpl(
    private val sessionDao: SessionDao,
    private val restClient: DashboardRestClient,
    private val webSocketClient: MobileWebSocketClient? = null,
    private val settingsManager: AppSettingsManager? = null,
    private val messageDao: MessageDao? = null,
    private val archivedPayloadDao: ArchivedSessionPayloadDao? = null,
    private val database: com.hermes.mobile.persistence.database.AppDatabase? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : SessionRepository {

    private fun checkpointDatabase() {
        try {
            database?.openHelper?.writableDatabase?.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
        } catch (_: Exception) {}
    }

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
            } else {
                val localSessions = sessionDao.getAllSessionsList()
                if (localSessions.isNotEmpty()) {
                    settingsManager?.setFirstLaunchDone()
                    // Если есть реальные пользовательские сессии, а sess_welcome пустая — удаляем ее
                    if (localSessions.any { it.id != "sess_welcome" }) {
                        val welcomeMsgs = messageDao?.getMessagesListForSession("sess_welcome")
                        if (welcomeMsgs.isNullOrEmpty()) {
                            sessionDao.deleteSession("sess_welcome")
                        }
                    }
                } else if (settingsManager?.isFirstLaunch() == true) {
                    // Если сессий действительно нет в БД и это первый запуск, создаем приветственную по умолчанию
                    val defaultSession = SessionEntity(
                        id = "sess_welcome",
                        title = "Диалог с Hermes Agent",
                        createdAtEpochMs = System.currentTimeMillis(),
                        updatedAtEpochMs = System.currentTimeMillis(),
                        model = "Hermes 3 (Llama-3.1 8B)",
                        isPinned = false,
                        isArchived = false,
                        lastMessagePreview = "Готов к работе в Termux"
                    )
                    sessionDao.insertSession(defaultSession)
                    settingsManager.setFirstLaunchDone()
                }
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
        return sessionDao.getActiveSessions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getArchivedSessionsFlow(): Flow<List<SessionInfo>> {
        return sessionDao.getArchivedSessions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getArchivedCountFlow(): Flow<Int> {
        return sessionDao.getArchivedCount()
    }

    override suspend fun getSessionById(sessionId: String): SessionInfo? {
        return sessionDao.getSessionById(sessionId)?.toDomain()
    }

    override fun getSessionByIdFlow(sessionId: String): Flow<SessionInfo?> {
        return sessionDao.getSessionByIdFlow(sessionId).map { it?.toDomain() }
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
        val current = sessionDao.getSessionById(sessionId) ?: return
        if (current.isArchived) {
            unarchiveSessionWithDecompression(sessionId)
        } else {
            archiveSessionWithCompression(sessionId)
        }
    }

    override suspend fun archiveSessionWithCompression(sessionId: String) {
        val msgs = messageDao?.getMessagesListForSession(sessionId) ?: emptyList()
        if (msgs.isNotEmpty()) {
            try {
                val (compressed, origSize) = SessionCompressionHelper.compressMessages(msgs)
                val entity = ArchivedSessionPayloadEntity(
                    sessionId = sessionId,
                    messageCount = msgs.size,
                    originalSizeBytes = origSize,
                    compressedSizeBytes = compressed.size.toLong(),
                    compressedPayload = compressed,
                    archivedAtEpochMs = System.currentTimeMillis()
                )
                archivedPayloadDao?.insertPayload(entity)
                messageDao?.deleteMessagesForSession(sessionId)
            } catch (e: Exception) {
                android.util.Log.e("SessionRepo", "Compression failed for $sessionId: ${e.message}", e)
            }
        }
        sessionDao.setArchived(sessionId, true, System.currentTimeMillis())
    }

    override suspend fun unarchiveSessionWithDecompression(sessionId: String) {
        val payload = archivedPayloadDao?.getPayload(sessionId)
        if (payload != null) {
            try {
                val msgs = SessionCompressionHelper.decompressMessages(payload.compressedPayload)
                if (msgs.isNotEmpty()) {
                    messageDao?.insertMessages(msgs)
                }
                archivedPayloadDao?.deletePayload(sessionId)
            } catch (e: Exception) {
                android.util.Log.e("SessionRepo", "Decompression failed for $sessionId: ${e.message}", e)
            }
        }
        sessionDao.setArchived(sessionId, false, System.currentTimeMillis())
    }

    override suspend fun deleteSession(sessionId: String) {
        sessionDao.deleteSession(sessionId)
        messageDao?.deleteMessagesForSession(sessionId)
        archivedPayloadDao?.deletePayload(sessionId)
        if (settingsManager?.getLastActiveSessionId() == sessionId) {
            settingsManager.setLastActiveSessionId(null)
        }
        try {
            restClient.deleteSession(sessionId)
        } catch (e: Exception) {
            // Safe ignore remote errors
        }
    }

    override suspend fun deleteSessionsBatch(sessionIds: List<String>) {
        if (sessionIds.isEmpty()) return
        sessionDao.deleteSessions(sessionIds)
        messageDao?.deleteMessagesForSessions(sessionIds)
        archivedPayloadDao?.deletePayloads(sessionIds)
        if (settingsManager?.getLastActiveSessionId() in sessionIds) {
            settingsManager?.setLastActiveSessionId(null)
        }
        sessionIds.forEach { sid ->
            try {
                restClient.deleteSession(sid)
            } catch (_: Exception) {
                // Safe ignore remote errors
            }
        }
        checkpointDatabase()
    }

    override suspend fun archiveSessionsBatch(sessionIds: List<String>) {
        sessionIds.forEach { id ->
            archiveSessionWithCompression(id)
        }
        checkpointDatabase()
    }

    override suspend fun unarchiveSessionsBatch(sessionIds: List<String>) {
        sessionIds.forEach { id ->
            unarchiveSessionWithDecompression(id)
        }
    }

    override suspend fun updateHermesSessionId(sessionId: String, hermesSessionId: String?) {
        sessionDao.updateHermesSessionId(sessionId, hermesSessionId)
    }

    override fun getCompressionSavingsFlow(): Flow<Pair<Long, Long>> {
        val origFlow = archivedPayloadDao?.getTotalOriginalBytes() ?: flowOf(0L)
        val compFlow = archivedPayloadDao?.getTotalCompressedBytes() ?: flowOf(0L)
        return combine(origFlow, compFlow) { orig, comp ->
            Pair(orig ?: 0L, comp ?: 0L)
        }
    }
}
