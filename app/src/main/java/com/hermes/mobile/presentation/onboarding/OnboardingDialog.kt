package com.hermes.mobile.presentation.onboarding

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hermes.mobile.R
import com.hermes.mobile.domain.model.ConnectionState
import com.hermes.mobile.presentation.i18n.strings
import com.hermes.mobile.presentation.theme.AccentEmerald
import com.hermes.mobile.presentation.theme.AccentRose
import com.hermes.mobile.presentation.theme.AppDimensions
import com.hermes.mobile.presentation.theme.PrimaryIndigo
import com.hermes.mobile.transport.auth.RemotePairingHelper
import com.hermes.mobile.transport.termux.TermuxRunner
import kotlinx.coroutines.delay

enum class WizardStep {
    WELCOME,
    TERMUX_CHECK,
    TERMUX_SETUP,
    TERMUX_LAUNCH,
    REMOTE_SERVER
}

@Composable
fun OnboardingDialog(
    connectionState: ConnectionState,
    onStartDaemonInBackground: (onResult: ((Boolean, String?) -> Unit)?) -> Unit,
    onReconnect: () -> Unit = {},
    onEnableDemoMode: () -> Unit,
    onSetServerUrl: (String) -> Unit = {},
    onCustomServer: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val appStrings = strings

    var currentStep by remember { mutableStateOf(WizardStep.WELCOME) }
    var isTermuxInstalled by remember { mutableStateOf(TermuxRunner.isTermuxInstalled(context)) }
    var isHermesAgentAppInstalled by remember { mutableStateOf(TermuxRunner.isHermesAgentAppInstalled(context)) }

    // Автоматическая проверка при возврате в приложение из Termux / браузера (ON_RESUME)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isTermuxInstalled = TermuxRunner.isTermuxInstalled(context)
                isHermesAgentAppInstalled = TermuxRunner.isHermesAgentAppInstalled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Фоновый периодический опрос статуса установки окружения
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            val updatedTermux = TermuxRunner.isTermuxInstalled(context)
            val updatedHermes = TermuxRunner.isHermesAgentAppInstalled(context)
            if (updatedTermux != isTermuxInstalled) {
                isTermuxInstalled = updatedTermux
            }
            if (updatedHermes != isHermesAgentAppInstalled) {
                isHermesAgentAppInstalled = updatedHermes
            }
        }
    }

    var isStartingDaemon by remember { mutableStateOf(false) }
    var daemonErrorMessage by remember { mutableStateOf<String?>(null) }
    var showManualCommandHint by remember { mutableStateOf(false) }
    var customUrlInput by remember { mutableStateOf("ws://192.168.1.100:8000/ws") }

    // Автоматическое закрытие онбординга при успешном подключении на этапе запуска
    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.READY && currentStep == WizardStep.TERMUX_LAUNCH) {
            delay(1200)
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = AppDimensions.ShapeDialog,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 14.dp, vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Верхний бар с индикатором шагов или кнопкой "Закрыть"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep != WizardStep.WELCOME) {
                        IconButton(
                            onClick = {
                                currentStep = when (currentStep) {
                                    WizardStep.TERMUX_CHECK -> WizardStep.WELCOME
                                    WizardStep.TERMUX_SETUP -> WizardStep.TERMUX_CHECK
                                    WizardStep.TERMUX_LAUNCH -> WizardStep.TERMUX_SETUP
                                    WizardStep.REMOTE_SERVER -> WizardStep.WELCOME
                                    WizardStep.WELCOME -> WizardStep.WELCOME
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = appStrings.onboardingWizardPrevStep,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(36.dp))
                    }

                    // Индикатор шагов визарда (если мы внутри сценария локального агента)
                    if (currentStep == WizardStep.TERMUX_CHECK ||
                        currentStep == WizardStep.TERMUX_SETUP ||
                        currentStep == WizardStep.TERMUX_LAUNCH
                    ) {
                        WizardStepper(
                            currentStepIndex = when (currentStep) {
                                WizardStep.TERMUX_CHECK -> 1
                                WizardStep.TERMUX_SETUP -> 2
                                WizardStep.TERMUX_LAUNCH -> 3
                                else -> 1
                            },
                            totalSteps = 3
                        )
                    } else {
                        Text(
                            text = appStrings.appName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = PrimaryIndigo
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = appStrings.close,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Основное содержимое шага (со скроллом)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = {
                            if (targetState.ordinal > initialState.ordinal) {
                                (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                    slideOutHorizontally { width -> -width } + fadeOut()
                                )
                            } else {
                                (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                    slideOutHorizontally { width -> width } + fadeOut()
                                )
                            }
                        },
                        label = "WizardStepTransition"
                    ) { step ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            when (step) {
                                WizardStep.WELCOME -> {
                                    WelcomeStepContent(
                                        onStartLocal = {
                                            onSetServerUrl("ws://127.0.0.1:8000/api/ws?token=hermes_mobile_local")
                                            currentStep = WizardStep.TERMUX_CHECK
                                        },
                                        onSelectRemote = { currentStep = WizardStep.REMOTE_SERVER },
                                        onEnableDemo = {
                                            onEnableDemoMode()
                                            onDismiss()
                                        }
                                    )
                                }
                                WizardStep.TERMUX_CHECK -> {
                                    TermuxCheckStepContent(
                                        isTermuxInstalled = isTermuxInstalled,
                                        isHermesAppInstalled = isHermesAgentAppInstalled,
                                        onDownloadFDroid = { TermuxRunner.openTermuxDownloadPage(context) },
                                        onCheckAgain = {
                                            isTermuxInstalled = TermuxRunner.isTermuxInstalled(context)
                                            isHermesAgentAppInstalled = TermuxRunner.isHermesAgentAppInstalled(context)
                                            Toast.makeText(
                                                context,
                                                if (isTermuxInstalled || isHermesAgentAppInstalled) appStrings.onboardingTermuxDetected else appStrings.onboardingTermuxNotInstalled,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        onNext = { currentStep = WizardStep.TERMUX_SETUP }
                                    )
                                }
                                WizardStep.TERMUX_SETUP -> {
                                    TermuxSetupStepContent(
                                        command = TermuxRunner.SETUP_COMMAND,
                                        isHermesAppInstalled = isHermesAgentAppInstalled,
                                        onCopyAndOpenTermux = {
                                            clipboardManager.setText(AnnotatedString(TermuxRunner.SETUP_COMMAND))
                                            Toast.makeText(context, appStrings.onboardingCommandCopied, Toast.LENGTH_SHORT).show()
                                            if (!TermuxRunner.openTermux(context)) {
                                                TermuxRunner.openTermuxDownloadPage(context)
                                            }
                                        },
                                        onCopyOnly = {
                                            clipboardManager.setText(AnnotatedString(TermuxRunner.SETUP_COMMAND))
                                            Toast.makeText(context, appStrings.onboardingCommandCopied, Toast.LENGTH_SHORT).show()
                                        },
                                        onNext = { currentStep = WizardStep.TERMUX_LAUNCH }
                                    )
                                }
                                WizardStep.TERMUX_LAUNCH -> {
                                    TermuxLaunchStepContent(
                                        connectionState = connectionState,
                                        isStarting = isStartingDaemon,
                                        isHermesAgentAppInstalled = isHermesAgentAppInstalled,
                                        isTermuxInstalled = isTermuxInstalled,
                                        errorMessage = daemonErrorMessage,
                                        showManualHint = showManualCommandHint,
                                        onToggleManualHint = { showManualCommandHint = !showManualCommandHint },
                                        onStartDaemon = {
                                            isStartingDaemon = true
                                            daemonErrorMessage = null
                                            onStartDaemonInBackground { success, err ->
                                                isStartingDaemon = false
                                                if (success) {
                                                    Toast.makeText(context, appStrings.onboardingDaemonSentToast, Toast.LENGTH_SHORT).show()
                                                } else {
                                                    daemonErrorMessage = err ?: "Ошибка отправки команды"
                                                    Toast.makeText(context, err ?: "Ошибка запуска", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        },
                                        onOpenHermesAgentApp = {
                                            if (!TermuxRunner.openHermesAgentApp(context)) {
                                                Toast.makeText(context, "Не удалось открыть Hermes Agent", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onOpenTermuxManual = {
                                            clipboardManager.setText(AnnotatedString(TermuxRunner.LAUNCH_COMMAND))
                                            Toast.makeText(context, appStrings.onboardingCommandCopied, Toast.LENGTH_SHORT).show()
                                            if (!TermuxRunner.openTermux(context)) {
                                                TermuxRunner.openTermuxDownloadPage(context)
                                            }
                                        },
                                        onOpenPermissions = {
                                            TermuxRunner.openAppPermissions(context)
                                        },
                                        onRetryConnection = {
                                            onReconnect()
                                            Toast.makeText(context, "Повторное подключение...", Toast.LENGTH_SHORT).show()
                                        },
                                        onComplete = onDismiss
                                    )
                                }
                                WizardStep.REMOTE_SERVER -> {
                                    RemoteServerStepContent(
                                        customUrl = customUrlInput,
                                        onUrlChange = { customUrlInput = it },
                                        onConnect = {
                                            if (customUrlInput.isNotBlank()) {
                                                onCustomServer(customUrlInput.trim())
                                                onDismiss()
                                            }
                                        },
                                        onBack = { currentStep = WizardStep.WELCOME }
                                    )
                                }
                            }
                        }
                    }
                }

                // Нижний футер с быстрыми действиями (Демо / Пропустить)
                if (currentStep == WizardStep.WELCOME || currentStep == WizardStep.TERMUX_CHECK) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                onEnableDemoMode()
                                onDismiss()
                            }
                        ) {
                            Text(
                                text = appStrings.onboardingDemoModeBtn,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        TextButton(onClick = onDismiss) {
                            Text(
                                text = appStrings.onboardingSkipBtn,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Индикатор шагов визарда (Breadcrumb Stepper)
 */
@Composable
private fun WizardStepper(
    currentStepIndex: Int,
    totalSteps: Int
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (i in 1..totalSteps) {
            val isDone = i < currentStepIndex
            val isActive = i == currentStepIndex

            val bg = when {
                isDone -> AccentEmerald
                isActive -> PrimaryIndigo
                else -> MaterialTheme.colorScheme.surfaceVariant
            }

            val textClr = when {
                isDone || isActive -> Color.White
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(bg),
                contentAlignment = Alignment.Center
            ) {
                if (isDone) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                } else {
                    Text(
                        text = "$i",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = textClr
                    )
                }
            }

            if (i < totalSteps) {
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(2.dp)
                        .background(
                            if (i < currentStepIndex) AccentEmerald else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                )
            }
        }
    }
}

/**
 * ШАГ 0: Приветствие и выбор режима
 */
@Composable
private fun WelcomeStepContent(
    onStartLocal: () -> Unit,
    onSelectRemote: () -> Unit,
    onEnableDemo: () -> Unit
) {
    val appStrings = strings

    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(AppDimensions.ShapeCard)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_icon),
            contentDescription = "Hermes",
            modifier = Modifier
                .size(72.dp)
                .clip(AppDimensions.ShapeCard)
        )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = appStrings.onboardingWelcome,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = appStrings.onboardingTagline,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }

    Spacer(modifier = Modifier.height(6.dp))

    // Карточка 1: Локальный агент на телефоне (Основной)
    Surface(
        shape = AppDimensions.ShapeCard,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.5.dp, PrimaryIndigo.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onStartLocal)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(PrimaryIndigo.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Smartphone,
                    contentDescription = null,
                    tint = PrimaryIndigo,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = appStrings.onboardingWizardModeLocalTitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = appStrings.onboardingWizardModeLocalDesc,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = PrimaryIndigo,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    // Карточка 2: Удалённый сервер
    Surface(
        shape = AppDimensions.ShapeCard,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelectRemote)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appStrings.onboardingWizardModeRemoteTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = appStrings.onboardingWizardModeRemoteDesc,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    // Кнопка начала настройки
    Button(
        onClick = onStartLocal,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = AppDimensions.ShapeButton,
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = appStrings.onboardingWizardStartLocalBtn,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * ШАГ 1: Проверка и установка Termux / Hermes Agent
 */
@Composable
private fun TermuxCheckStepContent(
    isTermuxInstalled: Boolean,
    isHermesAppInstalled: Boolean,
    onDownloadFDroid: () -> Unit,
    onCheckAgain: () -> Unit,
    onNext: () -> Unit
) {
    val appStrings = strings
    val hasAnyAgentEnvironment = isTermuxInstalled || isHermesAppInstalled

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = appStrings.onboardingWizardStep1,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (hasAnyAgentEnvironment) appStrings.onboardingWizardTermuxFoundDesc else appStrings.onboardingTermuxDescNotInstalled,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }

    // Карточка статуса Termux / Hermes App
    Surface(
        shape = AppDimensions.ShapeCard,
        color = if (hasAnyAgentEnvironment) AccentEmerald.copy(alpha = 0.1f) else AccentRose.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, if (hasAnyAgentEnvironment) AccentEmerald.copy(alpha = 0.35f) else AccentRose.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (hasAnyAgentEnvironment) AccentEmerald else AccentRose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasAnyAgentEnvironment) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isHermesAppInstalled && isTermuxInstalled -> "Termux и Hermes Agent установлены"
                        isHermesAppInstalled -> appStrings.onboardingHermesAppDetected
                        isTermuxInstalled -> appStrings.onboardingWizardTermuxFound
                        else -> appStrings.onboardingWizardTermuxMissing
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (hasAnyAgentEnvironment) AccentEmerald else AccentRose
                )
                Text(
                    text = if (hasAnyAgentEnvironment) "Окружение готово к следующему шагу" else "Требуется установка приложения Termux",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onCheckAgain, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = appStrings.onboardingCheckInstalledBtn,
                    tint = PrimaryIndigo,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    if (!hasAnyAgentEnvironment) {
        // Предупреждение о Google Play
        Surface(
            shape = AppDimensions.ShapeCard,
            color = Color(0xFFF59E0B).copy(alpha = 0.12f),
            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(20.dp).padding(top = 1.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = appStrings.onboardingWizardTermuxWarning,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 17.sp
                )
            }
        }

        // Кнопки скачивания
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onDownloadFDroid,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = AppDimensions.ShapeButton,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = appStrings.onboardingWizardDownloadFDroid,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedButton(
                onClick = onCheckAgain,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = AppDimensions.ShapeButton
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = appStrings.onboardingCheckInstalledBtn,
                    fontSize = 12.sp
                )
            }
        }
    } else {
        // Если Termux или Hermes Agent установлены — кнопка перехода к шагу 2
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = AppDimensions.ShapeButton,
            colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
        ) {
            Text(
                text = "${appStrings.onboardingWizardNextStep} →",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

/**
 * ШАГ 2: Инструкция по первоначальной настройке
 */
@Composable
private fun TermuxSetupStepContent(
    command: String,
    isHermesAppInstalled: Boolean,
    onCopyAndOpenTermux: () -> Unit,
    onCopyOnly: () -> Unit,
    onNext: () -> Unit
) {
    val appStrings = strings

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = appStrings.onboardingWizardStep2,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isHermesAppInstalled) {
                "Hermes Agent установлен. При использовании Termux выполните шаги ниже:"
            } else {
                "Выполните 3 простых действия для установки агента:"
            },
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }

    // 3 Наглядных карточки с действиями
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InstructionActionItem(
            stepNumber = "1",
            icon = Icons.Default.ContentCopy,
            title = appStrings.onboardingWizardAction1Title,
            description = appStrings.onboardingWizardAction1Desc,
            highlightColor = PrimaryIndigo
        )

        InstructionActionItem(
            stepNumber = "2",
            icon = Icons.Default.TouchApp,
            title = appStrings.onboardingWizardAction2Title,
            description = appStrings.onboardingWizardAction2Desc,
            highlightColor = Color(0xFFF59E0B)
        )

        InstructionActionItem(
            stepNumber = "3",
            icon = Icons.Default.HourglassTop,
            title = appStrings.onboardingWizardAction3Title,
            description = appStrings.onboardingWizardAction3Desc,
            highlightColor = AccentEmerald
        )
    }

    // Блок с кодом команды
    Surface(
        shape = AppDimensions.ShapeCard,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Команда авто-настройки",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                TextButton(
                    onClick = onCopyOnly,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(appStrings.copy, fontSize = 11.sp)
                }
            }

            Surface(
                shape = AppDimensions.ShapeBadge,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = command,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = PrimaryIndigo,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }

    // Кнопка перехода в Termux
    Button(
        onClick = onCopyAndOpenTermux,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = AppDimensions.ShapeButton,
        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
    ) {
        Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = appStrings.onboardingCopyAndOpenTermuxBtn,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }

    // Кнопка перехода к следующему шагу
    OutlinedButton(
        onClick = onNext,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = AppDimensions.ShapeButton
    ) {
        Text(
            text = "${appStrings.onboardingWizardDoneCommandBtn} →",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun InstructionActionItem(
    stepNumber: String,
    icon: ImageVector,
    title: String,
    description: String,
    highlightColor: Color
) {
    Surface(
        shape = AppDimensions.ShapeField,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(highlightColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stepNumber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

/**
 * ШАГ 3: Запуск агента и проверка связи
 */
@Composable
private fun TermuxLaunchStepContent(
    connectionState: ConnectionState,
    isStarting: Boolean,
    isHermesAgentAppInstalled: Boolean,
    isTermuxInstalled: Boolean,
    errorMessage: String?,
    showManualHint: Boolean,
    onToggleManualHint: () -> Unit,
    onStartDaemon: () -> Unit,
    onOpenHermesAgentApp: () -> Unit,
    onOpenTermuxManual: () -> Unit,
    onOpenPermissions: () -> Unit,
    onRetryConnection: () -> Unit,
    onComplete: () -> Unit
) {
    val appStrings = strings
    val isReady = connectionState == ConnectionState.READY

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = if (isReady) appStrings.onboardingWizardLaunchSuccessTitle else appStrings.onboardingWizardStep3,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isReady) appStrings.onboardingWizardLaunchSuccessDesc else appStrings.onboardingWizardLaunchDesc,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }

    if (isReady) {
        // Успех!
        Surface(
            shape = AppDimensions.ShapeCard,
            color = AccentEmerald.copy(alpha = 0.15f),
            border = BorderStroke(1.5.dp, AccentEmerald),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(AccentEmerald),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "Демон Hermes активен (127.0.0.1:8000)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = AccentEmerald
                )

                Text(
                    text = "Вы готовы общаться с ИИ и запускать локальные команды прямо с телефона.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Button(
            onClick = onComplete,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = AppDimensions.ShapeButton,
            colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
        ) {
            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = appStrings.onboardingWizardStartChatBtn,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    } else {
        // Ожидание старта или ошибка связи
        Surface(
            shape = AppDimensions.ShapeCard,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when (connectionState) {
                                    ConnectionState.CONNECTING, ConnectionState.AUTHENTICATING -> Color(0xFFF59E0B)
                                    ConnectionState.READY -> AccentEmerald
                                    else -> AccentRose
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Статус подключения: ${connectionState.name}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Кнопка фонового старта в Termux
                if (isTermuxInstalled) {
                    Button(
                        onClick = onStartDaemon,
                        enabled = !isStarting,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                        shape = AppDimensions.ShapeButton
                    ) {
                        if (isStarting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(appStrings.termuxDaemonStarting, fontSize = 13.sp)
                        } else {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(appStrings.onboardingStartDaemonBtn, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Кнопка открытия отдельного приложения Hermes Agent (если установлено)
                if (isHermesAgentAppInstalled) {
                    OutlinedButton(
                        onClick = onOpenHermesAgentApp,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = AppDimensions.ShapeButton
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp), tint = PrimaryIndigo)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(appStrings.onboardingOpenHermesAppBtn, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Кнопка принудительного повторного подключения
                OutlinedButton(
                    onClick = onRetryConnection,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = AppDimensions.ShapeButton
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(appStrings.onboardingRetryConnectionBtn, fontSize = 12.sp)
                }
            }
        }

        // Предупреждение об ошибке фонового запуска
        if (errorMessage != null) {
            Surface(
                shape = AppDimensions.ShapeCard,
                color = AccentRose.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, AccentRose.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AccentRose, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = appStrings.onboardingPermissionsRequired,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentRose
                        )
                    }
                    Text(
                        text = errorMessage,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenPermissions,
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = AppDimensions.ShapeButton
                        ) {
                            Text(appStrings.onboardingOpenSettingsBtn, fontSize = 11.sp)
                        }
                        Button(
                            onClick = onOpenTermuxManual,
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = AppDimensions.ShapeButton,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
                        ) {
                            Text(appStrings.onboardingWizardOpenTermuxManual, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Подсказка ручного запуска
        Surface(
            shape = AppDimensions.ShapeField,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ручной запуск через Termux",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onToggleManualHint, contentPadding = PaddingValues(0.dp)) {
                        Text(
                            text = if (showManualHint) appStrings.thinkingCollapse else appStrings.thinkingExpand,
                            fontSize = 11.sp
                        )
                    }
                }

                AnimatedVisibility(visible = showManualHint) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = appStrings.onboardingWizardManualLaunchHint,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                        OutlinedButton(
                            onClick = onOpenTermuxManual,
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppDimensions.ShapeButton
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("${appStrings.onboardingWizardOpenTermuxManual} (с авто-копированием)", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Альтернатива: Удалённый сервер
 */
@Composable
private fun RemoteServerStepContent(
    customUrl: String,
    onUrlChange: (String) -> Unit,
    onConnect: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appStrings = strings

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = appStrings.onboardingWizardModeRemoteTitle,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Введите WebSocket URL агента или отсканируйте QR-код из терминала на ПК (`hermes mobile qr`):",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }

    OutlinedTextField(
        value = customUrl,
        onValueChange = onUrlChange,
        label = { Text(appStrings.agentWsUrlLabel, fontSize = 11.sp) },
        singleLine = true,
        shape = AppDimensions.ShapeField,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            IconButton(onClick = {
                val activity = context as? Activity
                if (activity != null) {
                    RemotePairingHelper.startQrScan(
                        activity = activity,
                        onSuccess = { cfg ->
                            onUrlChange(cfg.serverUrl)
                            Toast.makeText(context, "${appStrings.onboardingQrRecognized}${cfg.name}", Toast.LENGTH_SHORT).show()
                        },
                        onError = { err ->
                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = appStrings.scanQrBtn,
                    tint = PrimaryIndigo
                )
            }
        }
    )

    OutlinedButton(
        onClick = {
            val activity = context as? Activity
            if (activity != null) {
                RemotePairingHelper.startQrScan(
                    activity = activity,
                    onSuccess = { cfg ->
                        onUrlChange(cfg.serverUrl)
                        Toast.makeText(context, "${appStrings.onboardingQrRecognized}${cfg.name}", Toast.LENGTH_SHORT).show()
                    },
                    onError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        },
        shape = AppDimensions.ShapeButton,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(appStrings.onboardingScanQrPrompt, fontSize = 12.sp)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.weight(1f).height(46.dp),
            shape = AppDimensions.ShapeButton
        ) {
            Text(appStrings.onboardingWizardPrevStep, fontSize = 13.sp)
        }

        Button(
            onClick = onConnect,
            modifier = Modifier.weight(1.5f).height(46.dp),
            shape = AppDimensions.ShapeButton,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
        ) {
            Text(
                text = appStrings.onboardingConnectServerBtn,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
