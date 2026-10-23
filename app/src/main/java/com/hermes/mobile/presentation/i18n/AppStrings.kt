package com.hermes.mobile.presentation.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

interface AppStrings {
    // Common
    val appName: String
    val close: String
    val cancel: String
    val save: String
    val delete: String
    val retry: String
    val copy: String
    val copied: String
    val error: String
    val success: String
    val online: String
    val offline: String
    val later: String

    // Language settings
    val languageSection: String
    val languageSubtitle: String
    val langRussian: String
    val langEnglish: String

    // Connection statuses
    val statusReadyTermux: String
    val statusReady: String
    val statusConnecting: String
    val statusAuthenticating: String
    val statusReconnecting: String
    val statusFailed: String
    val statusDisconnected: String
    val statusDemo: String

    // Chat Screen
    val defaultSessionTitle: String
    val shareChat: String
    val shareChatMarkdown: String
    val renameChat: String
    val renameChatTitle: String
    val renameChatHint: String
    val emptyWelcomeTitle: String
    val emptyWelcomeSubtitle: String
    val emptyWelcomeHint: String
    val inputPlaceholder: String
    val generating: String
    val stopGenerating: String
    val send: String
    val attachmentFile: String
    val attachmentFiles: String
    val attachmentSingle: String
    val fileDescription: String
    val errorGeneratingResponse: String
    val thinking: String
    val thinkingProgress: String
    val thinkingExpand: String
    val thinkingCollapse: String
    val codeCopied: String

    // Image & Media
    val imageSavedGallerySuccess: String
    val imageSaveFailed: String
    val imageLoadFailed: String
    val imageSaveGallery: String
    val imageFullScreen: String
    val imageSavingProgress: String

    // Sessions Drawer
    val sessionsTitle: String
    val newChat: String
    val searchSessionsHint: String
    val sectionPinned: String
    val sectionActive: String
    val sectionArchived: String
    val pinChat: String
    val unpinChat: String
    val archiveChat: String
    val unarchiveChat: String
    val deleteChatConfirm: String
    val noSessionsFound: String

    // Settings Dialog
    val settingsTitle: String
    val settingsSubtitle: String
    val localAgentCardTitle: String
    val localAgentCardSubtitleOnline: String
    val localAgentCardSubtitleOffline: String
    val localAgentCardStatusOnline: String
    val localAgentCardStatusWaiting: String
    val checkGatewaysTitle: String
    val checkGatewaysDesc: String
    val checkGatewaysBtn: String
    val providersTitle: String
    val providersSubtitle: String
    val configureBtn: String
    val slashManagerTitle: String
    val slashManagerSubtitle: String
    val backupTitle: String
    val backupSubtitle: String
    val createBackupBtn: String
    val restoreBackupBtn: String
    val backupCreated: String
    val backupRestored: String
    val backupError: String
    val connectionParamsAccordion: String
    val demoModeSwitch: String
    val demoModeDesc: String
    val agentWsUrlLabel: String
    val scanQrBtn: String
    val saveUrlBtn: String
    val emulatorChip: String
    val appVersionFooter: String

    // App Updates
    val updateSectionTitle: String
    val checkUpdatesBtn: String
    val checkingUpdates: String
    val updateAvailableTitle: String
    val noUpdatesTitle: String
    val currentVersionLatest: String
    val newVersionAvailableFormat: String
    val whatIsNew: String
    val downloadAndUpdateBtn: String
    val downloadingProgressFormat: String
    val installUpdateBtn: String
    val updateErrorTitle: String
    val installPermissionRequired: String
    val grantPermissionBtn: String

    // Diagnostics Dialog
    val diagTitle: String
    val diagSubtitle: String
    val diagLocalGateway: String
    val diagExternalProviders: String
    val diagRandomModel: String
    val diagModelAvailable: String
    val diagModelError: String
    val diagNoModels: String
    val diagCopyErrorBtn: String
    val diagErrorCopied: String
    val diagSummaryAllOk: String
    val diagSummaryIssues: String
    val diagPollingGateways: String
    val diagSampleModelCost: String
    val diagSummaryFormat: String
    val diagGatewayReachable: String
    val diagGatewayUnreachable: String
    val diagGatewayProvReachable: String
    val diagGatewayProvUnreachable: String
    val diagGatewayErrorCode: String
    val diagModelAvailableStats: String
    val diagModelFailureDefault: String
    val diagModelErrorCode: String
    val diagFailureWord: String

    // Termux Status Dialog
    val termuxTitle: String
    val termuxSubtitle: String
    val termuxProtocolVersion: String
    val termuxUpdateBtn: String
    val termuxOpenWizardBtn: String
    val termuxUpdating: String
    val termuxUpdateSuccess: String
    val termuxDaemonStarting: String
    val termuxDaemonStarted: String
    val termuxStartDaemonBtn: String
    val termuxOpenAppBtn: String
    val termuxAllowExternalNotice: String

    // Provider Sheet
    val providerSheetTitle: String
    val providerSheetSubtitle: String
    val fallbackModelBannerTitle: String
    val fallbackModelBannerDesc: String
    val fallbackModelClearBtn: String
    val fallbackBadge: String
    val setFallbackBtnDesc: String
    val apiKeyLabel: String
    val apiKeySaveBtn: String
    val apiKeySavedToast: String
    val testLatencyBtn: String
    val addCustomProviderTitle: String
    val customProviderNameLabel: String
    val customProviderUrlLabel: String
    val customProviderKeyLabel: String
    val customProviderAddBtn: String
    val provPollingHermes: String
    val provNoProvidersConfigured: String
    val provNoProvidersHint: String
    val provAddProviderBtn: String
    val provStatusConnected: String
    val provStatusAuthRequired: String
    val provStatusError: String
    val provStatusDisconnected: String
    val provNewChip: String
    val provCustomBadge: String
    val provPrimaryBadge: String
    val provAvailableModelsCount: String
    val provNoModelsList: String
    val provNeedApiKeyNotice: String
    val provApiKeyLabel: String
    val provEncryptedKeystore: String
    val provPasteKey: String
    val provHideKey: String
    val provShowKey: String
    val provKeySavedPlaceholder: String
    val provEnterKeyPlaceholder: String
    val provSaveToHermes: String
    val provKeySavedNotice: String
    val provDeleteTitle: String
    val provDeleteConfirm: String
    val provAddTitle: String
    val provAddSubtitle: String
    val provNameLabel: String
    val provNameHint: String
    val provNameLimit: String
    val provNameLimitReached: String
    val provProtocolLabel: String
    val provUrlLabel: String
    val provUrlError: String
    val provUrlDesc: String
    val provKeyLabel: String
    val provKeyNotice: String
    val provTestingConnection: String
    val provTestConnectionBtn: String
    val provTestSuccessLatency: String
    val provTestSuccessModels: String
    val provTestFailed: String
    val provModelsDiscovered: String
    val provMoreModels: String
    val provSaveConfigError: String

