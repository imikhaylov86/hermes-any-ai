package com.hermes.mobile.transport.termux

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat

/**
 * Управление интеграцией с окружением Termux на Android.
 * Обеспечивает фоновый запуск демона Hermes через RunCommandService (Tasker API)
 * без необходимости ручного открытия терминала пользователем.
 */
object TermuxRunner {

    const val TERMUX_PACKAGE = "com.termux"
    const val TERMUX_SERVICE = "com.termux.app.RunCommandService"
    const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"

    const val EXTRA_RUN_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    const val EXTRA_RUN_COMMAND_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    const val EXTRA_RUN_COMMAND_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    const val EXTRA_RUN_COMMAND_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    const val EXTRA_RUN_COMMAND_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"

    const val BASH_PATH = "/data/data/com.termux/files/usr/bin/bash"
    const val HOME_PATH = "/data/data/com.termux/files/home"

    // Однострочный скрипт авто-настройки (ставит зависимости и включает скрытый запуск)
    const val SETUP_COMMAND = "pkg update -y && pkg install -y python git curl && pip install --upgrade pip && pip install hermes-agent && mkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties && termux-reload-settings"

    // Команда штатного запуска агента
    const val LAUNCH_COMMAND = "termux-wake-lock; hermes run --mobile"

    // Фоновая команда с защитой от дублирования процессов и авто-установкой при отсутствии hermes
    const val BACKGROUND_DAEMON_CMD = "termux-wake-lock; if ! command -v hermes > /dev/null 2>&1; then pkg update -y && pkg install -y python git curl && pip install --upgrade pip && pip install hermes-agent; fi; if ! pgrep -f 'hermes run' > /dev/null 2>&1; then hermes run --mobile > /data/data/com.termux/files/home/hermes_mobile.log 2>&1 & fi"

    // Команда обновления Hermes Agent в Termux в один клик
    const val UPDATE_COMMAND = "termux-wake-lock; pip install --upgrade hermes-agent && pkill -f 'hermes run' ; sleep 1 ; hermes run --mobile > /data/data/com.termux/files/home/hermes_mobile.log 2>&1 &"

    const val F_DROID_TERMUX_URL = "https://f-droid.org/packages/com.termux/"
    const val GITHUB_TERMUX_URL = "https://github.com/termux/termux-app/releases"

    /**
     * Проверяет, установлен ли пакет Termux в системе.
     */
    fun isTermuxInstalled(context: Context): Boolean {
        return try {
            if (context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE) != null) return true
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Запускает Hermes Agent в фоновом режиме через Termux RunCommandService.
     * Не открывает терминал и не прерывает работу пользователя в приложении.
     */
    fun startHermesDaemonInBackground(context: Context): Result<Unit> {
        return try {
            if (!isTermuxInstalled(context)) {
                return Result.failure(IllegalStateException("Termux не установлен на устройстве"))
            }

            val intent = Intent().apply {
                component = ComponentName(TERMUX_PACKAGE, TERMUX_SERVICE)
                action = ACTION_RUN_COMMAND
                putExtra(EXTRA_RUN_COMMAND_PATH, BASH_PATH)
                putExtra(EXTRA_RUN_COMMAND_ARGUMENTS, arrayOf("-c", BACKGROUND_DAEMON_CMD))
                putExtra(EXTRA_RUN_COMMAND_WORKDIR, HOME_PATH)
                putExtra(EXTRA_RUN_COMMAND_BACKGROUND, true)
                putExtra(EXTRA_RUN_COMMAND_SESSION_ACTION, "0")
            }

            try {
                context.startService(intent)
            } catch (_: Exception) {
                // На некоторых версиях Android требуется запуск службы через ContextCompat
                ContextCompat.startForegroundService(context, intent)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Обновляет Hermes Agent в фоновом режиме через Termux RunCommandService.
     */
    fun updateHermesDaemonInBackground(context: Context): Result<Unit> {
        return try {
            if (!isTermuxInstalled(context)) {
                return Result.failure(IllegalStateException("Termux не установлен на устройстве"))
            }

            val intent = Intent().apply {
                component = ComponentName(TERMUX_PACKAGE, TERMUX_SERVICE)
                action = ACTION_RUN_COMMAND
                putExtra(EXTRA_RUN_COMMAND_PATH, BASH_PATH)
                putExtra(EXTRA_RUN_COMMAND_ARGUMENTS, arrayOf("-c", UPDATE_COMMAND))
                putExtra(EXTRA_RUN_COMMAND_WORKDIR, HOME_PATH)
                putExtra(EXTRA_RUN_COMMAND_BACKGROUND, true)
                putExtra(EXTRA_RUN_COMMAND_SESSION_ACTION, "0")
            }

            try {
                context.startService(intent)
            } catch (_: Exception) {
                ContextCompat.startForegroundService(context, intent)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Открывает окно приложения Termux.
     */
    fun openTermux(context: Context): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Открывает браузер со страницей загрузки официального релиза Termux (F-Droid).
     */
    fun openTermuxDownloadPage(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(F_DROID_TERMUX_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_TERMUX_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }
}
