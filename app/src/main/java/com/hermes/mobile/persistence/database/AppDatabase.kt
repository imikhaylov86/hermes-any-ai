package com.hermes.mobile.persistence.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestampEpochMs ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)

    @Query("UPDATE messages SET content = :content, status = :status WHERE id = :messageId")
    suspend fun updateContentAndStatus(messageId: String, content: String, status: String)

    @Query("UPDATE messages SET status = 'COMPLETE' WHERE sessionId = :sessionId AND status = 'STREAMING'")
    suspend fun completeStreamingMessages(sessionId: String)

    @Query("UPDATE messages SET status = 'COMPLETE' WHERE status = 'STREAMING'")
    suspend fun completeAllStreamingMessages()

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId AND role = 'USER' ORDER BY timestampEpochMs ASC LIMIT 1")
    suspend fun getFirstUserMessage(sessionId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId AND role = 'USER' ORDER BY timestampEpochMs DESC LIMIT 1")
    suspend fun getLastUserMessage(sessionId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestampEpochMs ASC")
    suspend fun getMessagesListForSession(sessionId: String): List<MessageEntity>

    @Query("SELECT * FROM messages ORDER BY timestampEpochMs ASC")
    suspend fun getAllMessagesList(): List<MessageEntity>

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY isPinned DESC, updatedAtEpochMs DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY isPinned DESC, updatedAtEpochMs DESC")
    suspend fun getAllSessionsList(): List<SessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<SessionEntity>)

    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): SessionEntity?

    @Query("SELECT * FROM sessions ORDER BY isPinned DESC, updatedAtEpochMs DESC LIMIT 1")
    suspend fun getLatestSession(): SessionEntity?

    @Query("UPDATE sessions SET title = :newTitle, updatedAtEpochMs = :timestamp WHERE id = :sessionId")
    suspend fun updateTitle(sessionId: String, newTitle: String, timestamp: Long)

    @Query("UPDATE sessions SET title = :newTitle, lastMessagePreview = :preview, updatedAtEpochMs = :timestamp WHERE id = :sessionId")
    suspend fun updateTitleAndPreview(sessionId: String, newTitle: String, preview: String, timestamp: Long)

    @Query("SELECT * FROM sessions WHERE title = 'Новая беседа' OR title = '' OR title IS NULL")
    suspend fun getUnnamedSessions(): List<SessionEntity>

    @Query("UPDATE sessions SET isPinned = NOT isPinned WHERE id = :sessionId")
    suspend fun togglePin(sessionId: String)

    @Query("UPDATE sessions SET isArchived = NOT isArchived WHERE id = :sessionId")
    suspend fun toggleArchive(sessionId: String)

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)
}

val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE messages ADD COLUMN attachmentsJson TEXT DEFAULT NULL")
    }
}

val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE messages ADD COLUMN thinkingContent TEXT DEFAULT NULL")
    }
}

@Database(entities = [MessageEntity::class, SessionEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // G2 (docs/07 §3): деструктивный fallback запрещён — при изменении
                // версии схемы обязателен Migration(N, N+1), иначе Room упадёт с
                // IllegalStateException вместо молчаливого стирания истории чатов.
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hermes_mobile_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