    // Slash Commands Manager
    val slashManagerAddBtn: String
    val slashManagerSearchHint: String
    val slashManagerNotFound: String
    val slashManagerMoveUp: String
    val slashManagerMoveDown: String
    val slashManagerEdit: String
    val slashManagerDelete: String
    val slashManagerResetDefaults: String
    val slashManagerDone: String
    val slashDeleteTitle: String
    val slashDeleteConfirm: String
    val slashEditTitle: String
    val slashNewTitle: String
    val slashCmdNameLabel: String
    val slashCmdTitleLabel: String
    val slashCmdTitlePlaceholder: String
    val slashCmdDescLabel: String
    val slashCmdDescPlaceholder: String
    val slashCmdTemplateLabel: String
    val slashErrorEmptyName: String
    val slashErrorEmptyTitle: String
    val slashPaletteTitle: String
    val slashPaletteSelect: String

    // Onboarding Dialog
    val onboardingWelcome: String
    val onboardingTagline: String
    val onboardingTermuxDetected: String
    val onboardingTermuxNotInstalled: String
    val onboardingTermuxDescDetected: String
    val onboardingTermuxDescNotInstalled: String
    val onboardingInstallTermuxBtn: String
    val onboardingStartDaemonBtn: String
    val onboardingFirstSetupTitle: String
    val onboardingCopySetupCommand: String
    val onboardingCommandCopied: String
    val onboardingRemoteServerBtn: String
    val onboardingDemoModeBtn: String
    val onboardingSkipBtn: String
    val onboardingConnectServerBtn: String
    val onboardingScanQrPrompt: String
    val onboardingQrRecognized: String
    val onboardingCheckInstalledBtn: String
    val onboardingCopyAndOpenTermuxBtn: String
    val onboardingDaemonSentToast: String
    val onboardingStep1Title: String
    val onboardingStep1Desc: String
    val onboardingStep2Title: String
    val onboardingStep2Desc: String

    // Onboarding Wizard
    val onboardingWizardStep1: String
    val onboardingWizardStep2: String
    val onboardingWizardStep3: String
    val onboardingWizardModeLocalTitle: String
    val onboardingWizardModeLocalDesc: String
    val onboardingWizardModeRemoteTitle: String
    val onboardingWizardModeRemoteDesc: String
    val onboardingWizardStartLocalBtn: String
    val onboardingWizardTermuxFound: String
    val onboardingWizardTermuxFoundDesc: String
    val onboardingWizardTermuxMissing: String
    val onboardingWizardTermuxWarning: String
    val onboardingWizardDownloadFDroid: String
    val onboardingWizardDownloadGitHub: String
    val onboardingWizardNextStep: String
    val onboardingWizardPrevStep: String
    val onboardingWizardAction1Title: String
    val onboardingWizardAction1Desc: String
    val onboardingWizardAction2Title: String
    val onboardingWizardAction2Desc: String
    val onboardingWizardAction3Title: String
    val onboardingWizardAction3Desc: String
    val onboardingWizardDoneCommandBtn: String
    val onboardingWizardLaunchTitle: String
    val onboardingWizardLaunchDesc: String
    val onboardingWizardLaunchSuccessTitle: String
    val onboardingWizardLaunchSuccessDesc: String
    val onboardingWizardStartChatBtn: String
    val onboardingWizardManualLaunchHint: String
    val onboardingWizardOpenTermuxManual: String
    val onboardingHermesAppDetected: String
    val onboardingOpenHermesAppBtn: String
    val onboardingPermissionsRequired: String
    val onboardingOpenSettingsBtn: String
    val onboardingRetryConnectionBtn: String

    // Tool Approval & Clarify
    val approvalTitle: String
    val approvalParams: String
    val approvalApprove: String
    val approvalDeny: String
    val approvalActionProcessed: String
    val approvalActionPending: String
    val approvalToolLabel: String
    val approvalCollapse: String
    val approvalExpandFull: String
    val approvalUserAllowed: String
    val approvalAlwaysAllowed: String
    val approvalUserDenied: String
    val approvalAlwaysAllowAction: String
    val clarifyTitle: String
    val clarifyPromptTitle: String
}

class RuAppStrings : AppStrings {
    override val appName = "Hermes Any AI"
    override val close = "Закрыть"
    override val cancel = "Отмена"
    override val save = "Сохранить"
    override val delete = "Удалить"
    override val retry = "Повторить"
    override val copy = "Копировать"
    override val copied = "Скопировано"
    override val error = "Ошибка"
    override val success = "Успешно"
    override val online = "В сети"
    override val offline = "Не в сети"
    override val later = "Позже"

    override val languageSection = "Язык приложения / App Language"
    override val languageSubtitle = "Выберите язык интерфейса"
    override val langRussian = "Русский"
    override val langEnglish = "English"

    override val statusReadyTermux = "В сети (Termux)"
    override val statusReady = "В сети"
    override val statusConnecting = "Подключение..."
    override val statusAuthenticating = "Авторизация..."
    override val statusReconnecting = "Восстановление связи..."
    override val statusFailed = "Ошибка связи"
    override val statusDisconnected = "Остановлен"
    override val statusDemo = "Демо-режим"

