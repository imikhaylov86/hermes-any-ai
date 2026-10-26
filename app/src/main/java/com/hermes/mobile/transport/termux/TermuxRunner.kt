package com.hermes.mobile.transport.termux

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Управление интеграцией с окружением Termux и Hermes Agent на Android.
 * Обеспечивает фоновый запуск демона Hermes через RunCommandService (Tasker API)
 * либо запуск локального приложения Hermes Agent.
 */
object TermuxRunner {

    const val TERMUX_PACKAGE = "com.termux"
    const val TERMUX_SERVICE = "com.termux.app.RunCommandService"
    const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    const val PERMISSION_RUN_COMMAND = "com.termux.permission.RUN_COMMAND"

    const val EXTRA_RUN_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    const val EXTRA_RUN_COMMAND_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    const val EXTRA_RUN_COMMAND_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    const val EXTRA_RUN_COMMAND_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    const val EXTRA_RUN_COMMAND_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"

    const val BASH_PATH = "/data/data/com.termux/files/usr/bin/bash"
    const val HOME_PATH = "/data/data/com.termux/files/home"

    // Python-скрипт, восстанавливающий пакет hermes_cli.dashboard_auth (в wheel hermes-agent
    // модули middleware/prefix/audit/ws_tickets отсутствуют, а web_server.py их импортирует —
    // без них dashboard падает с 500 на каждом запросе) и включающий фиксацию сессионного токена
    // через переменную окружения HERMES_DASHBOARD_SESSION_TOKEN (иначе токен генерируется
    // случайно при каждом старте и мобильное приложение получает 403).
    const val FIX_SCRIPT = "import os\nimport site\n\nsp = site.getsitepackages()[0]\nda = os.path.join(sp, 'hermes_cli', 'dashboard_auth')\nos.makedirs(da, exist_ok=True)\n\nMODULES = {\n    '__init__.py': '''\"\"\"Dashboard auth provider registry.\"\"\"\n\n\n_PROVIDERS = []\n\n\nclass DashboardAuthProvider:\n    name = \"base\"\n    display_name = \"Base Provider\"\n\n\ndef register_provider(provider):\n    if not any(getattr(p, \"name\", \"?\") == provider.name for p in _PROVIDERS):\n        _PROVIDERS.append(provider)\n\n\ndef list_providers():\n    return list(_PROVIDERS)\n''',\n    'middleware.py': '''async def gated_auth_middleware(request, call_next):\n    return await call_next(request)\n''',\n    'routes.py': '''from fastapi import APIRouter\n\nrouter = APIRouter()\n''',\n    'prefix.py': '''def normalise_prefix(raw):\n    if not raw:\n        return \"\"\n    p = raw.strip()\n    if not p:\n        return \"\"\n    if not p.startswith(\"/\"):\n        p = \"/\" + p\n    return p.rstrip(\"/\")\n''',\n    'audit.py': '''from enum import Enum\n\n\nclass AuditEvent(Enum):\n    WS_TICKET_REJECTED = \"ws_ticket_rejected\"\n\n\ndef audit_log(event, **kwargs):\n    return None\n''',\n    'ws_tickets.py': '''class TicketInvalid(Exception):\n    pass\n\n\ndef consume_ticket(ticket):\n    raise TicketInvalid(\"no ticket store configured\")\n''',\n}\n\nfor name, content in MODULES.items():\n    path = os.path.join(da, name)\n    if not os.path.exists(path) or os.path.getsize(path) == 0:\n        with open(path, 'w') as fh:\n            fh.write(content)\n\nws_path = os.path.join(sp, 'hermes_cli', 'web_server.py')\nif os.path.exists(ws_path):\n    with open(ws_path) as fh:\n        src = fh.read()\n    if 'HERMES_DASHBOARD_SESSION_TOKEN' not in src:\n        import re\n        patched = re.sub(\n            r'(_SESSION_TOKEN\\s*=\\s*)secrets\\.token_urlsafe\\(\\s*32\\s*\\)',\n            r'\\1os.environ.get(\"HERMES_DASHBOARD_SESSION_TOKEN\") or secrets.token_urlsafe(32)',\n            src,\n            count=1,\n        )\n        if patched != src:\n            with open(ws_path, 'w') as fh:\n                fh.write(patched)\n"

