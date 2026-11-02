package com.hermes.mobile.presentation.chat

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hermes.mobile.HermesApp
import com.hermes.mobile.presentation.i18n.strings
import com.hermes.mobile.presentation.theme.AppDimensions
import com.hermes.mobile.presentation.theme.PrimaryIndigo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "svg", "heic", "heif")
val TEXT_PREVIEW_EXTENSIONS = setOf(
    "md", "markdown", "txt", "log", "json", "csv", "tsv", "xml", "yaml", "yml",
    "html", "css", "js", "ts", "py", "kt", "java", "c", "cpp", "h", "sh", "bat", "ps1", "sql"
)
val ARCHIVE_EXTENSIONS = setOf("zip", "tar", "gz", "7z", "rar", "apk", "bz2", "xz")
val AUDIO_EXTENSIONS = setOf("mp3", "wav", "ogg", "m4a", "flac", "aac")
val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "mov", "webm", "avi")
val DOCUMENT_EXTENSIONS = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "rtf", "odt")

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}

fun extractFilenameAndExtension(source: String, label: String? = null): Pair<String, String> {
    val cleanLabel = label?.trim()?.trim('`', '"', '\'')
    // 1. Если label содержит валидное расширение (например, "отчет.md")
    if (!cleanLabel.isNullOrBlank() && cleanLabel.contains(".") &&
        !cleanLabel.startsWith("http", ignoreCase = true) && !cleanLabel.startsWith("/")
    ) {
        val ext = cleanLabel.substringAfterLast(".").lowercase()
        if (ext.length in 1..8 && ext.all { it.isLetterOrDigit() }) {
            return cleanLabel to ext
        }
    }

    // 2. Параметр path= или file= в URL
    if (source.contains("path=") || source.contains("file=")) {
        val rawParam = if (source.contains("path=")) {
            source.substringAfter("path=").substringBefore("&")
        } else {
            source.substringAfter("file=").substringBefore("&")
        }
        val decoded = try { URLDecoder.decode(rawParam, "UTF-8") } catch (e: Exception) { rawParam }
        val name = decoded.trim().substringAfterLast("/").substringAfterLast("\\")
        if (name.contains(".")) {
            val ext = name.substringAfterLast(".").lowercase()
            return name to ext
        } else if (name.isNotBlank()) {
            return name to ""
        }
    }

    // 3. Извлечение имени и расширения из источника
    val cleanSource = source.trim().trim('(', ')').removePrefix("MEDIA:").trim().substringBefore("?")
    val name = cleanSource.substringAfterLast("/").substringAfterLast("\\")
    val ext = if (name.contains(".")) name.substringAfterLast(".").lowercase() else ""
    val finalName = when {
        name.isNotBlank() && name != "download" -> name
        !cleanLabel.isNullOrBlank() && !cleanLabel.startsWith("http", ignoreCase = true) -> cleanLabel
        ext.isNotBlank() -> "file.$ext"
        else -> "file"
    }
    return finalName to ext
}

fun isImageTarget(path: String, label: String? = null): Boolean {
    val clean = path.trim().substringBefore("?").lowercase()
    if (clean.startsWith("data:image/")) return true
    if (IMAGE_EXTENSIONS.any { clean.endsWith(".$it") }) return true

    val labelExt = label?.trim()?.substringAfterLast(".", "")?.lowercase() ?: ""
    if (labelExt in IMAGE_EXTENSIONS) return true

    if (path.contains("path=") || path.contains("file=")) {
        val rawParam = if (path.contains("path=")) {
            path.substringAfter("path=").substringBefore("&").lowercase()
        } else {
            path.substringAfter("file=").substringBefore("&").lowercase()
        }
        val decoded = try { URLDecoder.decode(rawParam, "UTF-8") } catch (e: Exception) { rawParam }
        if (IMAGE_EXTENSIONS.any { decoded.endsWith(".$it") }) return true
    }

    if (clean.contains("hermes-media")) {
        val (_, ext) = extractFilenameAndExtension(path, label)
        if (ext !in TEXT_PREVIEW_EXTENSIONS && ext !in ARCHIVE_EXTENSIONS && ext !in DOCUMENT_EXTENSIONS) {
            return true
        }
    }

    return false
}

