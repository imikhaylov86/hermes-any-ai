# Changelog

All notable changes to **Hermes Any AI** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.1] - 2026-10-23

### 🇬🇧 English

#### ⚡ Performance & Diagnostics
* **Blazing Fast Diagnostics (`/diagnose`):** Diagnostics report now completes in 2–4 seconds (reduced from 3–5 minutes).
* **Smart Probing & Timeout Guard:** Wrapped local daemon check in a strict 2.5s timeout; skips local environment inspection entirely when daemon is offline.
* **Eliminated Probe Multi-Loops:** Removed slow multi-model retry loops during provider health checks, running single concurrent 5s probes per gateway.
* **Registered Slash Commands:** `/diagnose`, `/status`, and `/диагностика` now directly invoke the native `DiagnosticsReportDialog` from the chat bar.

#### 🤖 Termux & Onboarding Overhaul
* **Lifecycle-Aware Termux Detection:** Added `Lifecycle.Event.ON_RESUME` observer and background polling to immediately detect Termux installation upon returning to the app without restarting.
* **Dual-Method Package Detection:** Checks both `getLaunchIntentForPackage` and `getPackageInfo` for high compatibility across Android 12–15.
* **Two-Step Setup Wizard:** Clarified Onboarding into intuitive sequential steps:
  * **Step 1:** One-time Termux Setup with 1-click *Copy & Open Termux* button.
  * **Step 2:** One-click Background Launch of the Hermes Agent.
* **Auto-Install Fallback:** `BACKGROUND_DAEMON_CMD` now checks for `hermes` executable and automatically installs Python, pip, and `hermes-agent` if missing.
* **Frozen Button Protection:** Implemented 3.5s auto-reset on action buttons to prevent permanent disabled/spinning state.

#### 🐛 Bug Fixes
* Fixed Composable state invocation inside non-composable `onClick` listeners in `OnboardingDialog.kt`.
* Synchronized bilingual documentation between English and Russian sections in `README.md`.
* Standardized build output filenames to `HermesAnyAI_v<version>.apk` for release and `HermesAnyAI_v<version>-debug.apk` for debug.
* Maintained backward database compatibility during app updates (`adb install -r -d`).

---

### 🇷🇺 Русский

#### ⚡ Производительность и диагностика
* **Мгновенная диагностика (`/diagnose`):** Время формирования отчёта сокращено с нескольких минут до **2–4 секунд**.
* **Умные тайм-ауты:** Проверка локального сокета ограничена 2.5 секундами; при выключенном демоне бессмысленные запросы переменных окружения мгновенно пропускаются.
* **Удалены зацикленные пробы:** Убран долгий перебор запасных моделей при пинге шлюзов провайдеров.
* **Слеш-команды в чате:** Команды `/diagnose`, `/status` и русская `/диагностика` теперь зарегистрированы в чате и открывают всплывающий отчёт.

#### 🤖 Улучшения онбординга и интеграции с Termux
* **Реактивное определение установки:** Приложение слушает событие `ON_RESUME` и фоновый опрос — Termux определяется сразу при возврате из браузера/терминала без перезапуска приложения.
* **Надёжная проверка пакета:** Проверка через `getLaunchIntentForPackage` и `getPackageInfo` для Android 12–15.
* **Пошаговый мастер настройки:** 
  * **Шаг 1:** Разовая установка с кнопкой «Скопировать и открыть Termux» в один клик.
  * **Шаг 2:** Фоновый запуск демона Hermes.
* **Авто-установка в скрипте:** Фоновая команда запуска теперь проверяет наличие `hermes` и сама устанавливает Python, pip и `hermes-agent`, если пакет отсутствует.
* **Защита от зависания кнопок:** Добавлен 3.5-секундный авто-сброс спиннера на кнопках запуска.

#### 🐛 Исправления ошибок
* Исправлен вызов `@Composable` геттера строк внутри `onClick` слушателей в `OnboardingDialog.kt`.
* Синхронизированы разделы архитектуры и документации в `README.md`.
* Стандартизированы имена сборок: APK теперь всегда сохраняются как `HermesAnyAI_v<версия>.apk` для релиза и `HermesAnyAI_v<версия>-debug.apk` для отладки.
* Полное сохранение пользовательских данных (истории чатов, ключей Keystore, слеш-команд) при обновлении приложения поверх предыдущей версии.

---

## [1.0.0] - 2026-10-22

### Initial Release
* **Autonomous Local Agent Runtime:** Direct execution via Termux `RunCommandService` with wake-lock support.
* **Native Jetpack Compose Streaming Chat:** Real-time token streaming, Markdown formatting, syntax-highlighted code blocks.
* **Chain-of-Thought `<think>` Accordion:** Interactive inspection of model reasoning.
* **Tool Approvals:** Granular permissions (Approve Once, Deny, Always Allow) for terminal, files, and web scraping.
* **Multi-Provider Hub:** Native support for Anthropic Claude, OpenAI, Ollama, and custom OpenAI-compatible endpoints with AES-256 GCM Keystore encryption.
* **Intelligent Rate-Limit Fallbacks:** Automatic fallback switching on HTTP 429 quota exhaustion.
* **Zero-Permission QR Pairing:** Google Play Services Code Scanner sheet for instant server pairing.
* **Full Data Ownership:** Room SQLite storage, Markdown session export, and structured JSON backup/restore.
* **Custom Slash Commands Manager:** Quick `/` palette with in-app command customization.
* **Bilingual UI:** Dynamic runtime switching between English and Russian without activity reload.
