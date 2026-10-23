package com.hermes.mobile.domain.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.hermes.mobile.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class UpdateManager(
    private val context: Context,
    private val repoOwner: String = "imikhaylov86",
    private val repoName: String = "hermes-any-ai"
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Проверяет наличие нового релиза на GitHub.
     * Возвращает [UpdateInfo], если доступна новая версия, либо null, если установлена последняя.
     */
    suspend fun checkForUpdates(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "HermesMobile-${BuildConfig.VERSION_NAME}")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    // Релизов пока не создано в репозитории
                    return@withContext Result.success(null)
                }
                return@withContext Result.failure(
                    IllegalStateException("GitHub API error: HTTP ${response.code} ${response.message}")
                )
            }

            val body = response.body?.string().orEmpty()
            if (body.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Пустой ответ от GitHub API"))
            }

            val release = json.decodeFromString<GitHubRelease>(body)
            val remoteVersion = release.tagName.removePrefix("v").trim()
            val currentVersion = BuildConfig.VERSION_NAME.removePrefix("v").trim()

            if (isNewerVersion(remoteVersion, currentVersion)) {
                // Ищем APK в ассетах релиза (предпочтительно релизный APK)
                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") && !it.name.contains("debug", ignoreCase = true) }
                    ?: release.assets.firstOrNull { it.name.endsWith(".apk") }

                if (apkAsset == null) {
                    return@withContext Result.failure(
                        IllegalStateException("В релизе ${release.tagName} не найден APK-файл.")
                    )
                }

                val updateInfo = UpdateInfo(
                    tagName = release.tagName,
                    versionName = remoteVersion,
                    title = release.name ?: release.tagName,
                    changelog = release.body.orEmpty(),
                    asset = apkAsset
                )
                Result.success(updateInfo)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Потоково скачивает APK-файл в изолированный кэш приложения с отслеживанием прогресса (0..100%).
     */
    suspend fun downloadApk(
        asset: ReleaseAsset,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) {
                updatesDir.mkdirs()
            } else {
                // Очищаем старые скачанные APK
                updatesDir.listFiles()?.forEach { it.delete() }
            }

            val apkFile = File(updatesDir, asset.name)

            val request = Request.Builder()
                .url(asset.browserDownloadUrl)
                .header("User-Agent", "HermesMobile-${BuildConfig.VERSION_NAME}")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("Ошибка скачивания: HTTP ${response.code} ${response.message}")
                )
            }

            val body = response.body ?: return@withContext Result.failure(
                IllegalStateException("Пустое тело ответа при скачивании APK")
            )

            val totalBytes = if (body.contentLength() > 0) body.contentLength() else asset.size
            var downloadedBytes = 0L

            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var read: Int
                    var lastReportedPercent = -1

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        if (totalBytes > 0) {
                            val percent = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                    output.flush()
                }
            }

            if (!apkFile.exists() || apkFile.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("Файл APK не был сохранен или пуст"))
            }

            Result.success(apkFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Проверяет, дано ли разрешение на установку неизвестных пакетов (Android 8.0+).
     */
    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Открывает системный экран настроек «Разрешить установку из этого источника».
     */
    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Запускает системный пакетный установщик Android для бесшовного обновления поверх существующего клиента.
     */
    fun installApk(apkFile: File): Result<Unit> {
        return try {
            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Сравнение версий по семантическому версионированию (SemVer).
     * Возвращает true, если remote > current.
     */
    fun isNewerVersion(remote: String, current: String): Boolean {
        val cleanRemote = remote.split("-", "+")[0]
        val cleanCurrent = current.split("-", "+")[0]

        val remoteParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
