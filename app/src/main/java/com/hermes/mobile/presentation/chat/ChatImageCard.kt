package com.hermes.mobile.presentation.chat

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.hermes.mobile.HermesApp
import com.hermes.mobile.presentation.i18n.strings
import com.hermes.mobile.presentation.theme.AppDimensions
import com.hermes.mobile.presentation.theme.PrimaryIndigo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

sealed class ContentBlock {
    data class Text(val text: String) : ContentBlock()
    data class Code(val code: String, val language: String) : ContentBlock()
    data class Image(val source: String, val label: String?) : ContentBlock()
}

private val COMBINED_IMAGE_REGEX = Regex(
    """(!?\[([^\]]*?)\]\(\s*([^\s)]+)\s*\))|(?<=^|[\s\n])((?:https?://|/(?:data|sdcard|storage)/|[a-zA-Z0-9_\-\./]*hermes-media/)[^\s\n()]+\.(?:png|jpg|jpeg|webp|gif|bmp|svg))(?=$|[\s\n])""",
    RegexOption.IGNORE_CASE
)

fun isImageTarget(path: String): Boolean {
    val clean = path.trim().substringBefore("?").lowercase()
    val extensions = listOf(".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp", ".svg")
    return extensions.any { clean.endsWith(it) } ||
           clean.contains("hermes-media") ||
           clean.startsWith("data:image/") ||
           clean.contains("/api/files/download")
}

fun parseMessageContent(content: String): List<ContentBlock> {
    val result = mutableListOf<ContentBlock>()
    val codeParts = content.split("```")

    codeParts.forEachIndexed { index, part ->
        if (index % 2 == 1) {
            val lines = part.trim().lines()
            val language = lines.firstOrNull() ?: ""
            val codeBody = if (lines.size > 1) lines.drop(1).joinToString("\n") else part
            result.add(ContentBlock.Code(code = codeBody, language = language))
        } else {
            if (part.isNotBlank()) {
                result.addAll(parseImagesFromText(part))
            }
        }
    }
    return result
}

private fun parseImagesFromText(text: String): List<ContentBlock> {
    val blocks = mutableListOf<ContentBlock>()
    var currentIndex = 0
    val matches = COMBINED_IMAGE_REGEX.findAll(text)

    for (match in matches) {
        val markdownGroup = match.groups[1]
        val standaloneGroup = match.groups[4]

        val (target, label) = if (markdownGroup != null) {
            val lbl = match.groups[2]?.value?.trim()
            val tgt = match.groups[3]?.value?.trim() ?: ""
            if (isImageTarget(tgt)) {
                tgt to (if (lbl.isNullOrBlank()) null else lbl)
            } else {
                null to null
            }
        } else if (standaloneGroup != null) {
            val tgt = standaloneGroup.value.trim()
            tgt to null
        } else {
            null to null
        }

        if (target != null) {
            val textBefore = text.substring(currentIndex, match.range.first)
            if (textBefore.isNotBlank()) {
                blocks.add(ContentBlock.Text(textBefore.trim()))
            }
            blocks.add(ContentBlock.Image(source = target, label = label))
            currentIndex = match.range.last + 1
        }
    }

    if (currentIndex < text.length) {
        val remaining = text.substring(currentIndex)
        if (remaining.isNotBlank()) {
            blocks.add(ContentBlock.Text(remaining.trim()))
        }
    }

    if (blocks.isEmpty() && text.isNotBlank()) {
        blocks.add(ContentBlock.Text(text.trim()))
    }

    return blocks
}

fun resolveImageModel(source: String): Any {
    val cleanSource = source.trim().trim('(', ')')

    // 1. Data URL
    if (cleanSource.startsWith("data:image/")) {
        try {
            val base64Data = cleanSource.substringAfter("base64,")
            val decoded = Base64.decode(base64Data, Base64.DEFAULT)
            return android.graphics.BitmapFactory.decodeByteArray(decoded, 0, decoded.size) ?: cleanSource
        } catch (e: Exception) {
            return cleanSource
        }
    }

    // 2. Direct HTTP/HTTPS URL
    if (cleanSource.startsWith("http://") || cleanSource.startsWith("https://")) {
        if (cleanSource.contains("/api/files/download") && !cleanSource.contains("token=")) {
            val token = try { HermesApp.instance.settingsManager.getSessionToken() } catch (e: Exception) { "hermes_mobile_local" }
            val separator = if (cleanSource.contains("?")) "&" else "?"
            return "$cleanSource${separator}token=$token"
        }
        return cleanSource
    }

    // 3. Accessible shared storage (/sdcard or /storage/emulated/0)
    if (cleanSource.startsWith("/sdcard/") || cleanSource.startsWith("/storage/")) {
        val file = File(cleanSource)
        if (file.exists() && file.canRead()) {
            return file
        }
    }

    // 4. Hermes Termux-хранилище или относительный путь (e.g. /data/data/com.termux/files/home/... or hermes-media/...)
    // Файлы демона недоступны напрямую из песочницы приложения — качаем через REST /files/download
    val restUrl = try { HermesApp.instance.settingsManager.getRestUrl() } catch (e: Exception) { "http://127.0.0.1:8000/api" }
    val token = try { HermesApp.instance.settingsManager.getSessionToken() } catch (e: Exception) { "hermes_mobile_local" }
    val encodedPath = URLEncoder.encode(cleanSource, "UTF-8")
    return "$restUrl/files/download?path=$encodedPath&token=$token"
}