fun isFileTarget(path: String, label: String? = null): Boolean {
    if (isImageTarget(path, label)) return false

    val (_, ext) = extractFilenameAndExtension(path, label)
    if (ext in TEXT_PREVIEW_EXTENSIONS || ext in ARCHIVE_EXTENSIONS ||
        ext in AUDIO_EXTENSIONS || ext in VIDEO_EXTENSIONS || ext in DOCUMENT_EXTENSIONS
    ) {
        return true
    }

    if (path.contains("/api/files/download")) return true
    if (path.contains(".hermes/attachments") || path.contains("/attachments/")) return true
    if (path.startsWith("MEDIA:", ignoreCase = true)) return true

    if (ext.isNotBlank() && ext.length in 1..8 && ext.all { it.isLetterOrDigit() }) {
        return true
    }

    return false
}

fun resolveFileUrl(source: String): String {
    val cleanSource = source.trim().trim('(', ')').removePrefix("MEDIA:").trim()
    val restUrl = try { HermesApp.instance.settingsManager.getRestUrl() } catch (e: Exception) { "http://127.0.0.1:8000/api" }
    val token = try { HermesApp.instance.settingsManager.getSessionToken() } catch (e: Exception) { "hermes_mobile_local" }
    val baseUrl = restUrl.substringBefore("/api")

    if (cleanSource.startsWith("file://")) {
        val path = cleanSource.removePrefix("file://")
        if (File(path).canRead()) {
            return cleanSource
        }
        val encoded = URLEncoder.encode(path, "UTF-8")
        return "$baseUrl/api/files/download?path=$encoded&token=$token"
    }

    if (cleanSource.startsWith("content://")) {
        return cleanSource
    }

    if (cleanSource.startsWith("http://") || cleanSource.startsWith("https://")) {
        return if (!cleanSource.contains("token=") && cleanSource.contains("/api/files/download")) {
            val sep = if (cleanSource.contains("?")) "&" else "?"
            "$cleanSource${sep}token=$token"
        } else {
            cleanSource
        }
    }

    if (cleanSource.startsWith("/api/files/download") || cleanSource.startsWith("api/files/download")) {
        val normalized = if (cleanSource.startsWith("/")) cleanSource else "/$cleanSource"
        val sep = if (normalized.contains("?")) "&" else "?"
        return if (!normalized.contains("token=")) {
            "$baseUrl$normalized${sep}token=$token"
        } else {
            "$baseUrl$normalized"
        }
    }

    if (cleanSource.startsWith("/sdcard/") || cleanSource.startsWith("/storage/")) {
        val f = File(cleanSource)
        if (f.exists() && f.canRead()) {
            return cleanSource
        }
    }

    val encoded = URLEncoder.encode(cleanSource, "UTF-8")
    return "$baseUrl/api/files/download?path=$encoded&token=$token"
}

suspend fun fetchFileBytes(
    context: Context,
    source: String,
    fallbackFilename: String? = null
): ByteArray? = withContext(Dispatchers.IO) {
    try {
        val clean = source.trim().trim('(', ')').removePrefix("MEDIA:").trim()
        val baseName = fallbackFilename?.trim()?.ifBlank { null }
            ?: extractFilenameAndExtension(clean).first.takeIf { it.isNotBlank() && it != "file" && it != "download" }

        // 1. Android content:// URI
        if (clean.startsWith("content://")) {
            try {
                val uri = Uri.parse(clean)
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    return@withContext bytes
                }
            } catch (e: Exception) {
                Log.w("HermesFile", "Failed reading content URI $clean, will try server download", e)
            }
        }

        // 2. Android file:// URI or local file path
        if (clean.startsWith("file://")) {
            val path = clean.removePrefix("file://")
            val f = File(path)
            if (f.exists() && f.canRead()) {
                try {
                    return@withContext f.readBytes()
                } catch (e: Exception) {
                    Log.w("HermesFile", "Failed reading local file $path", e)
                }
            }
        } else if (clean.startsWith("/sdcard/") || clean.startsWith("/storage/") || (clean.startsWith("/") && File(clean).canRead())) {
            val f = File(clean)
            if (f.exists() && f.canRead()) {
                try {
                    return@withContext f.readBytes()
                } catch (e: Exception) {
                    Log.w("HermesFile", "Failed reading local file $clean", e)
                }
            }
        }

        // 3. Try downloading via DashboardRestClient with candidate paths
        val restClient = try { HermesApp.instance.restClient } catch (e: Exception) { null }
        if (restClient != null) {
            val candidatePaths = mutableListOf<String>()
            val rawPath = if (clean.startsWith("file://")) clean.removePrefix("file://") else clean

            // If clean is a download URL with path= query param, extract it
            if (clean.contains("/api/files/download") && clean.contains("path=")) {
                val extractedPath = try {
                    URLDecoder.decode(clean.substringAfter("path=").substringBefore("&"), "UTF-8")
                } catch (e: Exception) {
                    clean.substringAfter("path=").substringBefore("&")
                }
                candidatePaths.add(extractedPath)
            } else if (!clean.startsWith("http://") && !clean.startsWith("https://") && !clean.startsWith("content://")) {
                candidatePaths.add(rawPath)
            }

            if (baseName != null) {
                candidatePaths.add(".hermes/attachments/$baseName")
                candidatePaths.add("attachments/$baseName")
                candidatePaths.add(baseName)
            }

            for (candidate in candidatePaths.distinct()) {
                val result = restClient.downloadFile(candidate)
                if (result.isSuccess) {
                    val bytes = result.getOrNull()
                    if (bytes != null && bytes.isNotEmpty()) {
                        Log.i("HermesFile", "Successfully fetched file (${bytes.size} bytes) via restClient for candidate '$candidate'")
                        return@withContext bytes
                    }
                }
            }
        }

        // 4. HTTP / HTTPS fallback
        val fullUrl = resolveFileUrl(clean)
        if (fullUrl.startsWith("http://") || fullUrl.startsWith("https://")) {
            val token = try { HermesApp.instance.settingsManager.getSessionToken() } catch (e: Exception) { "hermes_mobile_local" }
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder()
                .url(fullUrl)
                .header("X-Hermes-Session-Token", token)
                .header("Authorization", "Bearer $token")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        return@withContext bytes
                    }
                } else {
                    Log.e("HermesFile", "HTTP fetch failed: ${resp.code} for $fullUrl")
                }
            }
        }

        null
    } catch (e: Exception) {
        Log.e("HermesFile", "fetchFileBytes error for $source", e)
        null
    }
}

