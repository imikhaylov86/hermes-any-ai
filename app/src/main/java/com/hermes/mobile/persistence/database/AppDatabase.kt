package com.hermes.mobile.persistence.database

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
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
                    // G16 (полевой тест 30.10): старые билды писали base64-dataUrl в
                    // attachmentsJson → SQLiteBlobTooBigException убивал Flow сообщений.
                    // Чистка чисто SQL-ом (строка не помещается в CursorWindow даже при
                    // чтении — поэтому без SELECT полного значения: substr-замена на месте).
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            try {
                                // Пересобираем attachmentsJson без dataUrl прямо в SQL:
                                // до первого '"' после "dataUrl":" — не выйдет регексом в SQL,
                                // поэтому режем по маркеру: находим строки с data: и заменяем
                                // значение на null посимвольной перезаписью через substr.
                                val ids = db.query("SELECT id FROM messages WHERE attachmentsJson LIKE '%data:%'").use { c ->
                                    val list = mutableListOf<String>()
                                    while (c.moveToNext()) list.add(c.getString(0))
                                    list
                                }
                                if (ids.isNotEmpty()) {
                                    for (id in ids) {
                                        // Полное значение читаем порционно через SQL-функции:
                                        // длина + substring по кускам вне CursorWindow.
                                        val len = db.query("SELECT length(attachmentsJson) FROM messages WHERE id=?", arrayOf(id)).use { c ->
                                            if (c.moveToFirst()) c.getInt(0) else 0
                                        }
                                        if (len <= 0) continue
                                        val marker = "\"dataUrl\":\""
                                        // SQL-ом заменяем каждое "dataUrl":"<...>" на "dataUrl":null
                                        // (без чтения BLOB в Java — чистый UPDATE с SQL-функциями):
                                        db.execSQL(
                                            "UPDATE messages SET attachmentsJson = " +
                                                "replace(attachmentsJson, substr(attachmentsJson, instr(attachmentsJson, '\"dataUrl\":\"'), " +
                                                "instr(substr(attachmentsJson, instr(attachmentsJson, '\"dataUrl\":\"') + 11, length(attachmentsJson)), '\"') + 11), '\"dataUrl\":null') " +
                                                "WHERE id=?",
                                            arrayOf(id)
                                        )
                                        // Одного вложения с dataUrl в строке достаточно
                                        // для краша; цикл повторяет при повторном LIKE-попадании.
                                    }
                                    // Повторная проверка: если dataUrl ещё остались —
                                    // многократно применяем replace (несколько вложений в строке).
                                    var remaining = db.query("SELECT COUNT(*) FROM messages WHERE attachmentsJson LIKE '%\"dataUrl\":\"%'").use { c ->
                                        if (c.moveToFirst()) c.getInt(0) else 0
                                    }
                                    var guard = 0
                                    while (remaining > 0 && guard < 20) {
                                        guard++
                                        db.query("SELECT id FROM messages WHERE attachmentsJson LIKE '%\"dataUrl\":\"%' LIMIT 1").use { c ->
                                            if (c.moveToFirst()) {
                                                val id = c.getString(0)
                                                db.execSQL(
                                                    "UPDATE messages SET attachmentsJson = " +
                                                        "replace(attachmentsJson, substr(attachmentsJson, instr(attachmentsJson, '\"dataUrl\":\"'), " +
                                                        "instr(substr(attachmentsJson, instr(attachmentsJson, '\"dataUrl\":\"') + 11, length(attachmentsJson)), '\"') + 11), '\"dataUrl\":null') " +
                                                        "WHERE id=?",
                                                    arrayOf(id)
                                                )
                                            }
                                        }
                                        remaining = db.query("SELECT COUNT(*) FROM messages WHERE attachmentsJson LIKE '%\"dataUrl\":\"%'").use { c ->
                                            if (c.moveToFirst()) c.getInt(0) else 0
                                        }
                                    }
                                    android.util.Log.i("AppDatabase", "G16 sanitizer cleaned ${ids.size} attachment rows (guard=$guard)")
                                }
                            } catch (e: Exception) {
                                android.util.Log.w("AppDatabase", "G16 sanitizer failed: ${e.message}")
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