suspend fun saveImageToGallery(context: Context, model: Any, displayName: String): Boolean = withContext(Dispatchers.IO) {
    try {
        Log.i("HermesImage", "Starting saveImageToGallery for $displayName, model=$model")
        val bytes: ByteArray = when (model) {
            is ByteArray -> model
            is Bitmap -> {
                val bos = ByteArrayOutputStream()
                model.compress(Bitmap.CompressFormat.PNG, 100, bos)
                bos.toByteArray()
            }
            is File -> {
                if (model.exists() && model.canRead()) model.readBytes() else return@withContext false
            }
            is String -> {
                if (model.startsWith("http://") || model.startsWith("https://")) {
                    val token = try { HermesApp.instance.settingsManager.getSessionToken() } catch (e: Exception) { "hermes_mobile_local" }
                    val client = OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build()
                    val req = Request.Builder()
                        .url(model)
                        .header("X-Hermes-Session-Token", token)
                        .header("Authorization", "Bearer $token")
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            Log.e("HermesImage", "HTTP download failed with code ${resp.code}")
                            return@withContext false
                        }
                        resp.body?.bytes() ?: return@withContext false
                    }
                } else if (model.startsWith("/sdcard/") || model.startsWith("/storage/")) {
                    val f = File(model)
                    if (f.exists() && f.canRead()) f.readBytes() else return@withContext false
                } else {
                    Log.e("HermesImage", "Unsupported model string: $model")
                    return@withContext false
                }
            }
            else -> {
                Log.e("HermesImage", "Unsupported model type: ${model.javaClass}")
                return@withContext false
            }
        }

        val cleanName = if (displayName.contains(".")) {
            displayName
        } else {
            "$displayName.png"
        }

        val mimeType = when {
            cleanName.endsWith(".jpg", true) || cleanName.endsWith(".jpeg", true) -> "image/jpeg"
            cleanName.endsWith(".webp", true) -> "image/webp"
            cleanName.endsWith(".gif", true) -> "image/gif"
            else -> "image/png"
        }

        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, cleanName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Hermes/")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        var uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        }

        if (uri == null) {
            Log.e("HermesImage", "MediaStore insert returned null URI")
            return@withContext false
        }

        try {
            resolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
                out.flush()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            Log.i("HermesImage", "Successfully saved image to $uri ($cleanName, ${bytes.size} bytes)")
            true
        } catch (e: Exception) {
            Log.e("HermesImage", "Failed to write bytes to URI", e)
            resolver.delete(uri, null, null)
            false
        }
    } catch (e: Exception) {
        Log.e("HermesImage", "Error in saveImageToGallery", e)
        false
    }
}

@Composable
fun ChatImageCard(
    source: String,
    label: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showFullscreen by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val resolvedModel = remember(source) { resolveImageModel(source) }
    val appStrings = strings

    val displayName = remember(source, label) {
        val fileName = source.trim().trim('(', ')').substringAfterLast("/").substringBefore("?")
        val cleanLabel = label?.trim()
        val genericLabels = listOf("скачать", "скачать фото", "фото", "image", "download", "link", "картинка", "файл")
        if (!cleanLabel.isNullOrBlank() && !cleanLabel.startsWith("http") && !cleanLabel.startsWith("/") && genericLabels.none { cleanLabel.equals(it, ignoreCase = true) }) {
            cleanLabel
        } else if (fileName.isNotBlank()) {
            fileName
        } else {
            "image_${System.currentTimeMillis()}.png"
        }
    }

    val onSaveAction: () -> Unit = {
        if (!isSaving) {
            isSaving = true
            scope.launch {
                val success = saveImageToGallery(context, resolvedModel, displayName)
                isSaving = false
                Toast.makeText(
                    context,
                    if (success) appStrings.imageSavedGallerySuccess else appStrings.imageSaveFailed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(AppDimensions.ShapeField)
            .background(Color.Black.copy(alpha = 0.25f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), AppDimensions.ShapeField)
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(resolvedModel)
                .crossfade(true)
                .build(),
            contentDescription = displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp, max = 260.dp)
                .clickable { showFullscreen = true },
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = PrimaryIndigo,
                        strokeWidth = 2.dp
                    )
                }
            },
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.BrokenImage,
                            contentDescription = strings.error,
                            tint = Color.Gray,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = strings.imageLoadFailed,
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        )

        // Нижняя строка с названием и кнопками
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = displayName,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onSaveAction,
                    enabled = !isSaving,
                    modifier = Modifier.size(36.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = strings.imageSaveGallery,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = { showFullscreen = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = strings.imageFullScreen,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }

    if (showFullscreen) {
        ImageFullscreenDialog(
            model = resolvedModel,
            title = displayName,
            onDismiss = { showFullscreen = false },
            onSave = onSaveAction,
            isSaving = isSaving
        )
    }
}

@Composable
fun ImageFullscreenDialog(
    model: Any,
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    isSaving: Boolean
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f))
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Верхняя панель действий
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = strings.close,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                )

                IconButton(onClick = onSave, enabled = !isSaving, modifier = Modifier.size(44.dp)) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = strings.save,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Полноэкранное изображение в центре
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(model)
                        .crossfade(true)
                        .build(),
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = PrimaryIndigo)
                        }
                    },
                    error = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = strings.imageLoadFailed, color = Color.Gray)
                        }
                    }
                )
            }

            // Нижняя удобная кнопка "Сохранить в Галерею"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = onSave,
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                    shape = AppDimensions.ShapeButton,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = strings.imageSavingProgress, color = Color.White, fontSize = 14.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = strings.imageSaveGallery, color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