suspend fun saveFileToDownloads(context: Context, source: String, displayName: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val bytes = fetchFileBytes(context, source, displayName) ?: return@withContext false
        val cleanName = displayName.trim().ifBlank { "hermes_file_${System.currentTimeMillis()}" }
        val ext = cleanName.substringAfterLast(".", "").lowercase()

        val mimeType = when (ext) {
            "md", "markdown" -> "text/markdown"
            "txt", "log" -> "text/plain"
            "json" -> "application/json"
            "csv" -> "text/csv"
            "tsv" -> "text/tab-separated-values"
            "xml" -> "application/xml"
            "yaml", "yml" -> "text/yaml"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            "7z" -> "application/x-7z-compressed"
            "rar" -> "application/vnd.rar"
            "apk" -> "application/vnd.android.package-archive"
            "html", "htm" -> "text/html"
            "py" -> "text/x-python"
            "kt" -> "text/x-kotlin"
            "java" -> "text/x-java-source"
            "js", "ts" -> "application/javascript"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "mp4" -> "video/mp4"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            else -> "application/octet-stream"
        }

        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, cleanName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Hermes")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            var uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri == null) {
                contentValues.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            }
            if (uri == null) {
                Log.e("HermesFile", "MediaStore.Downloads returned null URI")
                return@withContext false
            }
            resolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
                out.flush()
            }
            try {
                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            } catch (e: Exception) {
                Log.w("HermesFile", "Failed to clear IS_PENDING on $uri", e)
            }
            Log.i("HermesFile", "Successfully saved file to Downloads: $uri ($cleanName, ${bytes.size} bytes)")
            true
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Hermes")
            if (!dir.exists()) dir.mkdirs()
            val dest = File(dir, cleanName)
            dest.writeBytes(bytes)
            Log.i("HermesFile", "Successfully saved file to ${dest.absolutePath}")
            true
        }
    } catch (e: Exception) {
        Log.e("HermesFile", "saveFileToDownloads failed for $displayName", e)
        false
    }
}

