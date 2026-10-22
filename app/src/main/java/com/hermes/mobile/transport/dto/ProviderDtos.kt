package com.hermes.mobile.transport.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProviderItemDto(
    val id: String,
    val name: String,
    val status: String = "connected", // "connected", "disconnected", "error", "auth_required"
    @SerialName("current_model") val currentModel: String? = null,
    @SerialName("available_models") val availableModels: List<String> = emptyList(),
    @SerialName("has_key") val hasKey: Boolean = false,
    @SerialName("base_url") val baseUrl: String? = null,
    @SerialName("api_mode") val apiMode: String? = null,
    @SerialName("is_custom") val isCustom: Boolean = false
)

@Serializable
data class CustomEndpointDto(
    val id: String = "",
    val name: String,
    @SerialName("api_mode") val apiMode: String = "chat_completions",
    @SerialName("base_url") val baseUrl: String = "",
    val model: String? = null,
    @SerialName("api_key") val apiKey: String? = null,
    val models: List<String> = emptyList(),
    @SerialName("has_api_key") val hasApiKey: Boolean = false,
    @SerialName("api_key_preview") val apiKeyPreview: String? = null
)

@Serializable
data class CustomEndpointsResponseDto(
    val endpoints: List<CustomEndpointDto> = emptyList(),
    val current: kotlinx.serialization.json.JsonElement? = null
)

@Serializable
data class DeleteCustomEndpointResponseDto(
    val success: Boolean = false,
    val name: String? = null
)

@Serializable
data class ValidateEndpointRequestDto(
    val name: String = "",
    @SerialName("base_url") val baseUrl: String,
    val model: String = "default",
    @SerialName("api_key") val apiKey: String? = null,
    @SerialName("api_mode") val apiMode: String? = "chat_completions",
    @SerialName("discover_models") val discoverModels: Boolean = true
)

@Serializable
data class ValidateEndpointResponseDto(
    val ok: Boolean = false,
    val reachable: Boolean = false,
    val message: String? = null,
    val models: List<String> = emptyList()
)

@Serializable
data class CustomEndpointUpdateDto(
    val id: String = "",
    val name: String,
    @SerialName("base_url") val baseUrl: String,
    val model: String,
    @SerialName("api_key") val apiKey: String? = null,
    @SerialName("api_mode") val apiMode: String? = "chat_completions",
    @SerialName("discover_models") val discoverModels: Boolean = true,
    @SerialName("make_default") val makeDefault: Boolean = false,
    val models: List<String> = emptyList()
)

@Serializable
data class OpenAIModelItemDto(
    val id: String
)

@Serializable
data class OpenAIModelsResponseDto(
    val data: List<OpenAIModelItemDto> = emptyList()
)

@Serializable
data class ProvidersResponseDto(
    val providers: List<ProviderItemDto> = emptyList()
)

@Serializable
data class ModelOptionsProviderDto(
    val slug: String,
    val name: String,
    @SerialName("is_current") val isCurrent: Boolean = false,
    @SerialName("is_user_defined") val isUserDefined: Boolean = false,
    val models: List<String> = emptyList(),
    @SerialName("total_models") val totalModels: Int = 0,
    val source: String? = null,
    @SerialName("api_url") val apiUrl: String? = null,
    val authenticated: Boolean = false,
    val usage: kotlinx.serialization.json.JsonElement? = null
)

@Serializable
data class ModelOptionsResponseDto(
    val providers: List<ModelOptionsProviderDto> = emptyList(),
    val models: List<String> = emptyList(),
    val current: String? = null
)

@Serializable
data class ModelInfoDto(
    val model: String? = null,
    val provider: String? = null,
    @SerialName("effective_context_length") val contextLength: Int? = null
)

@Serializable
data class SaveKeyRequestDto(
    @SerialName("provider_id") val providerId: String,
    @SerialName("api_key") val apiKey: String
)

@Serializable
data class SetModelRequestDto(
    val scope: String = "main",
    val provider: String = "",
    val model: String = "",
    val task: String = "",
    @SerialName("base_url") val baseUrl: String = "",
    @SerialName("api_key") val apiKey: String = ""
)

@Serializable
data class TestConnectionResponseDto(
    val success: Boolean,
    @SerialName("latency_ms") val latencyMs: Long = 0,
    @SerialName("error_message") val errorMessage: String? = null
)
