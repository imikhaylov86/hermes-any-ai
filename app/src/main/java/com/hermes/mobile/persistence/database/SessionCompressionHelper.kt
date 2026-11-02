package com.hermes.mobile.persistence.database

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object SessionCompressionHelper {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Сжимает список сообщений сессии в GZIP-байтмассив.
     * Возвращает пару: сжатые байты и оригинальный размер в байтах.
     */
    fun compressMessages(messages: List<MessageEntity>): Pair<ByteArray, Long> {
        val jsonStr = json.encodeToString(messages)
        val originalBytes = jsonStr.toByteArray(Charsets.UTF_8)
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { gzip ->
            gzip.write(originalBytes)
        }
        val compressed = baos.toByteArray()
        return Pair(compressed, originalBytes.size.toLong())
    }

    /**
     * Распаковывает GZIP-байтмассив обратно в список MessageEntity.
     */
    fun decompressMessages(compressedPayload: ByteArray): List<MessageEntity> {
        if (compressedPayload.isEmpty()) return emptyList()
        return try {
            val bais = ByteArrayInputStream(compressedPayload)
            val baos = ByteArrayOutputStream()
            GZIPInputStream(bais).use { gzip ->
                gzip.copyTo(baos)
            }
            val jsonStr = baos.toString(Charsets.UTF_8.name())
            json.decodeFromString<List<MessageEntity>>(jsonStr)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