@Composable
fun ChatFileCard(
    source: String,
    filename: String,
    extension: String,
    sizeBytes: Long? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appStrings = strings
    var isDownloading by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }

    val isTextPreviewable = remember(extension) {
        extension.lowercase() in TEXT_PREVIEW_EXTENSIONS
    }

    val fileTypeDescription = remember(extension) {
        val ext = extension.lowercase()
        when {
            ext in setOf("md", "markdown") -> "Markdown"
            ext in TEXT_PREVIEW_EXTENSIONS -> appStrings.fileCode
            ext in ARCHIVE_EXTENSIONS -> appStrings.fileArchive
            ext in AUDIO_EXTENSIONS -> appStrings.fileAudio
            ext in VIDEO_EXTENSIONS -> appStrings.fileVideo
            ext == "pdf" -> "PDF"
            else -> appStrings.fileDoc
        }
    }

    val iconVector: ImageVector = remember(extension) {
        val ext = extension.lowercase()
        when {
            ext in setOf("md", "markdown", "txt", "log") -> Icons.Default.Description
            ext in TEXT_PREVIEW_EXTENSIONS -> Icons.Default.Code
            ext in ARCHIVE_EXTENSIONS -> Icons.Default.FolderZip
            ext in AUDIO_EXTENSIONS -> Icons.Default.Audiotrack
            ext in VIDEO_EXTENSIONS -> Icons.Default.VideoFile
            ext in DOCUMENT_EXTENSIONS -> Icons.AutoMirrored.Filled.Article
            else -> Icons.Default.AttachFile
        }
    }

    val iconTint: Color = remember(extension) {
        val ext = extension.lowercase()
        when {
            ext in setOf("md", "markdown") -> Color(0xFF60A5FA)
            ext in TEXT_PREVIEW_EXTENSIONS -> Color(0xFF34D399)
            ext in ARCHIVE_EXTENSIONS -> Color(0xFFFBBF24)
            ext in AUDIO_EXTENSIONS -> Color(0xFFA78BFA)
            ext in VIDEO_EXTENSIONS -> Color(0xFFF472B6)
            ext == "pdf" -> Color(0xFFF87171)
            else -> PrimaryIndigo
        }
    }

    val onDownloadAction: () -> Unit = {
        if (!isDownloading) {
            isDownloading = true
            scope.launch {
                val success = saveFileToDownloads(context, source, filename)
                isDownloading = false
                Toast.makeText(
                    context,
                    if (success) String.format(appStrings.fileSavedDownloads, filename) else appStrings.fileSaveFailed,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    Surface(
        shape = AppDimensions.ShapeField,
        color = Color.Black.copy(alpha = 0.28f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Иконка формата файла в контейнере
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconTint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = fileTypeDescription,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Имя файла и бейдж формата/размера
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp)
            ) {
                Text(
                    text = filename,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (extension.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = iconTint.copy(alpha = 0.25f),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = extension.uppercase(),
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    val detailsText = if (sizeBytes != null && sizeBytes > 0) {
                        "$fileTypeDescription • ${formatFileSize(sizeBytes)}"
                    } else {
                        fileTypeDescription
                    }
                    Text(
                        text = detailsText,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Кнопки: Предпросмотр (для текстовых) и Скачать
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isTextPreviewable) {
                    IconButton(
                        onClick = { showPreview = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = appStrings.filePreview,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onDownloadAction,
                    enabled = !isDownloading,
                    modifier = Modifier.size(36.dp)
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = PrimaryIndigo,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = appStrings.fileDownload,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    if (showPreview) {
        TextFilePreviewDialog(
            source = source,
            filename = filename,
            onDismiss = { showPreview = false },
            onDownload = onDownloadAction,
            isDownloading = isDownloading
        )
    }
}

@Composable
fun TextFilePreviewDialog(
    source: String,
    filename: String,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    isDownloading: Boolean
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val appStrings = strings

    var isLoading by remember { mutableStateOf(true) }
    var textContent by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(source) {
        isLoading = true
        errorMessage = null
        val bytes = fetchFileBytes(context, source, filename)
        if (bytes != null) {
            textContent = try {
                String(bytes, Charsets.UTF_8)
            } catch (e: Exception) {
                String(bytes)
            }
        } else {
            errorMessage = appStrings.filePreviewError
        }
        isLoading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF12141A))
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Верхняя панель диалога
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = appStrings.close,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Text(
                    text = filename,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!textContent.isNullOrBlank()) {
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(textContent ?: ""))
                                Toast.makeText(context, appStrings.fileCopied, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = appStrings.copy,
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDownload,
                        enabled = !isDownloading,
                        modifier = Modifier.size(40.dp)
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = PrimaryIndigo,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = appStrings.fileDownload,
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Поле текста
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = PrimaryIndigo)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = appStrings.fileLoadingPreview,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                    errorMessage != null -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = errorMessage ?: appStrings.error,
                                color = Color(0xFFF87171),
                                fontSize = 13.sp
                            )
                        }
                    }
                    textContent != null -> {
                        SelectionContainer {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = textContent ?: "",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
