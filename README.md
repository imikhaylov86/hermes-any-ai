# Hermes Any AI — Native Android Client

<div align="center">
  <img src="icon.jpg" alt="Hermes Any AI" width="160" style="border-radius: 28px;" />
  <h3>Autonomous AI Agent right on your Android device</h3>
  <p>Full-featured native Android client (Kotlin + Jetpack Compose) for the Hermes Agent Runtime ecosystem.</p>

  [![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
  [![Jetpack Compose](https://img.shields.io/badge/Compose-BOM_2024.10-blue.svg)](https://developer.android.com/jetpack/compose)
  [![Android](https://img.shields.io/badge/Android-12.0+_(API_31+)-green.svg)](https://developer.android.com)
  [![License](https://img.shields.io/badge/License-Apache_2.0-orange.svg)](LICENSE)
  [![i18n](https://img.shields.io/badge/Languages-EN%20%7C%20RU-success.svg)](#-internationalization--languages)

  <br/>
  
  **[English](#-english)** • **[Русский](#-русский)**
</div>

---

<a name="english"></a>
## 🇬🇧 English

### 🌟 Key Features (Ranked by Importance)

#### 🤖 1. Autonomous On-Device Agent Runtime (Termux Integration)
* **Local Agent on Your Phone:** Run the complete Hermes Agent daemon directly on Android without relying on desktop PCs or external cloud intermediaries.
* **Silent Background Execution:** Starts and stops the daemon silently in the background via the `com.termux.RUN_COMMAND` system intent — no manual terminal interaction needed.
* **1-Click Updates & Protocol Guard:** Built-in protocol verification (v1.0 • 100% compatibility) and a 1-click update button (`pip install --upgrade hermes-agent`) that automatically pulls the latest runtime and restarts the background service.
* **All-in-One Automated Setup:** Single setup script that configures Python, necessary dependencies, and permissions in seconds.

#### ⚡ 2. Native Streaming Chat, Reasoning (`<think>`) & Tool Approvals
* **High-Speed WebSocket Streaming:** Real-time token streaming with Markdown formatting, syntax-highlighted code blocks, and 1-tap code copying.
* **Expandable Reasoning Accordion:** Inspect chain-of-thought `<think>` traces inside an animated, collapsible drawer with live thinking indicators.
* **Human-in-the-Loop Tool Approvals:** Interactive approval cards for shell commands, file modifications, and web scraping with granular options: *Approve Once*, *Deny*, or *Always Allow*.
* **Interactive Clarification Dialogs:** Dedicated Clarify cards allow the agent to prompt the user with multiple-choice or free-form clarifying questions when instructions are ambiguous.
* **Image Viewer & Gallery Save:** Render generated images with zoom and 1-tap save into Android's media gallery (`Pictures/Hermes`).

#### 🛡️ 3. Multi-Provider Hub & Intelligent Fallback Guard
* **Universal Provider Support:** Built-in support for OpenAI, Anthropic (Claude), local Ollama, and arbitrary custom OpenAI-compatible endpoints.
* **Hardware-Backed Keystore:** All API keys and auth tokens are encrypted using **Android Keystore (AES-256 GCM)**. Keys are never logged in plain text.
* **Automatic Fallback on Rate Limits (HTTP 429):** Designate any model as a backup (`[Fallback]`). If the primary model hits quota limits or server errors, conversations automatically switch to the fallback model with an explicit in-chat notice.

#### 🩺 4. Express Diagnostics & 1-Token Model Micro-Tests
* **Pre-Flight Reachability Probes:** Test local gateways and external provider connectivity in 1 click without polluting chat history.
* **1-Token Micro-Tests:** Benchmark a random model from each configured provider with a minimal 1-token prompt to verify authentication and quota before starting complex work.
* **Actionable Error Codes & Diagnostics Copy:** Compact badges for status codes (`401`, `429`, `500`, `Timeout`, `Offline`) with a "Copy Error Text" button to export full technical diagnostics to clipboard.

#### 📷 5. Zero-Permission QR Pairing (Google Play Services)
* **Privacy-First Scanner:** Uses the **Google Play Services Code Scanner** — scanning occurs in Google's secure system sheet **without requesting camera permissions from the user**.
* **Smart Parsing:** Automatically handles `hermes://connect?url=...&token=...`, server configuration JSONs, and direct URLs (`ws://`, `wss://`, `http://`, `https://`).

#### 💾 6. Full Data Ownership & Backup
* **Markdown Session Export:** Export full conversations — including thoughts and tool executions — to clean Markdown and share via the native Android Share Sheet.
* **Structured JSON Database Backup:** Export and import the entire session history with duplicate protection and offline portability.

#### ⌨️ 7. Custom Slash Commands & Skills Catalog
* **Quick Command Palette:** Press `/` in the input field to search, view descriptions, and auto-insert skills and agent prompts.
* **Custom Command Manager:** Add, edit, reorder, or delete custom slash commands directly in the app.

#### 🌐 8. Bilingual Interface & Dynamic Language Switcher
* **Full English 🇬🇧 and Russian 🇷🇺 Localization:** Every screen, dialog, tool card, and status badge is fully bilingual.
* **Dynamic On-the-Fly Switching:** Change language in Settings anytime with instant UI re-rendering — zero lag, no activity restart required.

---

### 🏗 Architecture

The app is built following Clean Architecture + MVI/MVVM:

```text
app/src/main/java/com/hermes/mobile/
├── presentation/          # Jetpack Compose UI
│   ├── chat/              # ChatScreen, MessageBubble, ThinkingAccordion, ToolApprovalCard, ClarifyCard
│   ├── session/           # SessionDrawerContent, RenameSessionDialog, search & filters
│   ├── provider/          # ProviderBottomSheet, API key entry, model selection, custom providers
│   ├── settings/          # SettingsDialog, TermuxStatusDialog, DiagnosticsReportDialog
│   ├── onboarding/        # Onboarding wizard, Termux detection, remote pairing
│   ├── i18n/              # AppStrings dictionary (RussianStrings, EnglishStrings, LocalAppStrings)
│   ├── service/           # HermesForegroundService (background notification)
│   └── theme/             # Dark Theme palette, AppDimensions tokens
├── viewmodel/             # ViewModels (StateFlow, UI states, MVI/MVVM)
├── domain/                # Business logic & domain models
│   ├── model/             # Pure entities: ChatMessage, SessionInfo, ProviderConfig
│   ├── backup/            # ChatBackupManager (Markdown export, JSON backup/restore)
│   └── repository/        # Repository contracts
├── transport/             # Network & system integrations
│   ├── websocket/         # Full-duplex WebSocket client with reconnection
│   ├── rest/              # Retrofit/OkHttp REST client for daemon control
│   ├── auth/              # RemotePairingHelper, Play Services QR Scanner
│   └── termux/            # TermuxRunner (background launch, package verification)
├── persistence/           # Local storage
│   ├── database/          # Room DB (messages, sessions, indices)
│   └── datastore/         # Jetpack DataStore (preferences, active language, URLs)
└── security/              # Android Keystore Manager (AES-256 GCM)
```

---

### 🚀 Quick Start & Build

#### Prerequisites
* **Android Studio:** Ladybug / Meerkat (2024.2+) or Gradle 8.11+
* **JDK:** 21 (LTS)
* **Android SDK:** 35 (minimum version: Android 12 / API 31)
* **Target Device:** Physical Android smartphone or emulator

#### Building the Debug APK
```powershell
# Clone the repository
git clone https://github.com/imikhaylov86/hermes-any-ai.git
cd hermes-any-ai

# Run tests and assemble APK
.\gradlew.bat testDebugUnitTest assembleDebug
```
The resulting APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

#### Installing to Physical Device (User 0 Isolated)
Use the included PowerShell deployment script that strictly targets the personal profile (User 0):
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install_to_device.ps1
```

---

### 📱 Setting Up Hermes Agent in Termux

To run the agent locally on your smartphone without an external server:
1. Install **[Termux](https://f-droid.org/packages/com.termux/)** from F-Droid or GitHub Releases.
2. Open Termux and run the installer command (available via the 1-click Copy button inside the app):
```bash
pkg update -y && pkg install -y python git libffi openssl && pip install hermes-agent && mkdir -p ~/.termux && echo "allow-external-apps = true" >> ~/.termux/termux.properties && termux-reload-settings
```
3. You can now launch and stop Hermes Agent directly from the app drawer with a single tap.

---

<br/>

<a name="russian"></a>
## 🇷🇺 Русский

### 🌟 Ключевые возможности (по степени важности)

#### 🤖 1. Автономный запуск локального агента прямо на телефоне (Termux)
* **Полная автономность:** Выполнение агента Hermes локально на Android-устройстве без обязательного подключения к ПК или облаку.
* **Скрытый фоновый запуск:** Управление локальным демоном через системный интент `com.termux.RUN_COMMAND` без необходимости открывать терминал вручную.
* **Обновление в 1 клик и контроль протокола:** Встроенная проверка совместимости протокола (v1.0 • 100%) и кнопка обновления агента (`pip install --upgrade hermes-agent`) с автоперезапуском фоновой службы.
* **Автоматический скрипт настройки:** Готовая команда для быстрой установки Python, библиотек и агента в один клик из интерфейса приложения.

#### ⚡ 2. Нативный чат, стриминг ответов, размышления (`<think>`) и одобрение действий
* **Мгновенный стриминг через WebSocket:** Живой вывод токенов в реальном времени с поддержкой Markdown, подсветкой синтаксиса и быстрым копированием кода.
* **Сворачиваемый аккордеон размышлений:** Цепочка рассуждений модели `<think>` упакована в плавный сворачиваемый блок с индикатором мыслительного процесса.
* **Контроль действий (Human-in-the-Loop):** Интерактивные карточки подтверждения вызова инструментов (Tool Approval) с выбором: *Разрешить разово*, *Запретить* или *Разрешать всегда*.
* **Уточняющие вопросы (Clarify):** Специальные карточки диалога, когда агенту требуется уточнить задачу перед запуском инструментов.
* **Просмотр и сохранение картинок:** Полноэкранный просмотр сгенерированных изображений и сохранение в системную Галерею (`Pictures/Hermes`).

#### 🛡️ 3. Хаб провайдеров и автоматическая защита запасной моделью (Fallback Guard)
* **Поддержка любых провайдеров:** OpenAI, Claude (Anthropic), локальная Ollama и любые кастомные OpenAI-совместимые API.
* **Аппаратная безопасность:** Все ключи шифруются в **Android Keystore (AES-256 GCM)** и автоматически маскируются в логах (redacted logging).
* **Защита от исчерпания лимитов (HTTP 429):** Возможность назначить резервную модель (`[Резервная]`). При сбое или исчерпании квоты основной модели диалог автоматически переключается на запасную с уведомлением в чате.

#### 🩺 4. Экспресс-диагностика шлюзов и микро-тест моделей на 1 токен
* **Проверка доступности вне чата:** Экспресс-тест соединения с локальным шлюзом и внешними провайдерами в один клик.
* **Микро-запрос на 1 токен:** Тестирование случайной модели провайдера минимальным запросом для подтверждения авторизации и доступности перед началом работы.
* **Коды ошибок и экспорт лога:** Компактный вывод кодов (`401`, `429`, `500`, `Timeout`, `Offline`) и кнопка «Скопировать текст ошибки» для отправки полных диагностических данных.

#### 📷 5. Сопряжение по QR-коду без запроса прав на камеру (Google Play Services)
* **Безопасное сканирование:** Использование **Google Play Services Code Scanner** через системный диалог Google — камера не запрашивает прямых разрешений у пользователя.
* **Универсальный парсинг:** Поддержка схем `hermes://connect?url=...&token=...`, конфигураций серверов в JSON и прямых адресов `ws://`, `wss://`, `http://`, `https://`.

#### 💾 6. Полный контроль над данными и бэкап
* **Экспорт диалога в Markdown:** Формирование чистого файла беседы со всеми шагами рассуждений и инструментов с отправкой через Android Share Sheet.
* **Резервная копия всей базы в JSON:** Полный экспорт и импорт истории бесед со встроенной защитой от дублирования сообщений.

#### ⌨️ 7. Каталог и менеджер слэш-команд
* **Палитра быстрого выбора:** Ввод символа `/` открывает мгновенный поиск по каталогу системных команд и промптов.
* **Пользовательский редактор команд:** Создание, редактирование и сортировка собственных команд прямо в приложении.

#### 🌐 8. Двуязычный интерфейс с мгновенным переключением
* **Полная локализация (Русский 🇷🇺 и English 🇬🇧):** Все экраны, карточки, диалоговые окна и уведомления переведены на оба языка.
* **Переключение на лету:** Выбор языка в Настройках без задержек и без перезапуска экрана.

---

### 🏗 Архитектура проекта

```text
app/src/main/java/com/hermes/mobile/
├── presentation/          # Jetpack Compose UI
│   ├── chat/              # Чат, баблы сообщений, аккордеон мыслей, слэш-команды
│   ├── session/           # Шторка диалогов, поиск, закрепление, архивация
│   ├── provider/          # Провайдеры, ключи Keystore, выбор моделей, кастомные шлюзы
│   ├── settings/          # Настройки, выбор языка (RU/EN), статус Termux, диагностика
│   ├── onboarding/        # Мастер первого запуска и сопряжения
│   ├── i18n/              # Словарь строк AppStrings, переключатель языков
│   ├── service/           # Фоновая служба HermesForegroundService
│   └── theme/             # Тёмная тема Material 3, токены AppDimensions
├── viewmodel/             # ViewModels (StateFlow, UI-состояния)
├── domain/                # Бизнес-логика и модели данных
├── transport/             # Сетевые клиенты (WebSocket, REST, TermuxRunner, QR Scanner)
├── persistence/           # Room DB и DataStore
└── security/              # Android Keystore Manager
```

---

### 🚀 Сборка и установка

```powershell
# Клонирование репозитория
git clone https://github.com/imikhaylov86/hermes-any-ai.git
cd hermes-any-ai

# Запуск тестов и сборка APK
.\gradlew.bat testDebugUnitTest assembleDebug

# Установка на подключённое устройство (только User 0)
powershell -ExecutionPolicy Bypass -File .\scripts\install_to_device.ps1
```

---

### 📄 Лицензия
Распространяется под лицензией **Apache 2.0**. См. подробности в файле [LICENSE](LICENSE).