    override val defaultSessionTitle = "Новая беседа"
    override val shareChat = "Поделиться"
    override val shareChatMarkdown = "Поделиться (Markdown)"
    override val renameChat = "Переименовать"
    override val renameChatTitle = "Переименовать беседу"
    override val renameChatHint = "Введите новое название"
    override val emptyWelcomeTitle = "Hermes Any AI"
    override val emptyWelcomeSubtitle = "Ваш автономный агент прямо на Android"
    override val emptyWelcomeHint = "Задайте любой вопрос, прикрепите файлы или введите / для палитры команд."
    override val inputPlaceholder = "Спросите Hermes..."
    override val generating = "Генерация..."
    override val stopGenerating = "Остановить"
    override val send = "Отправить"
    override val attachmentFile = "Вложенный файл"
    override val attachmentFiles = "Вложенные файлы"
    override val attachmentSingle = "Вложение"
    override val fileDescription = "Файл"
    override val errorGeneratingResponse = "Сбой при генерации ответа"
    override val thinking = "Размышления"
    override val thinkingProgress = "Размышляет..."
    override val thinkingExpand = "Развернуть"
    override val thinkingCollapse = "Свернуть"
    override val codeCopied = "Код скопирован"

    override val imageSavedGallerySuccess = "Сохранено в Галерею (Pictures/Hermes)"
    override val imageSaveFailed = "Не удалось сохранить"
    override val imageLoadFailed = "Не удалось загрузить фото"
    override val imageSaveGallery = "Сохранить в Галерею"
    override val imageFullScreen = "Во весь экран"
    override val imageSavingProgress = "Сохранение..."

    override val sessionsTitle = "Диалоги"
    override val newChat = "Новый диалог"
    override val searchSessionsHint = "Поиск по беседам..."
    override val sectionPinned = "Закреплённые"
    override val sectionActive = "Недавние"
    override val sectionArchived = "В архиве"
    override val pinChat = "Закрепить"
    override val unpinChat = "Открепить"
    override val archiveChat = "В архив"
    override val unarchiveChat = "Вернуть из архива"
    override val deleteChatConfirm = "Удалить этот диалог?"
    override val noSessionsFound = "Диалоги не найдены"

    override val settingsTitle = "Настройки и диагностика"
    override val settingsSubtitle = "Управление агентом и шлюзами"
    override val localAgentCardTitle = "Локальный агент Hermes"
    override val localAgentCardSubtitleOnline = "В сети • 127.0.0.1:8000"
    override val localAgentCardSubtitleOffline = "Ожидание запуска в Termux"
    override val localAgentCardStatusOnline = "В сети"
    override val localAgentCardStatusWaiting = "Ожидание"
    override val checkGatewaysTitle = "Проверка шлюзов и моделей"
    override val checkGatewaysDesc = "Экспресс-тест доступности шлюзов и проверка моделей вне чата с расходом 1 токен."
    override val checkGatewaysBtn = "Проверить доступность"
    override val providersTitle = "Провайдеры и модели"
    override val providersSubtitle = "OpenAI, Claude, Ollama, ключи"
    override val configureBtn = "Настроить"
    override val slashManagerTitle = "Слэш-команды агента"
    override val slashManagerSubtitle = "Каталог быстрых команд и скиллов"
    override val backupTitle = "Резервное копирование и экспорт"
    override val backupSubtitle = "Бэкап всех бесед в JSON или восстановление"
    override val createBackupBtn = "Создать бэкап"
    override val restoreBackupBtn = "Восстановить"
    override val backupCreated = "Резервная копия создана"
    override val backupRestored = "Беседы успешно восстановлены"
    override val backupError = "Ошибка резервного копирования"
    override val connectionParamsAccordion = "Параметры соединения"
    override val demoModeSwitch = "Автономный Демо-режим"
    override val demoModeDesc = "Оффлайн эмуляция агента без Termux"
    override val agentWsUrlLabel = "WebSocket URL агента:"
    override val scanQrBtn = "Сканировать QR-код сервера"
    override val saveUrlBtn = "Сохранить адрес"
    override val emulatorChip = "Эмулятор"
    override val appVersionFooter = "Hermes Any AI v1.0.1 • Native Client"

    // App Updates
    override val updateSectionTitle = "Обновление приложения"
    override val checkUpdatesBtn = "Проверить обновления"
    override val checkingUpdates = "Проверка обновлений..."
    override val updateAvailableTitle = "Доступно обновление"
    override val noUpdatesTitle = "У вас актуальная версия"
    override val currentVersionLatest = "Установлена актуальная версия приложения"
    override val newVersionAvailableFormat = "Доступна новая версия %s"
    override val whatIsNew = "Что нового:"
    override val downloadAndUpdateBtn = "Скачать и обновить"
    override val downloadingProgressFormat = "Загрузка обновления: %d%%"
    override val installUpdateBtn = "Установить обновление"
    override val updateErrorTitle = "Ошибка при обновлении"
    override val installPermissionRequired = "Для установки обновления требуется разрешение на установку приложений из этого источника."
    override val grantPermissionBtn = "Предоставить разрешение"

    override val diagTitle = "Диагностика шлюзов и моделей"
    override val diagSubtitle = "Проверка доступности и микро-тест моделей"
    override val diagLocalGateway = "Локальный шлюз Hermes"
    override val diagExternalProviders = "Внешние провайдеры"
    override val diagRandomModel = "Случайная модель: "
    override val diagModelAvailable = "Доступна"
    override val diagModelError = "Сбой модели"
    override val diagNoModels = "Нет доступных моделей для проверки"
    override val diagCopyErrorBtn = "Скопировать текст ошибки"
    override val diagErrorCopied = "Текст ошибки скопирован"
    override val diagSummaryAllOk = "Все проверенные шлюзы и модели работают стабильно"
    override val diagSummaryIssues = "Обнаружены сбои на некоторых шлюзах или моделях"
    override val diagPollingGateways = "Опрос шлюзов и тестирование моделей..."
    override val diagSampleModelCost = "Случайный выбор моделей, расход 1 токен"
    override val diagSummaryFormat = "Шлюзов: %d/%d • Моделей: %d/%d • Израсходовано: ~%d ток."
    override val diagGatewayReachable = "В сети • %d мс"
    override val diagGatewayUnreachable = "Недоступен"
    override val diagGatewayProvReachable = "Шлюз в сети • %d мс"
    override val diagGatewayProvUnreachable = "Шлюз недоступен"
    override val diagGatewayErrorCode = "Ошибка шлюза: %s"
    override val diagModelAvailableStats = "Доступна • %d токен • %d мс"
    override val diagModelFailureDefault = "Сбой ответа модели"
    override val diagModelErrorCode = "Ошибка модели: %s"
    override val diagFailureWord = "Сбой"

