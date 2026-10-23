package com.hermes.mobile.presentation

import android.Manifest
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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

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
        val chatViewModel = ChatViewModel(
            chatRepository = app.chatRepository,
            sessionRepository = app.sessionRepository,
            settingsManager = app.settingsManager,
            keystoreManager = app.keystoreManager,
            webSocketClient = app.webSocketClient,
            providerRepository = app.providerRepository,
            restClient = app.restClient
        )
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
                            chatViewModel.checkForUpdates(isManual = true)
                        },
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
                        onReconnect = { chatViewModel.reconnect() },
                        onDismiss = { showTermuxStatusDialog = false }
                    )
                }

                if (showOnboardingDialog) {
                    com.hermes.mobile.presentation.onboarding.OnboardingDialog(
                        connectionState = connectionState,
                        onStartDaemonInBackground = {
                            chatViewModel.startTermuxDaemon(context)
                        },
                        onEnableDemoMode = {
                            chatViewModel.setDemoMode(true)
                            chatViewModel.setFirstLaunchDone()
                            showOnboardingDialog = false
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
}
