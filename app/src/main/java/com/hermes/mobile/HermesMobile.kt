package com.hermes.mobile

/**
 * Hermes Mobile Client
 * Тонкий нативный Android-клиент (Kotlin + Jetpack Compose) для Hermes Agent.
 *
 * Слои приложения:
 * - presentation: экраны, UI-состояния, компоненты Jetpack Compose
 * - viewmodel: обработка действий пользователя и хранение StateFlow
 * - domain: чистые сущности, модели данных и интерфейсы репозиториев
 * - transport: клиенты WebSocket (hermes-mobile) и REST (Dashboard API)
 * - persistence: DataStore для настроек и Room для offline-кэша
 * - security: Android Keystore для защищенного хранения токенов
 */
