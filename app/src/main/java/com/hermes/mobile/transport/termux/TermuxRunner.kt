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
    // без них dashboard падает с 500 на каждом запросе), включающий фиксацию сессионного токена
    // через переменную окружения HERMES_DASHBOARD_SESSION_TOKEN и добавляющий локальные эндпоинты
    // для двустороннего обмена медиафайлами: POST /api/attachments/upload и GET /api/files/download (G15, G16),
    // а также глобальную системную изоляцию IPv4, авто-ретрай 503/429 (sitecustomize.py) в системном site-packages (G19, 05 §D2),
    // автоматическую подгрузку ~/.hermes/.env в os.environ и перехват Bearer no-key-required (G22, 05 §D3).
    const val FIX_SCRIPT = "import os\nimport sys\n\n# --- 1. GLOBAL FORCE IPV4, MULTI-KEY ENV & RESILIENT RETRY (sitecustomize.py & config.yaml) ---\nSC_CODE = '''import socket as _socket\nimport os as _os\n\n# Auto-load ~/.hermes/.env to os.environ (G22)\ntry:\n    _env_path = _os.path.expanduser('~/.hermes/.env')\n    if _os.path.exists(_env_path):\n        with open(_env_path, 'r') as _f:\n            for _line in _f:\n                _line = _line.strip()\n                if _line and not _line.startswith('#') and '=' in _line:\n                    _k, _v = _line.split('=', 1)\n                    _k = _k.strip()\n                    _v = _v.strip().strip(\\'\\\"\\')\n                    if _k and (_k not in _os.environ or not _os.environ[_k]):\n                        _os.environ[_k] = _v\nexcept Exception:\n    pass\n\ntry:\n    _orig_getaddrinfo = _socket.getaddrinfo\n    def _v4_only_getaddrinfo(host, port, family=0, type=0, proto=0, flags=0):\n        if family in (0, getattr(_socket, 'AF_UNSPEC', 0), getattr(_socket, 'AF_INET6', 10)):\n            return _orig_getaddrinfo(host, port, _socket.AF_INET, type, proto, flags)\n        return _orig_getaddrinfo(host, port, family, type, proto, flags)\n    _socket.getaddrinfo = _v4_only_getaddrinfo\nexcept Exception:\n    pass\n\ntry:\n    import urllib3.util.connection as _u3c\n    _u3c.allowed_gai_family = lambda: _socket.AF_INET\nexcept Exception:\n    pass\n\ntry:\n    import httpx as _httpx\n    def _intercept_no_key(request):\n        try:\n            _auth_hdr = request.headers.get('authorization', '')\n            if 'no-key-required' in _auth_hdr.lower():\n                _repl_key = _os.environ.get('OPENAI_API_KEY') or _os.environ.get('CUSTOM_API_KEY')\n                if not _repl_key:\n                    for _ek, _ev in _os.environ.items():\n                        if _ek.startswith('HERMES_CUSTOM_') and _ek.endswith('_API_KEY') and _ev:\n                            _repl_key = _ev\n                            break\n                if _repl_key:\n                    request.headers['authorization'] = 'Bearer ' + _repl_key\n        except Exception:\n            pass\n\n    _orig_client_send = _httpx.Client.send\n    def _retry_send(self, request, *args, **kwargs):\n        import time\n        _intercept_no_key(request)\n        delays = [2.0, 4.0, 8.0]\n        attempt = 0\n        while True:\n            resp = _orig_client_send(self, request, *args, **kwargs)\n            if resp.status_code in (429, 503) and attempt < len(delays):\n                try:\n                    bt = resp.text.lower()\n                except Exception:\n                    bt = ''\n                if any(k in bt for k in ('maintenance', 'source_temporarily_unavailable', 'короткое обновление', 'upstream_error')):\n                    time.sleep(delays[attempt])\n                    attempt += 1\n                    continue\n            return resp\n    _httpx.Client.send = _retry_send\n\n    _orig_async_send = _httpx.AsyncClient.send\n    async def _async_retry_send(self, request, *args, **kwargs):\n        import asyncio\n        _intercept_no_key(request)\n        delays = [2.0, 4.0, 8.0]\n        attempt = 0\n        while True:\n            resp = await _orig_async_send(self, request, *args, **kwargs)\n            if resp.status_code in (429, 503) and attempt < len(delays):\n                try:\n                    bt = resp.text.lower()\n                except Exception:\n                    bt = ''\n                if any(k in bt for k in ('maintenance', 'source_temporarily_unavailable', 'короткое обновление', 'upstream_error')):\n                    await asyncio.sleep(delays[attempt])\n                    attempt += 1\n                    continue\n            return resp\n    _httpx.AsyncClient.send = _async_retry_send\nexcept Exception:\n    pass\n'''\n\ntarget_sc_dirs = []\ntry:\n    import site\n    for sp in site.getsitepackages():\n        target_sc_dirs.append(sp)\nexcept Exception:\n    pass\ntarget_sc_dirs.extend([\n    os.path.expanduser('~/.hermes/force-ipv4'),\n    os.path.expanduser('~/.local/lib/python3.14/site-packages'),\n    os.path.expanduser('~/.local/lib/python3.12/site-packages'),\n    '/data/data/com.termux/files/usr/lib/python3.14/site-packages',\n    '/data/data/com.termux/files/usr/lib/python3.12/site-packages'\n])\n\nfor d in target_sc_dirs:\n    try:\n        if os.path.exists(d) or 'force-ipv4' in d:\n            os.makedirs(d, exist_ok=True)\n            p = os.path.join(d, 'sitecustomize.py')\n            with open(p, 'w') as fh:\n                fh.write(SC_CODE)\n    except Exception:\n        pass\n\ntry:\n    cfg_path = os.path.expanduser('~/.hermes/config.yaml')\n    if os.path.exists(cfg_path):\n        with open(cfg_path, 'r') as fh:\n            cfg_txt = fh.read()\n        if 'force_ipv4: false' in cfg_txt:\n            cfg_txt = cfg_txt.replace('force_ipv4: false', 'force_ipv4: true')\n            with open(cfg_path, 'w') as fh:\n                fh.write(cfg_txt)\n        elif 'network:' in cfg_txt and 'force_ipv4' not in cfg_txt:\n            cfg_txt = cfg_txt.replace('network:', 'network:\\n  force_ipv4: true')\n            with open(cfg_path, 'w') as fh:\n                fh.write(cfg_txt)\nexcept Exception:\n    pass\n\ntry:\n    gbn_path = os.path.expanduser('~/.hermes/scripts/gateway_boot_net.sh')\n    if os.path.exists(gbn_path):\n        with open(gbn_path, 'r') as fh:\n            gbn_txt = fh.read()\n        if 'PYTHONPATH=' not in gbn_txt and 'hermes gateway run' in gbn_txt:\n            gbn_txt = gbn_txt.replace('hermes gateway run', 'PYTHONPATH=\"\\\$HOME/.hermes/force-ipv4:\\\$PYTHONPATH\" hermes gateway run')\n            with open(gbn_path, 'w') as fh:\n                fh.write(gbn_txt)\nexcept Exception:\n    pass\n\ntarget_dirs = []\ntry:\n    import hermes_cli\n    target_dirs.append(os.path.dirname(os.path.abspath(hermes_cli.__file__)))\nexcept Exception:\n    pass\n\ntry:\n    import site\n    for sp in site.getsitepackages():\n        target_dirs.append(os.path.join(sp, 'hermes_cli'))\nexcept Exception:\n    pass\n\ntarget_dirs.append(os.path.expanduser('~/.local/lib/python3.14/site-packages/hermes_cli'))\ntarget_dirs.append(os.path.expanduser('~/.local/lib/python3.12/site-packages/hermes_cli'))\ntarget_dirs.append('/data/data/com.termux/files/usr/lib/python3.14/site-packages/hermes_cli')\ntarget_dirs.append('/data/data/com.termux/files/usr/lib/python3.12/site-packages/hermes_cli')\n\nseen = set()\nunique_dirs = []\nfor d in target_dirs:\n    if d and d not in seen:\n        seen.add(d)\n        unique_dirs.append(d)\n\nMODULES = {\n    '__init__.py': '''\"\"\"Dashboard auth provider registry.\"\"\"\n\n\n_PROVIDERS = []\n\n\nclass DashboardAuthProvider:\n    name = \"base\"\n    display_name = \"Base Provider\"\n\n\ndef register_provider(provider):\n    if not any(getattr(p, \"name\", \"?\") == provider.name for p in _PROVIDERS):\n        _PROVIDERS.append(provider)\n\n\ndef list_providers():\n    return list(_PROVIDERS)\n''',\n    'middleware.py': '''async def gated_auth_middleware(request, call_next):\n    return await call_next(request)\n''',\n    'routes.py': '''from fastapi import APIRouter\n\nrouter = APIRouter()\n''',\n    'prefix.py': '''def normalise_prefix(raw):\n    if not raw:\n        return \"\"\n    p = raw.strip()\n    if not p:\n        return \"\"\n    if not p.startswith(\"/\"):\n        p = \"/\" + p\n    return p.rstrip(\"/\")\n''',\n    'audit.py': '''from enum import Enum\n\n\nclass AuditEvent(Enum):\n    WS_TICKET_REJECTED = \"ws_ticket_rejected\"\n\n\ndef audit_log(event, **kwargs):\n    return None\n''',\n    'ws_tickets.py': '''class TicketInvalid(Exception):\n    pass\n\n\ndef consume_ticket(ticket):\n    raise TicketInvalid(\"no ticket store configured\")\n''',\n}\n\nfor base_dir in unique_dirs:\n    ws_path = os.path.join(base_dir, 'web_server.py')\n    if not os.path.exists(ws_path):\n        continue\n\n    da = os.path.join(base_dir, 'dashboard_auth')\n    os.makedirs(da, exist_ok=True)\n    for name, content in MODULES.items():\n        p = os.path.join(da, name)\n        if not os.path.exists(p) or os.path.getsize(p) == 0:\n            with open(p, 'w') as fh:\n                fh.write(content)\n\n    with open(ws_path) as fh:\n        src = fh.read()\n    modified = False\n    if 'HERMES_DASHBOARD_SESSION_TOKEN' not in src:\n        import re\n        patched = re.sub(\n            r'(_SESSION_TOKEN\\s*=\\s*)secrets\\.token_urlsafe\\(\\s*32\\s*\\)',\n            r'\\1os.environ.get(\"HERMES_DASHBOARD_SESSION_TOKEN\") or secrets.token_urlsafe(32)',\n            src,\n            count=1,\n        )\n        if patched != src:\n            src = patched\n            modified = True\n    if '# --- HERMES MOBILE ATTACHMENTS' in src:\n        src = src.split('# --- HERMES MOBILE ATTACHMENTS')[0].rstrip()\n        modified = True\n    extra = '''\\n\\n# --- HERMES MOBILE ATTACHMENTS & MEDIA (G15, G16) ---\\ntry:\\\\n    from fastapi import Request as _Req, HTTPException as _HTTPEx\\\\n    from fastapi.responses import Response as _FResp\\\\n    import base64 as _b64\\\\n    import mimetypes as _mimetypes\\\\n\\\\n    try:\\\\n        _PUBLIC_API_PATHS = frozenset(set(_PUBLIC_API_PATHS) | {\"/api/attachments/upload\", \"/api/files/download\"})\\\\n    except Exception:\\\\n        pass\\\\n\\\\n    @app.post(\"/api/attachments/upload\")\\\\n    async def _hermes_upload_attachment(req: _Req):\\\\n        try:\\\\n            body = await req.json()\\\\n            name = body.get(\"name\") or \"attachment.bin\"\\\\n            data_url = body.get(\"data_url\") or \"\"\\\\n            if \",\" in data_url:\\\\n                data_url = data_url.split(\",\", 1)[1]\\\\n            raw_bytes = _b64.b64decode(data_url)\\\\n            target_dir = os.path.expanduser(\"~/.hermes/attachments\")\\\\n            os.makedirs(target_dir, exist_ok=True)\\\\n            target_path = os.path.join(target_dir, name)\\\\n            with open(target_path, \"wb\") as f:\\\\n                f.write(raw_bytes)\\\\n            return {\"status\": \"ok\", \"name\": name, \"path\": f\".hermes/attachments/{name}\", \"full_path\": target_path, \"size\": len(raw_bytes)}\\\\n        except Exception as e:\\\\n            raise _HTTPEx(status_code=400, detail=str(e))\\\\n\\\\n    @app.get(\"/api/files/download\")\\\\n    async def _hermes_download_file(path: str):\\\\n        clean_path = path.strip()\\\\n        candidates = []\\\\n        if clean_path.startswith(\"~\"):\\\\n            candidates.append(os.path.expanduser(clean_path))\\\\n        elif not os.path.isabs(clean_path):\\\\n            candidates.append(os.path.expanduser(os.path.join(\"~\", clean_path)))\\\\n            candidates.append(os.path.join(os.getcwd(), clean_path))\\\\n        else:\\\\n            candidates.append(clean_path)\\\\n        base = os.path.basename(clean_path)\\\\n        candidates.append(os.path.expanduser(os.path.join(\"~/.hermes/attachments\", base)))\\\\n        candidates.append(os.path.expanduser(os.path.join(\"~/.hermes\", base)))\\\\n        candidates.append(os.path.expanduser(os.path.join(\"~/hermes-media\", base)))\\\\n        candidates.append(os.path.expanduser(os.path.join(\"~\", base)))\\\\n        candidates.append(os.path.expanduser(os.path.join(\"~/storage/shared/Download\", base)))\\\\n        candidates.append(os.path.join(\"/sdcard/Download\", base))\\\\n        candidates.append(os.path.join(\"/sdcard\", base))\\\\n        found_path = None\\\\n        for cp in candidates:\\\\n            if cp and os.path.isfile(cp):\\\\n                found_path = cp\\\\n                break\\\\n        if not found_path:\\\\n            raise _HTTPEx(status_code=404, detail=f\"File not found: {path}\")\\\\n        media_type, _ = _mimetypes.guess_type(found_path)\\\\n        if not media_type:\\\\n            ext = os.path.splitext(found_path)[1].lower()\\\\n            if ext in (\".md\", \".markdown\"):\\\\n                media_type = \"text/markdown; charset=utf-8\"\\\\n            elif ext in (\".txt\", \".log\"):\\\\n                media_type = \"text/plain; charset=utf-8\"\\\\n            elif ext == \".json\":\\\\n                media_type = \"application/json\"\\\\n            else:\\\\n                media_type = \"application/octet-stream\"\\\\n        with open(found_path, \"rb\") as f:\\\\n            data = f.read()\\\\n        return _FResp(content=data, media_type=media_type)\\\\n\\\\n    for _r in list(app.router.routes):\\\\n        if getattr(_r, \"path\", \"\") in (\"/api/attachments/upload\", \"/api/files/download\"):\\\\n            app.router.routes.remove(_r)\\\\n            app.router.routes.insert(0, _r)\\\\nexcept Exception:\\\\n    pass\\\\n'''\\n    src = src + extra\n    modified = True\n    if modified:\n        with open(ws_path, 'w') as fh:\n            fh.write(src)\n"

    const val FIX_SCRIPT_PATH = "~/.hermes_mobile_fix.py"

    /**
     * G15 (docs/08 §2.8): preseed с реальной первичной конфигурацией, собранной
     * в онбординге. Схема валидатора hermes (config.py validate_config_structure):
     * model — СЕКЦИЯ (provider/default/base_url), не строка; провайдер —
     * providers.<name> с base_url/key_env/model. .env хранит ключ (key_env).
     * Демон стартует неинтерактивно с рабочим провайдером.
     */
    /**
     * G15 (docs/08 §2.8), G22 (docs/05 §Zero-ADB, docs/08 §2.12): multi-key preseed с конфигурацией
     * провайдера, собранной в онбординге.
     * Записывает в ~/.hermes/.env и ~/.bashrc:
     * - HERMES_CUSTOM_<NAME>_API_KEY (для key_env в config.yaml)
     * - CUSTOM_API_KEY
     * - OPENAI_API_KEY
     * - OPENAI_BASE_URL
     * - OPENAI_API_BASE
     * Предотвращает ошибку 401 Unauthorized и fallback на Bearer no-key-required.
     */
    fun buildPreseedCommand(providerName: String, baseUrl: String, apiKey: String, model: String): String {
        val safeName = providerName.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "_").ifBlank { "custom" }
        val envKey = "HERMES_CUSTOM_${safeName.uppercase()}_API_KEY"
        val cleanBaseUrl = baseUrl.trim()
        val cleanApiKey = apiKey.trim()
        val cleanModel = model.trim().ifBlank { safeName }

        return "mkdir -p ~/.hermes && cat > ~/.hermes/config.yaml << 'CFGEOF'\n" +
            "model:\n" +
            "  provider: custom\n" +
            "  default: \"$cleanModel\"\n" +
            "  base_url: \"$cleanBaseUrl\"\n" +
            "providers:\n" +
            "  $safeName:\n" +
            "    base_url: \"$cleanBaseUrl\"\n" +
            "    key_env: \"$envKey\"\n" +
            "    model: \"$cleanModel\"\n" +
            "CFGEOF\n" +
            "touch ~/.hermes/.env && " +
            "(grep -v -E '^($envKey|CUSTOM_API_KEY|OPENAI_API_KEY|OPENAI_BASE_URL|OPENAI_API_BASE)=' ~/.hermes/.env 2>/dev/null || true) > ~/.hermes/.env.tmp && " +
            "mv ~/.hermes/.env.tmp ~/.hermes/.env && " +
            "cat >> ~/.hermes/.env << 'ENVEOF'\n" +
            "$envKey=\"$cleanApiKey\"\n" +
            "CUSTOM_API_KEY=\"$cleanApiKey\"\n" +
            "OPENAI_API_KEY=\"$cleanApiKey\"\n" +
            "OPENAI_BASE_URL=\"$cleanBaseUrl\"\n" +
            "OPENAI_API_BASE=\"$cleanBaseUrl\"\n" +
            "ENVEOF\n" +
            "(grep -q 'set -a; . \"\$HOME/.hermes/.env\"' ~/.bashrc 2>/dev/null || cat >> ~/.bashrc << 'BASHCEOF'\n" +
            "if [ -f \"\$HOME/.hermes/.env\" ]; then set -a; . \"\$HOME/.hermes/.env\"; set +a; fi\n" +
            "BASHCEOF\n) && " +
            "echo '[preseed] config.yaml + multi-key .env written for provider $safeName'"
    }

    /**
     * G15 (docs/08 §2.8): команда установки с конфигурацией провайдера из
     * онбординга — preseed формирует config.yaml + .env ДО первого старта,
     * демон стартует неинтерактивно с рабочим провайдером.
     */
    fun buildSetupCommandWithProvider(name: String, baseUrl: String, apiKey: String, model: String): String {
        val preseed = buildPreseedCommand(name, baseUrl, apiKey, model)
        return SETUP_COMMAND.replace("PRESEED_PLACEHOLDER", preseed)
    }

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
    // G23 (D18): chromium исключён из pkg install, в pip добавлены 'uvicorn[standard]' websockets httpx python-dotenv,
    // CARGO_BUILD_JOBS=4 для быстрой компиляции.
    const val SETUP_COMMAND = "termux-wake-lock; mkdir -p ~/.termux && (grep -q 'allow-external-apps=true' ~/.termux/termux.properties 2>/dev/null || echo 'allow-external-apps=true' >> ~/.termux/termux.properties) && termux-reload-settings; echo '[1/6] Properties OK'; (grep -q 'HERMES_DASHBOARD_SESSION_TOKEN' ~/.bashrc 2>/dev/null || echo 'export HERMES_DASHBOARD_SESSION_TOKEN=\"hermes_mobile_local\"' >> ~/.bashrc) && (grep -q 'HERMES_DASHBOARD_TUI' ~/.bashrc 2>/dev/null || echo 'export HERMES_DASHBOARD_TUI=1' >> ~/.bashrc) && (grep -q 'set -a; . \"\$HOME/.hermes/.env\"' ~/.bashrc 2>/dev/null || echo 'if [ -f \"\$HOME/.hermes/.env\" ]; then set -a; . \"\$HOME/.hermes/.env\"; set +a; fi' >> ~/.bashrc); echo '[2/6] bashrc OK'; (pkg update -y || (echo 'default mirrors unreachable, switching...'; (" + MIRROR_FALLBACK + ") || echo '[!] all mirrors FAILED - check internet, then rerun')); echo '[3/6] apt update done'; pkg install -y python python-pip python-psutil git curl coreutils procps rust binutils clang || echo '[!] pkg install FAILED - rerun this command later'; echo '[4/6] packages done'; if ! command -v python3 >/dev/null 2>&1; then echo '[!] python3 missing - run: pkg install python, then rerun'; elif ! python3 -m pip --version >/dev/null 2>&1; then echo '[!] pip module missing - run: pkg install python-pip, then rerun'; else export CARGO_BUILD_JOBS=4; echo 'installing hermes-agent (may compile Rust deps 10-40 min, keep screen on)...'; if python3 -m pip install --no-cache-dir 'hermes-agent[termux]' 'uvicorn[standard]' fastapi aiofiles websockets httpx python-dotenv jiter 2>&1 | tail -8; then cat > ~/.hermes_mobile_fix.py << 'PYEOF'\n" + FIX_SCRIPT + "PYEOF\npython3 ~/.hermes_mobile_fix.py && echo '[5/6] hermes-agent installed'; else echo '[!] pip install hermes-agent FAILED - see error above, rerun this command (safe)'; fi; fi; echo '[6/6] starting daemon...'; (PRESEED_PLACEHOLDER); if command -v hermes >/dev/null 2>&1; then IMPORT_ERR=\$(python3 -c 'import fastapi, uvicorn, websockets, httpx, dotenv, jiter, pydantic' 2>&1 | grep -oE \"No module named '[^']+'\" | head -1); if [ -n \"\$IMPORT_ERR\" ]; then echo \"[!] missing \$IMPORT_ERR - run: python3 -m pip install 'uvicorn[standard]' fastapi aiofiles websockets httpx python-dotenv jiter, then rerun\"; else export PYTHONPATH=\"\$HOME/.hermes/force-ipv4:\$PYTHONPATH\"; export HERMES_DASHBOARD_SESSION_TOKEN=\"hermes_mobile_local\"; export HERMES_DASHBOARD_TUI=1; if [ -f \"\$HOME/.hermes/.env\" ]; then set -a; . \"\$HOME/.hermes/.env\"; set +a; fi; if [ -f \"\$HOME/.hermes/scripts/gateway_boot_net.sh\" ]; then nohup bash \"\$HOME/.hermes/scripts/gateway_boot_net.sh\" >/dev/null 2>&1 & fi; pkill -f '[h]ermes dashboard' 2>/dev/null; sleep 1; (HERMES_CMD=\"dashboard\"; hermes \$HERMES_CMD --port 8000 --host 127.0.0.1 --no-open --skip-build > /data/data/com.termux/files/home/hermes_mobile.log 2>&1 &) && disown -a 2>/dev/null && echo 'DONE - daemon starting, return to the app'; fi; else echo '[!] hermes not installed - resolve [!] steps above and rerun this command'; fi"

    // Команда штатного запуска агента с экспортом переменных TUI, сессионного токена и ключей .env.
    // ВАЖНО: pkill с паттерном '[h]ermes dashboard' — квадратные скобки не дают pkill -f
    // сматчить собственный bash-процесс (в его cmdline паттерн записан как '[h]ermes',
    // а не 'hermes'), иначе команда убивает сама себя до старта демона.
    const val LAUNCH_COMMAND = "export PATH=\"\$HOME/.local/bin:/data/data/com.termux/files/usr/bin:\$PATH\"; export PYTHONPATH=\"\$HOME/.hermes/force-ipv4:\$PYTHONPATH\"; export HERMES_DASHBOARD_TUI=1; export HERMES_DASHBOARD_SESSION_TOKEN=\"hermes_mobile_local\"; if [ -f \"\$HOME/.hermes/.env\" ]; then set -a; . \"\$HOME/.hermes/.env\"; set +a; fi; termux-wake-lock; if [ -f \"\$HOME/.hermes/scripts/gateway_boot_net.sh\" ]; then nohup bash \"\$HOME/.hermes/scripts/gateway_boot_net.sh\" >/dev/null 2>&1 & fi; pkill -f '[h]ermes dashboard' 2>/dev/null; sleep 1; hermes dashboard --port 8000 --host 127.0.0.1 --no-open --skip-build"

    // Фоновая команда с перезапуском при вызове (pkill старых процессов), проверкой наличия пакета,
    // применением патча совместимости и чистым стартом в фоне через subshell & disown.
    // pkill использует '[h]ermes' трюк против самоубийства команды (см. LAUNCH_COMMAND).
    // G14: pkg-шаги отделены (сбой установки не блокирует запуск уже установленного hermes),
    // wake-lock держит установку живой.
    // G22, G23: очистка от chromium, добавление websockets/httpx/python-dotenv, загрузка .env.
    const val BACKGROUND_DAEMON_CMD = "export PATH=\"\$HOME/.local/bin:/data/data/com.termux/files/usr/bin:\$PATH\"; export PYTHONPATH=\"\$HOME/.hermes/force-ipv4:\$PYTHONPATH\"; export HERMES_DASHBOARD_TUI=1; export HERMES_DASHBOARD_SESSION_TOKEN=\"hermes_mobile_local\"; if [ -f \"\$HOME/.hermes/.env\" ]; then set -a; . \"\$HOME/.hermes/.env\"; set +a; fi; termux-wake-lock; if [ -f \"\$HOME/.hermes/scripts/gateway_boot_net.sh\" ]; then nohup bash \"\$HOME/.hermes/scripts/gateway_boot_net.sh\" >/dev/null 2>&1 & fi; if ! command -v hermes > /dev/null 2>&1; then (pkg update -y || (" + MIRROR_FALLBACK + ")) && pkg install -y python python-pip python-psutil git curl coreutils procps rust binutils clang && CARGO_BUILD_JOBS=4 python3 -m pip install --no-cache-dir 'hermes-agent[termux]' 'uvicorn[standard]' fastapi aiofiles websockets httpx python-dotenv jiter || echo '[!] install failed - open Termux and run setup again'; fi; if command -v hermes >/dev/null 2>&1; then if [ ! -f ~/.hermes/config.yaml ]; then mkdir -p ~/.hermes; printf 'model: \\\"\\\"\\nproviders: {}\\n' > ~/.hermes/config.yaml; fi; cat > ~/.hermes_mobile_fix.py << 'PYEOF'\n" + FIX_SCRIPT + "PYEOF\npython3 ~/.hermes_mobile_fix.py 2>/dev/null; pkill -f '[h]ermes dashboard' 2>/dev/null; sleep 1; (HERMES_CMD=\"dashboard\"; hermes \$HERMES_CMD --port 8000 --host 127.0.0.1 --no-open --skip-build > /data/data/com.termux/files/home/hermes_mobile.log 2>&1 &) && disown -a 2>/dev/null; else echo '[!] hermes not installed - run the setup command in the wizard first'; fi"

    // Команда обновления Hermes Agent в Termux в один клик
    // G22, G23: чистый стек пакетов и загрузка .env
    const val UPDATE_COMMAND = "export PATH=\"\$HOME/.local/bin:/data/data/com.termux/files/usr/bin:\$PATH\"; export PYTHONPATH=\"\$HOME/.hermes/force-ipv4:\$PYTHONPATH\"; export HERMES_DASHBOARD_TUI=1; export HERMES_DASHBOARD_SESSION_TOKEN=\"hermes_mobile_local\"; if [ -f \"\$HOME/.hermes/.env\" ]; then set -a; . \"\$HOME/.hermes/.env\"; set +a; fi; termux-wake-lock; if [ -f \"\$HOME/.hermes/scripts/gateway_boot_net.sh\" ]; then nohup bash \"\$HOME/.hermes/scripts/gateway_boot_net.sh\" >/dev/null 2>&1 & fi; CARGO_BUILD_JOBS=4 python3 -m pip install --no-cache-dir --upgrade 'hermes-agent[termux]' 'uvicorn[standard]' fastapi aiofiles websockets httpx python-dotenv jiter && cat > ~/.hermes_mobile_fix.py << 'PYEOF'\n" + FIX_SCRIPT + "PYEOF\npython3 ~/.hermes_mobile_fix.py && pkill -f '[h]ermes dashboard' 2>/dev/null; sleep 1; (HERMES_CMD=\"dashboard\"; hermes \$HERMES_CMD --port 8000 --host 127.0.0.1 --no-open --skip-build > /data/data/com.termux/files/home/hermes_mobile.log 2>&1 &) && disown -a 2>/dev/null"

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
     * G14 (docs/08 §2.7): хвост лога демона для изолированных устройств —
     * единственный канал диагностики, когда ни Termux, ни adb недоступны.
     * null = лога нет/не читается (диагноз: демон не стартовал вовсе).
     */
    fun readDaemonLogTail(maxLines: Int = 20): String? {
        return try {
            val f = java.io.File("/data/data/com.termux/files/home/hermes_mobile.log")
            if (!f.exists()) null else {
                val lines = f.readLines()
                if (lines.isEmpty()) "(лог пуст)" else lines.takeLast(maxLines).joinToString("\n")
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * G14/G15 (полевой тест 27.10): полный диагностический снимок окружения —
     * единственный надёжный канал на изолированном устройстве. Пользователь
     * переписывает/фотографирует один блок текста вместо десяти ручных команд.
     * Собирает: конфиг демона, .env-ключи (имена), статусы процессов, порт,
     * список пакетов, ошибки лога.
     */
    fun collectDiagnosticsSnapshot(): String {
        val sb = StringBuilder()
        val home = "/data/data/com.termux/files/home"
        val envFile = java.io.File("/data/data/com.termux/files/home/.hermes/.env")
        val cfgFile = java.io.File("/data/data/com.termux/files/home/.hermes/config.yaml")
        val logFile = java.io.File("$home/hermes_mobile.log")

        sb.appendLine("=== HERMES MOBILE DIAGNOSTICS ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())} ===")

        // 1. Конфиг демона (ключи не показываем — имена env-ключей только)
        sb.appendLine("--- config.yaml ---")
        sb.appendLine(
            runCatching {
                if (cfgFile.exists()) cfgFile.readText().take(1500) else "(missing)"
            }.getOrDefault("(unreadable)")
        )

        // 2. .env — только имена ключей
        sb.appendLine("--- .env keys ---")
        sb.appendLine(
            runCatching {
                if (envFile.exists()) {
                    envFile.readLines().mapNotNull { l ->
                        l.substringBefore('=').takeIf { it.isNotBlank() && !it.startsWith("#") }
                    }.joinToString(", ").ifBlank { "(empty)" }
                } else "(missing)"
            }.getOrDefault("(unreadable)")
        )

        // 3. Процессы демона
        sb.appendLine("--- processes (hermes/python) ---")
        sb.appendLine(
            runCatching {
                val p = ProcessBuilder("sh", "-c", "ps -A | grep -iE 'hermes|python' | head -10")
                    .redirectErrorStream(true).start()
                p.inputStream.bufferedReader().readText().trim()
            }.getOrDefault("(ps failed)")
        )

        // 4. Порт 8000
        sb.appendLine("--- port 8000 ---")
        sb.appendLine(
            runCatching {
                val s = java.net.Socket()
                try {
                    s.connect(java.net.InetSocketAddress("127.0.0.1", 8000), 1500)
                    "OPEN"
                } finally {
                    s.close()
                }
                "OPEN"
            }.recover { "CLOSED (${it.javaClass.simpleName})" }.getOrDefault("CLOSED")
        )

        // 5. REST-ответ (без токена — статус видно и так)
        sb.appendLine("--- REST /status ---")
        sb.appendLine(
            runCatching {
                val conn = java.net.URL("http://127.0.0.1:8000/api/status").openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 2000; conn.readTimeout = 2000
                try { "HTTP ${conn.responseCode}" } finally { conn.disconnect() }
            }.recover { "FAIL: ${it.javaClass.simpleName}: ${it.message?.take(80)}" }.getOrDefault("FAIL")
        )

        // 6. Хвост лога демона
        sb.appendLine("--- daemon log tail (~30 lines) ---")
        sb.appendLine(readDaemonLogTail(30) ?: "(no log file)")

        sb.appendLine("=== END DIAGNOSTICS ===")
        return sb.toString()
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