    override val termuxTitle = "Статус агента в Termux"
    override val termuxSubtitle = "Локальный рантайм Hermes"
    override val termuxProtocolVersion = "Протокол Hermes: v1.0 • Совместимость: 100%"
    override val termuxUpdateBtn = "Обновить Hermes (pip upgrade)"
    override val termuxOpenWizardBtn = "Мастер пошаговой настройки"
    override val termuxUpdating = "Запуск обновления в Termux..."
    override val termuxUpdateSuccess = "Команда обновления отправлена в Termux"
    override val termuxDaemonStarting = "Запуск агента в фоне..."
    override val termuxDaemonStarted = "Команда запуска отправлена в Termux"
    override val termuxStartDaemonBtn = "Запустить Hermes в фоне"
    override val termuxOpenAppBtn = "Открыть Termux"
    override val termuxAllowExternalNotice = "Убедитесь, что в Termux разрешён запуск внешних команд (allow-external-apps = true)."

    override val providerSheetTitle = "Провайдеры и модели"
    override val providerSheetSubtitle = "Настройка ключей доступа и выбор активной модели"
    override val fallbackModelBannerTitle = "Резервная модель:"
    override val fallbackModelBannerDesc = "Автоматически включится при ошибках или исчерпании лимитов основной модели (429)."
    override val fallbackModelClearBtn = "Снять"
    override val fallbackBadge = "Резервная"
    override val setFallbackBtnDesc = "Назначить резервной моделью"
    override val apiKeyLabel = "API Ключ:"
    override val apiKeySaveBtn = "Сохранить ключ"
    override val apiKeySavedToast = "Ключ надёжно сохранён в Keystore"
    override val testLatencyBtn = "Тест связи"
    override val addCustomProviderTitle = "Добавить свой провайдер"
    override val customProviderNameLabel = "Название провайдера"
    override val customProviderUrlLabel = "Base URL эндпоинта"
    override val customProviderKeyLabel = "API Ключ (опционально)"
    override val customProviderAddBtn = "Добавить провайдер"
    override val provPollingHermes = "Опрос установленного Hermes..."
    override val provNoProvidersConfigured = "Провайдеры пока не настроены в Hermes"
    override val provNoProvidersHint = "Добавьте провайдера в конфиг агента или подключитесь к запущенному серверу"
    override val provAddProviderBtn = "Добавить провайдера"
    override val provStatusConnected = "Подключен"
    override val provStatusAuthRequired = "Требуется ключ"
    override val provStatusError = "Ошибка"
    override val provStatusDisconnected = "Отключен"
    override val provNewChip = "Новый"
    override val provCustomBadge = "Пользовательский"
    override val provPrimaryBadge = "Основная"
    override val provAvailableModelsCount = "Доступные модели (%d):"
    override val provNoModelsList = "Провайдер не передал список отдельных моделей. Текущая модель: %s"
    override val provNeedApiKeyNotice = "Для моделей %s сначала укажите API-ключ ниже"
    override val provApiKeyLabel = "API-ключ провайдера:"
    override val provEncryptedKeystore = "Зашифрован в Keystore"
    override val provPasteKey = "Вставить ключ"
    override val provHideKey = "Скрыть ключ"
    override val provShowKey = "Показать ключ"
    override val provKeySavedPlaceholder = "•••••••••••••••• (Ключ сохранён)"
    override val provEnterKeyPlaceholder = "Введите ключ API..."
    override val provSaveToHermes = "Сохранить в Hermes"
    override val provKeySavedNotice = "✓ Ключ сохранён в защищённом хранилище и передан агенту Hermes"
    override val provDeleteTitle = "Удалить провайдера?"
    override val provDeleteConfirm = "Провайдер «%s» будет удалён из файла конфигурации агента Hermes (config.yaml), а сохранённый API-ключ будет безвозвратно стёрт из защищённого хранилища устройства."
    override val provAddTitle = "Новый провайдер ИИ"
    override val provAddSubtitle = "Настройки изменят файл config.yaml агента"
    override val provNameLabel = "1. Название провайдера *"
    override val provNameHint = "например: DeepSeek, Groq"
    override val provNameLimit = "Ограничение до 16 символов"
    override val provNameLimitReached = "Достигнут предел в 16 символов"
    override val provProtocolLabel = "2. Тип взаимодействия (API протокол) *"
    override val provUrlLabel = "3. Base URL *"
    override val provUrlError = "URL должен начинаться с http:// или https://"
    override val provUrlDesc = "Адрес API сервера с версией эндпоинта"
    override val provKeyLabel = "4. API-ключ (сохраняется в Android Keystore)"
    override val provKeyNotice = "🔒 Ключ шифруется аппаратным Keystore и передаётся строго в конфиг агента без попадания в логи"
    override val provTestingConnection = "Проверка связи и поиск моделей..."
    override val provTestConnectionBtn = "Проверить соединение и модели"
    override val provTestSuccessLatency = "Связь успешна! Отклик: %d мс"
    override val provTestSuccessModels = "Связь успешна! Отклик: %d мс (найдено моделей: %d)"
    override val provTestFailed = "Сбой соединения с сервером"
    override val provModelsDiscovered = "Обнаружено моделей (%d):"
    override val provMoreModels = "+ еще %d"
    override val provSaveConfigError = "Ошибка сохранения в конфиг Hermes"

    override val slashManagerAddBtn = "Добавить команду (например, /quota)"
    override val slashManagerSearchHint = "Поиск по командам..."
    override val slashManagerNotFound = "Команды не найдены"
    override val slashManagerMoveUp = "Поднять выше"
    override val slashManagerMoveDown = "Опустить ниже"
    override val slashManagerEdit = "Редактировать"
    override val slashManagerDelete = "Удалить команду"
    override val slashManagerResetDefaults = "Сброс к исходным"
    override val slashManagerDone = "Готово"
    override val slashDeleteTitle = "Удалить команду?"
    override val slashDeleteConfirm = "Команда «%s» будет удалена из всплывающей палитры чата."
    override val slashEditTitle = "Редактировать команду"
    override val slashNewTitle = "Новая слэш-команда"
    override val slashCmdNameLabel = "Имя команды *"
    override val slashCmdTitleLabel = "Название *"
    override val slashCmdTitlePlaceholder = "Проверка квот"
    override val slashCmdDescLabel = "Описание"
    override val slashCmdDescPlaceholder = "Запрос лимита токенов и баланса"
    override val slashCmdTemplateLabel = "Шаблон отправки (необязательно)"
    override val slashErrorEmptyName = "Укажите имя команды"
    override val slashErrorEmptyTitle = "Укажите название команды"
    override val slashPaletteTitle = "Команды Hermes"
    override val slashPaletteSelect = "Выбрать"