    const val FIX_SCRIPT_PATH = "~/.hermes_mobile_fix.py"

    // G14 (docs/08 §2.7): зеркала Termux недоступны из РФ на чистой установке.
    // Fallback — прямая перезапись sources.list рабочими зеркалами (не sed-замена:
    // дефолтные записи варьируются между версиями bootstrap — termux.org/packages.termux.dev).
    // echo без \n-эскейпов: printf '\n' в Kotlin-строке даёт реальный перенос строки,
    // ломающий однострочную команду при вставке (артефакт «$б» — docs/08 §2.7).
    const val MIRROR_FALLBACK = "cp \$PREFIX/etc/apt/sources.list \$PREFIX/etc/apt/sources.list.bak 2>/dev/null; echo 'deb https://grimler.se/termux/termux-packages-24 stable main' > \$PREFIX/etc/apt/sources.list; (pkg update -y || (echo 'deb https://termux.mentality.rip/termux/termux-packages-24 stable main' > \$PREFIX/etc/apt/sources.list; pkg update -y))"
    // Однострочный скрипт авто-настройки (включает allow-external-apps, настраивает ~/.bashrc,
    // ставит зависимости, патчит установленный hermes-agent И ЗАПУСКАЕТ демон —
    // финальный уровень [6/6] делает достижимым уровень 0 авто-монитора визарда:
    // установка и запуск — одна команда, «Готово» = демон отвечает).
    // G14: стадийная модель, зеркальный fallback MIRROR_FALLBACK при недоступности
    // дефолтных зеркал (РФ), явные маркеры прогресса и ошибки, wake-lock.
    const val SETUP_COMMAND = "termux-wake-lock; mkdir -p ~/.termux && (grep -q 'allow-external-apps=true' ~/.termux/termux.properties 2>/dev/null || echo 'allow-external-apps=true' >> ~/.termux/termux.properties) && termux-reload-settings; echo '[1/6] Properties OK'; (grep -q 'HERMES_DASHBOARD_SESSION_TOKEN' ~/.bashrc 2>/dev/null || echo 'export HERMES_DASHBOARD_SESSION_TOKEN=\"hermes_mobile_local\"' >> ~/.bashrc) && (grep -q 'HERMES_DASHBOARD_TUI' ~/.bashrc 2>/dev/null || echo 'export HERMES_DASHBOARD_TUI=1' >> ~/.bashrc); echo '[2/6] bashrc OK'; (pkg update -y || (echo 'default mirrors unreachable, switching...'; (" + MIRROR_FALLBACK + ") || echo '[!] all mirrors FAILED - check internet, then rerun')); echo '[3/6] apt update done'; pkg install -y python python-pip python-psutil git curl coreutils procps || echo '[!] pkg install FAILED - rerun this command later'; echo '[4/6] packages done'; if ! command -v python3 >/dev/null 2>&1; then echo '[!] python3 missing - run: pkg install python, then rerun'; elif ! python3 -m pip --version >/dev/null 2>&1; then echo '[!] pip module missing - run: pkg install python-pip, then rerun'; else echo 'installing hermes-agent (10-15 min)...'; if python3 -m pip install hermes-agent 2>&1 | tail -5; then cat > ~/.hermes_mobile_fix.py << 'PYEOF'\n" + FIX_SCRIPT + "PYEOF\npython3 ~/.hermes_mobile_fix.py && echo '[5/6] hermes-agent installed'; else echo '[!] pip install hermes-agent FAILED - see error above, rerun this command (safe)'; fi; fi; echo '[6/6] starting daemon...'; if command -v hermes >/dev/null 2>&1; then pkill -f '[h]ermes dashboard' 2>/dev/null; sleep 1; (hermes dashboard --port 8000 --host 127.0.0.1 --no-open --skip-build --tui > /data/data/com.termux/files/home/hermes_mobile.log 2>&1 &) && disown -a 2>/dev/null && echo 'DONE - daemon starting, return to the app'; else echo '[!] hermes not installed - resolve [!] steps above and rerun this command'; fi"

