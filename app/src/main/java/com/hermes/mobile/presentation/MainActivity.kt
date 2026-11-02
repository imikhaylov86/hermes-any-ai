package com.hermes.mobile.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.hermes.mobile.HermesApp
import com.hermes.mobile.domain.model.ConnectionState
import com.hermes.mobile.presentation.chat.ChatScreen
import com.hermes.mobile.presentation.i18n.EnStrings
import com.hermes.mobile.presentation.i18n.LocalAppStrings
import com.hermes.mobile.presentation.i18n.RuStrings
import com.hermes.mobile.presentation.service.HermesForegroundService
import com.hermes.mobile.presentation.session.SessionDrawerContent
import com.hermes.mobile.presentation.theme.HermesMobileTheme
import com.hermes.mobile.viewmodel.ChatViewModel
import com.hermes.mobile.viewmodel.SessionViewModel
import com.hermes.mobile.presentation.service.NotificationHelper
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var chatViewModel: ChatViewModel? = null

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            HermesForegroundService.startService(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Запрет поворота экрана — работа строго в вертикальной (portrait) ориентации
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // Запуск фоновой службы агента
        checkAndStartForegroundService()

        val app = HermesApp.instance
        val targetSessionFromIntent = intent?.getStringExtra(NotificationHelper.EXTRA_SESSION_ID)
        val vm = ChatViewModel(
            chatRepository = app.chatRepository,
            sessionRepository = app.sessionRepository,
            settingsManager = app.settingsManager,
            keystoreManager = app.keystoreManager,
            webSocketClient = app.webSocketClient,
            providerRepository = app.providerRepository,
            restClient = app.restClient,
            initialSessionId = targetSessionFromIntent
        )
        chatViewModel = vm
        val chatViewModel = vm
        handleIntent(intent)

        val sessionViewModel = SessionViewModel(app.sessionRepository)

        setContent {
            val appLanguage by chatViewModel.appLanguage.collectAsState()
            val appStrings = remember(appLanguage) { if (appLanguage.equals("en", ignoreCase = true)) EnStrings else RuStrings }

            CompositionLocalProvider(LocalAppStrings provides appStrings) {
                HermesMobileTheme {
                    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                    val scope = rememberCoroutineScope()

                val activeSessionId by chatViewModel.activeSessionId.collectAsState()
                val connectionState by chatViewModel.connectionState.collectAsState()
                val context = androidx.compose.ui.platform.LocalContext.current
                val isFirstLaunch = remember { chatViewModel.isFirstLaunch }
                var showOnboardingDialog by remember { mutableStateOf(isFirstLaunch && connectionState != ConnectionState.READY) }
                var showTermuxStatusDialog by remember { mutableStateOf(false) }
                var showSettingsDialogFromDrawer by remember { mutableStateOf(false) }
                val showDiagnosticsDialog by chatViewModel.showDiagnosticsDialog.collectAsState()
                var showSlashManagerDialogFromSettings by remember { mutableStateOf(false) }
                val diagnosticsReport by chatViewModel.diagnosticsReport.collectAsState()
                val isDiagnosticsRunning by chatViewModel.isDiagnosticsRunning.collectAsState()
                val updateState by chatViewModel.updateState.collectAsState()
                val keepScreenOn by chatViewModel.keepScreenOn.collectAsState()

                LaunchedEffect(keepScreenOn) {
                    if (keepScreenOn) {
                        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                LaunchedEffect(connectionState) {
                    val statusText = when (connectionState) {
                        ConnectionState.READY -> "Hermes готов к работе"
                        ConnectionState.CONNECTING -> "Подключение к агенту..."
                        ConnectionState.AUTHENTICATING -> "Аутентификация агента..."
                        ConnectionState.RECONNECTING -> "Переподключение..."
                        ConnectionState.FAILED -> "Связь с агентом прервана"
                        ConnectionState.DISCONNECTED -> "Агент отключен"
                    }
                    HermesForegroundService.startService(this@MainActivity, statusText)
                }

                LaunchedEffect(Unit) {
                    chatViewModel.checkForUpdates(isManual = false)
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        SessionDrawerContent(
                            viewModel = sessionViewModel,
                            activeSessionId = activeSessionId,
                            connectionState = connectionState,
                            onSelectSession = { newSessionId ->
                                chatViewModel.selectSession(newSessionId)
                            },
                            onActiveSessionDeleted = {
                                chatViewModel.onActiveSessionDeleted()
                            },
                            onCloseDrawer = {
                                scope.launch { drawerState.close() }
                            },
                            onOpenSettings = {
                                showSettingsDialogFromDrawer = true
                            },
                            onOpenTermuxStatus = {
                                showTermuxStatusDialog = true
                            }
                        )
                    }
                ) {
                    ChatScreen(
                        viewModel = chatViewModel,
                        onOpenDrawer = {
                            scope.launch { drawerState.open() }
                        }
                    )
                }

                if (showSettingsDialogFromDrawer) {
                    val isDemoMode by chatViewModel.isDemoMode.collectAsState()
                    val serverUrl by chatViewModel.serverUrl.collectAsState()
                    com.hermes.mobile.presentation.settings.SettingsDialog(
                        isDemoMode = isDemoMode,
                        currentServerUrl = serverUrl,
                        connectionState = connectionState,
                        onToggleDemoMode = { enabled -> chatViewModel.setDemoMode(enabled) },
                        onSaveServerUrl = { url -> chatViewModel.setServerUrl(url) },
                        onOpenDiagnostics = {
                            showSettingsDialogFromDrawer = false
                            chatViewModel.showDiagnosticsDialog.value = true
                            chatViewModel.runDiagnostics()
                        },
                        onOpenProviders = {
                            showSettingsDialogFromDrawer = false
                            chatViewModel.showProviderSheet.value = true
                        },
                        onOpenSlashCommands = {
                            showSettingsDialogFromDrawer = false
                            showSlashManagerDialogFromSettings = true
                        },
                        onOpenTermuxStatus = {
                            showSettingsDialogFromDrawer = false
                            showTermuxStatusDialog = true
                        },
                        onExportBackup = {
                            chatViewModel.exportFullBackup(context)
                        },
                        onRestoreBackup = { jsonString ->
                            chatViewModel.restoreBackupFromJson(context, jsonString) { _, msg ->
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                            }
                        },
                        onCheckForUpdates = {
                            showSettingsDialogFromDrawer = false
                            chatViewModel.checkForUpdates(isManual = true)
                        },
                        isDebugKeysExportEnabled = chatViewModel.isDebugKeysExportEnabled.value,
                        onToggleDebugKeysExport = { enabled ->
                            chatViewModel.setDebugKeysExportEnabled(enabled)
                        },
                        keepScreenOn = keepScreenOn,
                        onToggleKeepScreenOn = { enabled -> chatViewModel.setKeepScreenOn(enabled) },
                        currentLanguage = appLanguage,
                        onSelectLanguage = { lang -> chatViewModel.setAppLanguage(lang) },
                        onDismiss = { showSettingsDialogFromDrawer = false }
                    )
                }

                if (showDiagnosticsDialog) {
                    com.hermes.mobile.presentation.settings.DiagnosticsReportDialog(
                        report = diagnosticsReport,
                        isRunning = isDiagnosticsRunning,
                        onRetry = { chatViewModel.runDiagnostics() },
                        onDismiss = {
                            chatViewModel.showDiagnosticsDialog.value = false
                            chatViewModel.clearDiagnosticsReport()
                        }
                    )
                }

                if (showSlashManagerDialogFromSettings) {
                    val slashCommands by chatViewModel.slashCommands.collectAsState()
                    com.hermes.mobile.presentation.chat.SlashCommandsManagerDialog(
                        commands = slashCommands,
                        onAddCommand = { chatViewModel.addSlashCommand(it) },
                        onEditCommand = { oldCmd, updated -> chatViewModel.updateSlashCommand(oldCmd, updated) },
                        onMoveCommand = { fromIdx, toIdx -> chatViewModel.moveSlashCommand(fromIdx, toIdx) },
                        onDeleteCommand = { chatViewModel.deleteSlashCommand(it) },
                        onResetDefaults = { chatViewModel.resetSlashCommandsToDefault() },
                        onDismiss = { showSlashManagerDialogFromSettings = false }
                    )
                }

                if (showTermuxStatusDialog) {
                    val serverUrl by chatViewModel.serverUrl.collectAsState()
                    com.hermes.mobile.presentation.settings.TermuxStatusDialog(
                        connectionState = connectionState,
                        serverUrl = serverUrl,
                        onStartDaemonInBackground = {
                            chatViewModel.startTermuxDaemon(context)
                        },
                        onReconnect = { chatViewModel.reconnect(force = true) },
                        onOpenWizard = {
                            showTermuxStatusDialog = false
                            showOnboardingDialog = true
                        },
                        onDismiss = { showTermuxStatusDialog = false }
                    )
                }

                if (showOnboardingDialog) {
                    com.hermes.mobile.presentation.onboarding.OnboardingDialog(
                        connectionState = connectionState,
                        onStartDaemonInBackground = { onResult ->
                            chatViewModel.startTermuxDaemon(context, onResult)
                        },
                        onReconnect = {
                            chatViewModel.reconnect(force = true)
                        },
                        onEnableDemoMode = {
                            chatViewModel.setDemoMode(true)
                            chatViewModel.setFirstLaunchDone()
                            showOnboardingDialog = false
                        },
                        onSetServerUrl = { url ->
                            chatViewModel.setServerUrl(url)
                        },
                        onCustomServer = { url ->
                            chatViewModel.setServerUrl(url)
                            chatViewModel.setFirstLaunchDone()
                            showOnboardingDialog = false
                        },
                        onDismiss = {
                            chatViewModel.setFirstLaunchDone()
                            showOnboardingDialog = false
                        }
                    )
                }

                if (updateState !is com.hermes.mobile.domain.update.UpdateState.Idle) {
                    com.hermes.mobile.presentation.settings.UpdateDialog(
                        updateState = updateState,
                        canInstallPackages = chatViewModel.updateManager.canRequestPackageInstalls(),
                        onCheckAgain = { chatViewModel.checkForUpdates(isManual = true) },
                        onStartDownload = { info -> chatViewModel.startDownloadUpdate(info) },
                        onInstallApk = { apkFile -> chatViewModel.installDownloadedApk(apkFile) },
                        onRequestInstallPermission = { chatViewModel.requestInstallPermission() },
                        onDismiss = { chatViewModel.dismissUpdate() }
                    )
                }
            }
        }
    }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getStringExtra("action") == "start_termux_daemon") {
            com.hermes.mobile.transport.termux.TermuxRunner.startHermesDaemonInBackground(this)
        }
        val targetSessionId = intent.getStringExtra(NotificationHelper.EXTRA_SESSION_ID)
        if (!targetSessionId.isNullOrBlank()) {
            chatViewModel?.selectSession(targetSessionId)
        }
    }

    private fun checkAndStartForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                HermesForegroundService.startService(this)
            } else {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            HermesForegroundService.startService(this)
        }
    }

    override fun onDestroy() {
        chatViewModel = null
        super.onDestroy()
    }
}