    override val onboardingWelcome = "Добро пожаловать в Hermes"
    override val onboardingTagline = "Автономный AI-агент прямо на вашем телефоне"
    override val onboardingTermuxDetected = "Termux обнаружен в системе"
    override val onboardingTermuxNotInstalled = "Termux не установлен"
    override val onboardingTermuxDescDetected = "Агент может запускаться в скрытом фоновом режиме"
    override val onboardingTermuxDescNotInstalled = "Для локального запуска агента требуется среда Termux"
    override val onboardingInstallTermuxBtn = "Установить Termux (F-Droid / GitHub)"
    override val onboardingStartDaemonBtn = "Запустить Hermes в фоне"
    override val onboardingFirstSetupTitle = "Первая настройка Hermes"
    override val onboardingCopySetupCommand = "Копировать команду"
    override val onboardingCommandCopied = "Команда скопирована!"
    override val onboardingRemoteServerBtn = "Удалённый сервер"
    override val onboardingDemoModeBtn = "Демо-режим"
    override val onboardingSkipBtn = "Позже"
    override val onboardingConnectServerBtn = "Подключиться к серверу"
    override val onboardingScanQrPrompt = "Сканировать QR-код сервера"
    override val onboardingQrRecognized = "QR-код распознан: "
    override val onboardingCheckInstalledBtn = "Проверить снова"
    override val onboardingCopyAndOpenTermuxBtn = "Скопировать и открыть Termux"
    override val onboardingDaemonSentToast = "Команда запуска отправлена в Termux"
    override val onboardingStep1Title = "Шаг 1: Разовая установка Hermes"
    override val onboardingStep1Desc = "В чистом Termux нет Hermes. Скопируйте команду, откройте Termux, вставьте её (долгий тап -> Paste) и нажмите Enter:"
    override val onboardingStep2Title = "Шаг 2: Фоновый запуск агента"
    override val onboardingStep2Desc = "После завершения установки в Termux нажмите кнопку ниже для старта в фоне:"

    override val onboardingWizardStep1 = "Среда Termux"
    override val onboardingWizardStep2 = "Настройка в Termux"
    override val onboardingWizardStep3 = "Запуск и связь"
    override val onboardingWizardModeLocalTitle = "Автономный агент на телефоне"
    override val onboardingWizardModeLocalDesc = "ИИ работает локально в среде Termux без передачи ваших данных на сторонние серверы."
    override val onboardingWizardModeRemoteTitle = "Удалённый сервер или ПК"
    override val onboardingWizardModeRemoteDesc = "Подключение к уже запущенному агенту Hermes на домашнем компьютере или VPS."
    override val onboardingWizardStartLocalBtn = "Настроить локально на телефоне"
    override val onboardingWizardTermuxFound = "Termux обнаружен!"
    override val onboardingWizardTermuxFoundDesc = "Среда Termux установлена на устройстве. Переходим к инициализации агента."
    override val onboardingWizardTermuxMissing = "Termux не установлен"
    override val onboardingWizardTermuxWarning = "Важно: устанавливайте Termux только из F-Droid или GitHub. Версия в Google Play устарела и не работает."
    override val onboardingWizardDownloadFDroid = "Скачать из F-Droid"
    override val onboardingWizardDownloadGitHub = "Скачать APK с GitHub"
    override val onboardingWizardNextStep = "Продолжить"
    override val onboardingWizardPrevStep = "Назад"
    override val onboardingWizardAction1Title = "1. Скопируйте и откройте Termux"
    override val onboardingWizardAction1Desc = "Нажмите синюю кнопку внизу. Команда настройки скопируется, и откроется терминал Termux."
    override val onboardingWizardAction2Title = "2. Вставьте команду в Termux"
    override val onboardingWizardAction2Desc = "Зажмите палец на чёрном экране Termux -> нажмите «PASTE» -> нажмите Enter (↵) на клавиатуре."
    override val onboardingWizardAction3Title = "3. Дождитесь завершения"
    override val onboardingWizardAction3Desc = "Termux установит Python и агента. Когда появится строка с символом «$», вернитесь сюда."
    override val onboardingWizardDoneCommandBtn = "Команда выполнена, далее"
    override val onboardingWizardLaunchTitle = "Запуск агента Hermes"
    override val onboardingWizardLaunchDesc = "Окружение готово. Запустите фоновый демон Hermes для начала работы."
    override val onboardingWizardLaunchSuccessTitle = "Связь установлена!"
    override val onboardingWizardLaunchSuccessDesc = "Локальный агент Hermes запущен и готов к выполнению задач."
    override val onboardingWizardStartChatBtn = "Перейти к диалогу"
    override val onboardingWizardManualLaunchHint = "Если фоновый запуск заблокирован системой энергосбережения, откройте Termux и выполните команду: hermes run --mobile"
    override val onboardingWizardOpenTermuxManual = "Открыть Termux"
    override val onboardingHermesAppDetected = "Обнаружено приложение Hermes Agent"
    override val onboardingOpenHermesAppBtn = "Открыть Hermes Agent"
    override val onboardingPermissionsRequired = "Требуется разрешение Termux RUN_COMMAND или ручной запуск через терминал"
    override val onboardingOpenSettingsBtn = "Настройки приложения"
    override val onboardingRetryConnectionBtn = "Повторить подключение"

    override val approvalTitle = "Требуется подтверждение действия"
    override val approvalParams = "Параметры:"
    override val approvalApprove = "Разрешить"
    override val approvalDeny = "Отклонить"
    override val approvalActionProcessed = "Действие обработано"
    override val approvalActionPending = "Запрос подтверждения действия"
    override val approvalToolLabel = "Инструмент: "
    override val approvalCollapse = "▲ Свернуть"
    override val approvalExpandFull = "▼ Развернуть полностью"
    override val approvalUserAllowed = "✓ Разрешено вами"
    override val approvalAlwaysAllowed = "✓ Всегда разрешено для этой команды"
    override val approvalUserDenied = "✕ Отклонено вами"
    override val approvalAlwaysAllowAction = "Всегда разрешать для этой команды"
    override val clarifyTitle = "Уточняющий вопрос агента"
    override val clarifyPromptTitle = "Уточняющий вопрос"
}