    // Команда штатного запуска агента с экспортом переменных TUI и сессионного токена.
    // ВАЖНО: pkill с паттерном '[h]ermes dashboard' — квадратные скобки не дают pkill -f
    // сматчить собственный bash-процесс (в его cmdline паттерн записан как '[h]ermes',
    // а не 'hermes'), иначе команда убивает сама себя до старта демона.
    const val LAUNCH_COMMAND = "export PATH=\"\$HOME/.local/bin:/data/data/com.termux/files/usr/bin:\$PATH\"; export HERMES_DASHBOARD_TUI=1; export HERMES_DASHBOARD_SESSION_TOKEN=\"hermes_mobile_local\"; termux-wake-lock; pkill -f '[h]ermes dashboard' 2>/dev/null; sleep 1; hermes dashboard --port 8000 --host 127.0.0.1 --no-open --skip-build --tui"

    // Фоновая команда с перезапуском при вызове (pkill старых процессов), проверкой наличия пакета,
    // применением патча совместимости и чистым стартом в фоне через subshell & disown.
    // pkill использует '[h]ermes' трюк против самоубийства команды (см. LAUNCH_COMMAND).
    // G14: pkg-шаги отделены (сбой установки не блокирует запуск уже установленного hermes),
    // wake-lock держит установку живой.
    const val BACKGROUND_DAEMON_CMD = "export PATH=\"\$HOME/.local/bin:/data/data/com.termux/files/usr/bin:\$PATH\"; export HERMES_DASHBOARD_TUI=1; export HERMES_DASHBOARD_SESSION_TOKEN=\"hermes_mobile_local\"; termux-wake-lock; if ! command -v hermes > /dev/null 2>&1; then (pkg update -y || (" + MIRROR_FALLBACK + ")) && pkg install -y python python-pip python-psutil git curl coreutils procps && python3 -m pip install hermes-agent || echo '[!] install failed - open Termux and run setup again'; fi; if command -v hermes >/dev/null 2>&1; then cat > ~/.hermes_mobile_fix.py << 'PYEOF'\n" + FIX_SCRIPT + "PYEOF\npython3 ~/.hermes_mobile_fix.py 2>/dev/null; pkill -f '[h]ermes dashboard' 2>/dev/null; sleep 1; (hermes dashboard --port 8000 --host 127.0.0.1 --no-open --skip-build --tui > /data/data/com.termux/files/home/hermes_mobile.log 2>&1 &) && disown -a 2>/dev/null; else echo '[!] hermes not installed - run the setup command in the wizard first'; fi"

