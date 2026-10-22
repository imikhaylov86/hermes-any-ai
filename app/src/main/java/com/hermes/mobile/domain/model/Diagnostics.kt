package com.hermes.mobile.domain.model

/**
 * Результат проверки доступности шлюза (Gateway / Base URL).
 */
data class GatewayCheckResult(
    val name: String,
    val url: String,
    val isReachable: Boolean,
    val latencyMs: Long,
    val errorMessage: String? = null
)

/**
 * Результат проверки доступности конкретной модели.
 * Тестирование проводится вне чата с лимитом 1 токен.
 */
data class ModelCheckResult(
    val modelName: String,
    val isSuccess: Boolean,
    val latencyMs: Long,
    val tokensUsed: Int = 1,
    val errorMessage: String? = null
)

/**
 * Результаты диагностики одного провайдера: шлюз + случайная проверенная модель.
 */
data class ProviderDiagnosticResult(
    val providerId: String,
    val providerName: String,
    val apiMode: ProviderApiMode,
    val gateway: GatewayCheckResult,
    val testedModel: ModelCheckResult?,
    val totalModelsCount: Int
)

/**
 * Общий сводный отчёт диагностики.
 */
data class DiagnosticsReport(
    val localGateway: GatewayCheckResult,
    val providerResults: List<ProviderDiagnosticResult>,
    val timestamp: Long = System.currentTimeMillis()
) {
    val totalGatewaysChecked: Int get() = 1 + providerResults.size
    val totalGatewaysOnline: Int get() = (if (localGateway.isReachable) 1 else 0) + providerResults.count { it.gateway.isReachable }
    val totalModelsTested: Int get() = providerResults.count { it.testedModel != null }
    val totalModelsSuccess: Int get() = providerResults.count { it.testedModel?.isSuccess == true }
    val totalTokensUsed: Int get() = providerResults.sumOf { it.testedModel?.tokensUsed ?: 0 }
    val isAllHealthy: Boolean get() = totalGatewaysOnline == totalGatewaysChecked && (totalModelsTested == 0 || totalModelsSuccess == totalModelsTested)
}