class EnAppStrings : AppStrings {
    override val appName = "Hermes Any AI"
    override val close = "Close"
    override val cancel = "Cancel"
    override val save = "Save"
    override val delete = "Delete"
    override val retry = "Retry"
    override val copy = "Copy"
    override val copied = "Copied"
    override val error = "Error"
    override val success = "Success"
    override val online = "Online"
    override val offline = "Offline"
    override val later = "Later"

    override val languageSection = "App Language / Язык интерфейса"
    override val languageSubtitle = "Choose display language"
    override val langRussian = "Русский"
    override val langEnglish = "English"

    override val statusReadyTermux = "Online (Termux)"
    override val statusReady = "Online"
    override val statusConnecting = "Connecting..."
    override val statusAuthenticating = "Authenticating..."
    override val statusReconnecting = "Reconnecting..."
    override val statusFailed = "Connection Error"
    override val statusDisconnected = "Stopped"
    override val statusDemo = "Demo Mode"

    override val defaultSessionTitle = "New Chat"
    override val shareChat = "Share"
    override val shareChatMarkdown = "Share (Markdown)"
    override val renameChat = "Rename"
    override val renameChatTitle = "Rename Conversation"
    override val renameChatHint = "Enter conversation name"
    override val emptyWelcomeTitle = "Hermes Any AI"
    override val emptyWelcomeSubtitle = "Your autonomous agent right on Android"
    override val emptyWelcomeHint = "Ask any question, attach files, or type / for command palette."
    override val inputPlaceholder = "Ask Hermes..."
    override val generating = "Generating..."
    override val stopGenerating = "Stop"
    override val send = "Send"
    override val attachmentFile = "Attachment"
    override val attachmentFiles = "Attachments"
    override val attachmentSingle = "Attachment"
    override val fileDescription = "File"
    override val errorGeneratingResponse = "Error generating response"
    override val thinking = "Reasoning"
    override val thinkingProgress = "Thinking..."
    override val thinkingExpand = "Expand"
    override val thinkingCollapse = "Collapse"
    override val codeCopied = "Code copied"

    override val imageSavedGallerySuccess = "Saved to Gallery (Pictures/Hermes)"
    override val imageSaveFailed = "Failed to save image"
    override val imageLoadFailed = "Failed to load image"
    override val imageSaveGallery = "Save to Gallery"
    override val imageFullScreen = "Fullscreen"
    override val imageSavingProgress = "Saving..."

    override val sessionsTitle = "Conversations"
    override val newChat = "New Chat"
    override val searchSessionsHint = "Search conversations..."
    override val sectionPinned = "Pinned"
    override val sectionActive = "Recent"
    override val sectionArchived = "Archived"
    override val pinChat = "Pin"
    override val unpinChat = "Unpin"
    override val archiveChat = "Archive"
    override val unarchiveChat = "Unarchive"
    override val deleteChatConfirm = "Delete this conversation?"
    override val noSessionsFound = "No conversations found"

    override val settingsTitle = "Settings & Diagnostics"
    override val settingsSubtitle = "Agent and gateway management"
    override val localAgentCardTitle = "Local Hermes Agent"
    override val localAgentCardSubtitleOnline = "Online • 127.0.0.1:8000"
    override val localAgentCardSubtitleOffline = "Waiting for Termux launch"
    override val localAgentCardStatusOnline = "Online"
    override val localAgentCardStatusWaiting = "Waiting"
    override val checkGatewaysTitle = "Gateway & Model Health Check"
    override val checkGatewaysDesc = "Express test for gateway reachability and 1-token model check outside chat."
    override val checkGatewaysBtn = "Check Availability"
    override val providersTitle = "Providers & Models"
    override val providersSubtitle = "OpenAI, Claude, Ollama, API Keys"
    override val configureBtn = "Configure"
    override val slashManagerTitle = "Agent Slash Commands"
    override val slashManagerSubtitle = "Catalog of quick commands and skills"
    override val backupTitle = "Backup & Export"
    override val backupSubtitle = "Export all chats to JSON or restore from backup"
    override val createBackupBtn = "Create Backup"
    override val restoreBackupBtn = "Restore"
    override val backupCreated = "Backup created successfully"
    override val backupRestored = "Conversations restored successfully"
    override val backupError = "Backup failed"
    override val connectionParamsAccordion = "Connection Parameters"
    override val demoModeSwitch = "Autonomous Demo Mode"
    override val demoModeDesc = "Offline agent emulation without Termux"
    override val agentWsUrlLabel = "Agent WebSocket URL:"
    override val scanQrBtn = "Scan Server QR Code"
    override val saveUrlBtn = "Save Address"
    override val emulatorChip = "Emulator"
    override val appVersionFooter = "Hermes Any AI v1.0.1 • Native Client"

    // App Updates
    override val updateSectionTitle = "App Updates"
    override val checkUpdatesBtn = "Check for Updates"
    override val checkingUpdates = "Checking for updates..."
    override val updateAvailableTitle = "Update Available"
    override val noUpdatesTitle = "App is Up to Date"
    override val currentVersionLatest = "You are running the latest version of the app"
    override val newVersionAvailableFormat = "New version %s is available"
    override val whatIsNew = "What's new:"
    override val downloadAndUpdateBtn = "Download & Update"
    override val downloadingProgressFormat = "Downloading update: %d%%"
    override val installUpdateBtn = "Install Update"
    override val updateErrorTitle = "Update Error"
    override val installPermissionRequired = "Permission to install apps from this source is required to perform the update."
    override val grantPermissionBtn = "Grant Permission"

