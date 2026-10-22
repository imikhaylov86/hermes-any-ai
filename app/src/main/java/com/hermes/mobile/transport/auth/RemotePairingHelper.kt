package com.hermes.mobile.transport.auth

import android.app.Activity
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class RemotePairingConfig(
    val serverUrl: String,
    val token: String? = null,
    val name: String = "Удалённый сервер"
)

object RemotePairingHelper {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Парсит строку из QR-кода или буфера обмена.
     * Поддерживает форматы:
     * 1. hermes://connect?url=wss://myserver.com/api/ws&token=xyz
     * 2. JSON: {"url":"wss://...","token":"...","name":"Homelab"}
     * 3. Прямой URL: ws://, wss://, http://, https://
     */
    fun parsePairingString(rawText: String): RemotePairingConfig? {
        val clean = rawText.trim()
        if (clean.isBlank()) return null

        try {
            // 1. hermes:// URI схема
            if (clean.startsWith("hermes://", ignoreCase = true)) {
                val query = clean.substringAfter("?", "")
                val params = if (query.isNotBlank()) {
                    query.split("&").associate { param ->
                        val parts = param.split("=", limit = 2)
                        val key = try { java.net.URLDecoder.decode(parts[0], "UTF-8") } catch (_: Exception) { parts[0] }
                        val value = if (parts.size > 1) {
                            try { java.net.URLDecoder.decode(parts[1], "UTF-8") } catch (_: Exception) { parts[1] }
                        } else ""
                        key to value
                    }
                } else emptyMap()

                val targetUrl = params["url"] ?: params["server"]
                val token = params["token"] ?: params["auth"]
                val name = params["name"] ?: "Hermes Server"
                if (!targetUrl.isNullOrBlank()) {
                    val wsUrl = convertToWsUrl(targetUrl, token)
                    return RemotePairingConfig(serverUrl = wsUrl, token = token, name = name)
                }
            }

            // 2. JSON формат
            if (clean.startsWith("{") && clean.endsWith("}")) {
                val obj = json.parseToJsonElement(clean).jsonObject
                val targetUrl = obj["url"]?.jsonPrimitive?.content
                    ?: obj["server"]?.jsonPrimitive?.content
                    ?: obj["endpoint"]?.jsonPrimitive?.content
                val token = obj["token"]?.jsonPrimitive?.content
                val name = obj["name"]?.jsonPrimitive?.content ?: "Удалённый сервер"
                if (!targetUrl.isNullOrBlank()) {
                    val wsUrl = convertToWsUrl(targetUrl, token)
                    return RemotePairingConfig(serverUrl = wsUrl, token = token, name = name)
                }
            }

            // 3. Прямой URL адрес
            if (clean.startsWith("ws://", ignoreCase = true) ||
                clean.startsWith("wss://", ignoreCase = true) ||
                clean.startsWith("http://", ignoreCase = true) ||
                clean.startsWith("https://", ignoreCase = true)
            ) {
                val wsUrl = convertToWsUrl(clean, null)
                return RemotePairingConfig(serverUrl = wsUrl, token = null, name = "Удалённый сервер")
            }
        } catch (_: Exception) {
            return null
        }

        return null
    }

    private fun convertToWsUrl(url: String, token: String?): String {
        var result = url.trim()
        if (result.startsWith("http://", ignoreCase = true)) {
            result = "ws://" + result.substring(7)
        } else if (result.startsWith("https://", ignoreCase = true)) {
            result = "wss://" + result.substring(8)
        }

        if (!token.isNullOrBlank() && !result.contains("token=")) {
            result = if (result.contains("?")) {
                "$result&token=$token"
            } else {
                "$result?token=$token"
            }
        }
        return result
    }

    /**
     * Запускает сканирование QR-кода через Google Play Services Code Scanner.
     * Не требует системного разрешения на камеру от пользователя (Google Play Services рисует системный UI).
     */
    fun startQrScan(
        activity: Activity,
        onSuccess: (RemotePairingConfig) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .enableAutoZoom()
                .build()

            val scanner = GmsBarcodeScanning.getClient(activity, options)

            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    val rawValue = barcode.rawValue
                    if (!rawValue.isNullOrBlank()) {
                        val config = parsePairingString(rawValue)
                        if (config != null) {
                            onSuccess(config)
                        } else {
                            onError("QR-код не содержит валидного адреса сервера Hermes")
                        }
                    } else {
                        onError("Пустой QR-код")
                    }
                }
                .addOnCanceledListener {
                    // Пользователь закрыл сканер
                }
                .addOnFailureListener { e ->
                    onError(e.message ?: "Ошибка сканирования QR-кода")
                }
        } catch (e: Exception) {
            onError("Сканер недоступен: ${e.message}")
        }
    }
}
