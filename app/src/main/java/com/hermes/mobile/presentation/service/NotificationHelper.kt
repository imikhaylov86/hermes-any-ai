package com.hermes.mobile.presentation.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hermes.mobile.R
import com.hermes.mobile.presentation.MainActivity

object NotificationHelper {

    const val CHANNEL_ID_COMPLETION = "hermes_chat_completion_channel"
    const val EXTRA_SESSION_ID = "EXTRA_SESSION_ID"
    private const val NOTIFICATION_ID_BASE = 2000

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_COMPLETION,
                "Ответы ассистента",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Уведомления о завершении генерации ответа агентом"
                setShowBadge(true)
                enableVibration(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun notifyResponseCompleted(
        context: Context,
        sessionId: String,
        sessionTitle: String?,
        content: String
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    android.util.Log.d("NotificationHelper", "POST_NOTIFICATIONS not granted, skipping notification")
                    return
                }
            }

            createNotificationChannels(context)

            val title = if (!sessionTitle.isNullOrBlank()) sessionTitle else "Hermes: Ответ готов"
            val cleanSnippet = cleanMarkdownSnippet(content)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_SESSION_ID, sessionId)
            }

            val requestCode = (sessionId.hashCode() and 0xFFFF)
            val pendingIntent = PendingIntent.getActivity(
                context,
                requestCode,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val largeIcon = try {
                BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
            } catch (_: Exception) {
                null
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID_COMPLETION)
                .setSmallIcon(R.drawable.ic_stat_hermes)
                .setContentTitle(title)
                .setContentText(cleanSnippet)
                .setStyle(NotificationCompat.BigTextStyle().bigText(cleanSnippet))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setColor(0xFF6366F1.toInt())

            if (largeIcon != null) {
                builder.setLargeIcon(largeIcon)
            }

            val notificationManager = NotificationManagerCompat.from(context)
            val notificationId = NOTIFICATION_ID_BASE + (sessionId.hashCode() and 0x7FFF)
            notificationManager.notify(notificationId, builder.build())
        } catch (e: Exception) {
            android.util.Log.w("NotificationHelper", "Failed to post completion notification: ${e.message}")
        }
    }

    internal fun cleanMarkdownSnippet(text: String): String {
        val hasMedia = text.contains("MEDIA:", ignoreCase = true)
        var clean = text
            // Strip think tags and contents
            .replace(Regex("<think>[\\s\\S]*?(</think>|$)", RegexOption.IGNORE_CASE), "")
            // Strip media tags
            .replace(Regex("MEDIA:[^\\s\\n]+", RegexOption.IGNORE_CASE), "")
            // Strip code fences
            .replace(Regex("```[a-zA-Z0-9_-]*\n?"), "")
            // Strip markdown formatting symbols
            .replace(Regex("[*_~`#]"), "")
            // Strip blockquotes and bullet points
            .replace(Regex("(?m)^[\\s]*[>*-]\\s+"), "")
            // Replace multiple linebreaks with space
            .replace(Regex("\\s+"), " ")
            .trim()

        if (clean.length > 250) {
            clean = clean.take(247).trimEnd() + "..."
        }

        return when {
            clean.isNotBlank() -> clean
            hasMedia -> "Изображение готово"
            else -> "Ответ сгенерирован"
        }
    }
}