    // Команда обновления Hermes Agent в Termux в один клик
    const val UPDATE_COMMAND = "export PATH=\"\$HOME/.local/bin:/data/data/com.termux/files/usr/bin:\$PATH\"; export HERMES_DASHBOARD_TUI=1; export HERMES_DASHBOARD_SESSION_TOKEN=\"hermes_mobile_local\"; termux-wake-lock; python3 -m pip install --upgrade hermes-agent && cat > ~/.hermes_mobile_fix.py << 'PYEOF'\n" + FIX_SCRIPT + "PYEOF\npython3 ~/.hermes_mobile_fix.py && pkill -f '[h]ermes dashboard' 2>/dev/null; sleep 1; (hermes dashboard --port 8000 --host 127.0.0.1 --no-open --skip-build --tui > /data/data/com.termux/files/home/hermes_mobile.log 2>&1 &) && disown -a 2>/dev/null"

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
     * Проверяет, предоставлено ли разрешение RUN_COMMAND для Termux.
     */
    fun hasRunCommandPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, PERMISSION_RUN_COMMAND) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Запускает Hermes Agent в фоновом режиме через Termux RunCommandService.
     *
     * Порядок критичен: Termux exec'ит внешние команды только когда его процесс
     * инициализирован и Activity жива. Если Termux был убит/перезагружен, команда,
     * отправленная в мёртвый процесс, молча теряется (RunCommandService стартует
     * процесс, но команда до TermuxService не доходит — создаётся пустая сессия).
     * Поэтому: сначала будим Termux (Activity выводится вперёд), даём ему
     * инициализироваться, и только затем отправляем RunCommand с задержкой.
     */
    fun startHermesDaemonInBackground(context: Context): Result<Unit> {
        return try {
            if (!isTermuxInstalled(context)) {
                return Result.failure(IllegalStateException("Termux не установлен на устройстве"))
            }

            // 1. Разбудить Termux: холодный старт процесса + вывод Activity вперёд.
            wakeUpTermuxForExecution(context)

            // 2. Отправить команду с задержкой: процесс Termux должен успеть
            //    инициализировать TermuxService и сессии (холодный старт ~2-3 сек).
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                sendRunCommandIntent(context, BACKGROUND_DAEMON_CMD)
            }, TERMUX_WAKEUP_DELAY_MS)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Отправляет RUN_COMMAND intent в Termux (сам по себе, без буферизации).
     */
    private fun sendRunCommandIntent(context: Context, command: String) {
        try {
            val intent = Intent().apply {
                component = ComponentName(TERMUX_PACKAGE, TERMUX_SERVICE)
                action = ACTION_RUN_COMMAND
                putExtra(EXTRA_RUN_COMMAND_PATH, BASH_PATH)
                putExtra(EXTRA_RUN_COMMAND_ARGUMENTS, arrayOf("-c", command))
                putExtra(EXTRA_RUN_COMMAND_WORKDIR, HOME_PATH)
                putExtra(EXTRA_RUN_COMMAND_BACKGROUND, true)
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
                // На некоторых версиях Android требуется запуск службы через ContextCompat
                ContextCompat.startForegroundService(context, intent)
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Задержка перед отправкой RunCommand после пробуждения Termux:
     * холодный старт процесса Termux + инициализация TermuxService.
     */
    private const val TERMUX_WAKEUP_DELAY_MS = 3000L

    /**
     * Гарантирует, что процесс Termux запущен и его Activity видима хотя бы
     * мгновение — без этого RunCommandService складирует команды без exec.
     * После запуска демона можно вернуть пользователя кнопкой "назад".
     */
    fun wakeUpTermuxForExecution(context: Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(intent)
            }
        } catch (_: Exception) {
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

            wakeUpTermuxForExecution(context)

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                sendRunCommandIntent(context, UPDATE_COMMAND)
            }, TERMUX_WAKEUP_DELAY_MS)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * G14 (docs/08 §2.7): измерение готовности среды Hermes БЕЗ adb/ПК —
     * приложение само опрашивает состояние. Уровни:
     * 0 — демон отвечает (готово);
     * 1 — порт 8000 открыт, но /api/status молчит (демон стартует);
     * 2 — Termux-файлы присутствуют, но порта нет (установка/демон не поднялся);
     * 3 — Termux не установлен.
     */
    fun probeHermesEnvironment(context: Context, restUrl: String, token: String, callback: (Int) -> Unit) {
        Thread {
            val level: Int = when {
                // 0: REST health-check демона (любой HTTP-ответ = демон жив)
                runCatching {
                    val url = java.net.URL(restUrl.trimEnd('/') + "/status")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 2500
                    conn.readTimeout = 2500
                    conn.setRequestProperty("X-Hermes-Session-Token", token)
                    try {
                        conn.responseCode
                    } finally {
                        conn.disconnect()
                    }
                }.isSuccess -> 0

                // 1: TCP-порт демона открыт (демон стартует, REST ещё не поднялся)
                runCatching {
                    val socket = java.net.Socket()
                    try {
                        socket.connect(java.net.InetSocketAddress("127.0.0.1", 8000), 2000)
                        true
                    } finally {
                        socket.close()
                    }
                }.getOrDefault(false) -> 1

                // 2: Termux установлен, но демона нет
                isTermuxInstalled(context) -> 2

                else -> 3
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post { callback(level) }
        }.start()
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
     * Открывает системные настройки приложения для управления разрешениями (RUN_COMMAND и др.)
     */
    fun openAppPermissions(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
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