    override val diagTitle = "Gateway & Model Diagnostics"
    override val diagSubtitle = "Reachability check and 1-token model micro-test"
    override val diagLocalGateway = "Local Hermes Gateway"
    override val diagExternalProviders = "External Providers"
    override val diagRandomModel = "Sample model: "
    override val diagModelAvailable = "Available"
    override val diagModelError = "Model error"
    override val diagNoModels = "No models available to test"
    override val diagCopyErrorBtn = "Copy Error Text"
    override val diagErrorCopied = "Error diagnostic text copied"
    override val diagSummaryAllOk = "All checked gateways and models are working properly"
    override val diagSummaryIssues = "Issues detected on some gateways or models"
    override val diagPollingGateways = "Probing gateways and testing models..."
    override val diagSampleModelCost = "Random model selection, cost: ~1 token"
    override val diagSummaryFormat = "Gateways: %d/%d • Models: %d/%d • Consumed: ~%d tok."
    override val diagGatewayReachable = "Online • %d ms"
    override val diagGatewayUnreachable = "Unreachable"
    override val diagGatewayProvReachable = "Gateway Online • %d ms"
    override val diagGatewayProvUnreachable = "Gateway Unreachable"
    override val diagGatewayErrorCode = "Gateway Error: %s"
    override val diagModelAvailableStats = "Available • %d token • %d ms"
    override val diagModelFailureDefault = "Model response failure"
    override val diagModelErrorCode = "Model Error: %s"
    override val diagFailureWord = "Failure"

    override val termuxTitle = "Hermes Agent in Termux"
    override val termuxSubtitle = "Local Termux Runtime"
    override val termuxProtocolVersion = "Hermes Protocol: v1.0 • Compatibility: 100%"
    override val termuxUpdateBtn = "Update Hermes (pip upgrade)"
    override val termuxOpenWizardBtn = "Step-by-step Setup Wizard"
    override val termuxUpdating = "Starting update in Termux..."
    override val termuxUpdateSuccess = "Update command sent to Termux"
    override val termuxDaemonStarting = "Starting agent in background..."
    override val termuxDaemonStarted = "Start command sent to Termux"
    override val termuxStartDaemonBtn = "Start Hermes in Background"
    override val termuxOpenAppBtn = "Open Termux"
    override val termuxAllowExternalNotice = "Ensure external command execution is enabled in Termux (allow-external-apps = true)."

    override val providerSheetTitle = "Providers & Models"
    override val providerSheetSubtitle = "API keys setup and active model selection"
    override val fallbackModelBannerTitle = "Fallback Model:"
    override val fallbackModelBannerDesc = "Will automatically take over if the primary model fails or hits 429 rate limit."
    override val fallbackModelClearBtn = "Clear"
    override val fallbackBadge = "Fallback"
    override val setFallbackBtnDesc = "Set as fallback model"
    override val apiKeyLabel = "API Key:"
    override val apiKeySaveBtn = "Save Key"
    override val apiKeySavedToast = "Key securely saved in Keystore"
    override val testLatencyBtn = "Test Ping"
    override val addCustomProviderTitle = "Add Custom Provider"
    override val customProviderNameLabel = "Provider Name"
    override val customProviderUrlLabel = "Endpoint Base URL"
    override val customProviderKeyLabel = "API Key (optional)"
    override val customProviderAddBtn = "Add Provider"
    override val provPollingHermes = "Polling installed Hermes..."
    override val provNoProvidersConfigured = "No providers configured in Hermes yet"
    override val provNoProvidersHint = "Add a provider to agent config or connect to running server"
    override val provAddProviderBtn = "Add Provider"
    override val provStatusConnected = "Connected"
    override val provStatusAuthRequired = "Key Required"
    override val provStatusError = "Error"
    override val provStatusDisconnected = "Disconnected"
    override val provNewChip = "New"
    override val provCustomBadge = "Custom"
    override val provPrimaryBadge = "Primary"
    override val provAvailableModelsCount = "Available Models (%d):"
    override val provNoModelsList = "Provider has not returned specific models list. Current model: %s"
    override val provNeedApiKeyNotice = "For %s models, please enter the API key below first"
    override val provApiKeyLabel = "Provider API Key:"
    override val provEncryptedKeystore = "Encrypted in Keystore"
    override val provPasteKey = "Paste Key"
    override val provHideKey = "Hide Key"
    override val provShowKey = "Show Key"
    override val provKeySavedPlaceholder = "•••••••••••••••• (Key saved)"
    override val provEnterKeyPlaceholder = "Enter API key..."
    override val provSaveToHermes = "Save to Hermes"
    override val provKeySavedNotice = "✓ Key securely stored in Keystore and transferred to Hermes agent"
    override val provDeleteTitle = "Delete Provider?"
    override val provDeleteConfirm = "Provider '%s' will be removed from Hermes configuration (config.yaml), and the saved API key will be permanently deleted from the device Keystore."
    override val provAddTitle = "New AI Provider"
    override val provAddSubtitle = "Settings will update the agent config.yaml"
    override val provNameLabel = "1. Provider Name *"
    override val provNameHint = "e.g.: DeepSeek, Groq"
    override val provNameLimit = "Limit up to 16 characters"
    override val provNameLimitReached = "16 character limit reached"
    override val provProtocolLabel = "2. Interaction Type (API Protocol) *"
    override val provUrlLabel = "3. Base URL *"
    override val provUrlError = "URL must start with http:// or https://"
    override val provUrlDesc = "API server address with endpoint version"
    override val provKeyLabel = "4. API Key (stored in Android Keystore)"
    override val provKeyNotice = "🔒 Key encrypted via hardware Keystore and sent strictly to agent config without logging"
    override val provTestingConnection = "Testing connection and discovering models..."
    override val provTestConnectionBtn = "Test Connection & Models"
    override val provTestSuccessLatency = "Connection successful! Ping: %d ms"
    override val provTestSuccessModels = "Connection successful! Ping: %d ms (models found: %d)"
    override val provTestFailed = "Server connection failed"
    override val provModelsDiscovered = "Models discovered (%d):"
    override val provMoreModels = "+ %d more"
    override val provSaveConfigError = "Error saving to Hermes config"

    override val slashManagerAddBtn = "Add Command (e.g. /quota)"
    override val slashManagerSearchHint = "Search commands..."
    override val slashManagerNotFound = "No commands found"
    override val slashManagerMoveUp = "Move Up"
    override val slashManagerMoveDown = "Move Down"
    override val slashManagerEdit = "Edit"
    override val slashManagerDelete = "Delete Command"
    override val slashManagerResetDefaults = "Reset to Defaults"
    override val slashManagerDone = "Done"
    override val slashDeleteTitle = "Delete Command?"
    override val slashDeleteConfirm = "Command '%s' will be removed from chat palette."
    override val slashEditTitle = "Edit Command"
    override val slashNewTitle = "New Slash Command"
    override val slashCmdNameLabel = "Command Name *"
    override val slashCmdTitleLabel = "Title *"
    override val slashCmdTitlePlaceholder = "Quota check"
    override val slashCmdDescLabel = "Description"
    override val slashCmdDescPlaceholder = "Request token limit and balance"
    override val slashCmdTemplateLabel = "Send Template (optional)"
    override val slashErrorEmptyName = "Please specify command name"
    override val slashErrorEmptyTitle = "Please specify command title"
    override val slashPaletteTitle = "Hermes Commands"
    override val slashPaletteSelect = "Select"

    override val onboardingWelcome = "Welcome to Hermes"
    override val onboardingTagline = "Autonomous AI Agent right on your phone"
    override val onboardingTermuxDetected = "Termux detected on device"
    override val onboardingTermuxNotInstalled = "Termux is not installed"
    override val onboardingTermuxDescDetected = "Agent can run seamlessly in the hidden background"
    override val onboardingTermuxDescNotInstalled = "Termux environment is required for local agent execution"
    override val onboardingInstallTermuxBtn = "Install Termux (F-Droid / GitHub)"
    override val onboardingStartDaemonBtn = "Start Hermes in Background"
    override val onboardingFirstSetupTitle = "First-time Hermes Setup"
    override val onboardingCopySetupCommand = "Copy Command"
    override val onboardingCommandCopied = "Command copied!"
    override val onboardingRemoteServerBtn = "Remote Server"
    override val onboardingDemoModeBtn = "Demo Mode"
    override val onboardingSkipBtn = "Later"
    override val onboardingConnectServerBtn = "Connect to Server"
    override val onboardingScanQrPrompt = "Scan Server QR Code"
    override val onboardingQrRecognized = "QR Code recognized: "
    override val onboardingCheckInstalledBtn = "Check Again"
    override val onboardingCopyAndOpenTermuxBtn = "Copy & Open Termux"
    override val onboardingDaemonSentToast = "Launch command sent to Termux"
    override val onboardingStep1Title = "Step 1: One-time Hermes Setup"
    override val onboardingStep1Desc = "Clean Termux does not include Hermes. Copy command, open Termux, paste (long tap -> Paste) and hit Enter:"
    override val onboardingStep2Title = "Step 2: Background Launch"
    override val onboardingStep2Desc = "After setup finishes in Termux, tap below to start the daemon in background:"

    override val onboardingWizardStep1 = "Termux Environment"
    override val onboardingWizardStep2 = "Termux Setup"
    override val onboardingWizardStep3 = "Launch & Connect"
    override val onboardingWizardModeLocalTitle = "Autonomous Agent on Phone"
    override val onboardingWizardModeLocalDesc = "AI runs locally inside Termux environment without sending your data to third-party clouds."
    override val onboardingWizardModeRemoteTitle = "Remote Server or PC"
    override val onboardingWizardModeRemoteDesc = "Connect to an already running Hermes instance on your PC or VPS."
    override val onboardingWizardStartLocalBtn = "Setup Locally on Phone"
    override val onboardingWizardTermuxFound = "Termux detected!"
    override val onboardingWizardTermuxFoundDesc = "Termux environment is installed. Proceeding to agent initialization."
    override val onboardingWizardTermuxMissing = "Termux is not installed"
    override val onboardingWizardTermuxWarning = "Important: install Termux from F-Droid or GitHub only. The Google Play version is deprecated and broken."
    override val onboardingWizardDownloadFDroid = "Download from F-Droid"
    override val onboardingWizardDownloadGitHub = "Download APK from GitHub"
    override val onboardingWizardNextStep = "Continue"
    override val onboardingWizardPrevStep = "Back"
    override val onboardingWizardAction1Title = "1. Copy & Open Termux"
    override val onboardingWizardAction1Desc = "Tap the blue button below. Setup command will be copied, and Termux will open automatically."
    override val onboardingWizardAction2Title = "2. Paste command into Termux"
    override val onboardingWizardAction2Desc = "Long-press the black Termux screen -> tap «PASTE» -> hit Enter (↵) on your keyboard."
    override val onboardingWizardAction3Title = "3. Wait for completion"
    override val onboardingWizardAction3Desc = "Termux will install Python and Hermes. When a new line with «$» appears, return here."
    override val onboardingWizardDoneCommandBtn = "Command completed, Next"
    override val onboardingWizardLaunchTitle = "Start Hermes Agent"
    override val onboardingWizardLaunchDesc = "Environment is ready. Start the background Hermes daemon to begin."
    override val onboardingWizardLaunchSuccessTitle = "Connected successfully!"
    override val onboardingWizardLaunchSuccessDesc = "Local Hermes agent is active and ready for your requests."
    override val onboardingWizardStartChatBtn = "Start Chatting"
    override val onboardingWizardManualLaunchHint = "If background start is blocked by battery saver, open Termux and run: hermes run --mobile"
    override val onboardingWizardOpenTermuxManual = "Open Termux"
    override val onboardingHermesAppDetected = "Hermes Agent app detected"
    override val onboardingOpenHermesAppBtn = "Open Hermes Agent"
    override val onboardingPermissionsRequired = "Termux RUN_COMMAND permission or manual start required"
    override val onboardingOpenSettingsBtn = "App Settings"
    override val onboardingRetryConnectionBtn = "Retry Connection"

    override val approvalTitle = "Action Approval Required"
    override val approvalParams = "Parameters:"
    override val approvalApprove = "Approve"
    override val approvalDeny = "Deny"
    override val approvalActionProcessed = "Action Processed"
    override val approvalActionPending = "Action Approval Required"
    override val approvalToolLabel = "Tool: "
    override val approvalCollapse = "▲ Collapse"
    override val approvalExpandFull = "▼ Expand Full"
    override val approvalUserAllowed = "✓ Approved by you"
    override val approvalAlwaysAllowed = "✓ Always allowed for this command"
    override val approvalUserDenied = "✕ Denied by you"
    override val approvalAlwaysAllowAction = "Always allow for this command"
    override val clarifyTitle = "Agent Clarification Request"
    override val clarifyPromptTitle = "Clarification Question"
}

val RuStrings: AppStrings = RuAppStrings()
val EnStrings: AppStrings = EnAppStrings()


val LocalAppStrings = staticCompositionLocalOf { RuStrings }

val strings: AppStrings
    @Composable
    @ReadOnlyComposable
    get() = LocalAppStrings.current
