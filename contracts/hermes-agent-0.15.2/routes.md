# hermes-agent 0.15.2 — маршрутный реестр

> Источник: живой демон на устройстве (Xiaomi, Termux), снят 2026-10-23, подтверждён 2026-10-26.
> Снимок: `/data/local/tmp/all_routes.txt` (adb shell cat), raw: `routes_raw.txt` рядом.
> Машинный источник схем: `openapi.json` (GET /openapi.json, 91 path; REST-часть реестра).

## Итого

118 маршрутов (REST + 5 WS), из них:

- REST API: ~110 маршрутов (сессии, env, config, model, OAuth, cron, profiles, skills, tools, analytics, dashboard, plugins)
- WS: `/api/ws` (gateway_ws — основной чат-канал), `/api/events` (events_ws), `/api/pty` (pty_ws), `/api/pub` (pub_ws), `/api/plugins/kanban/events` (stream_events)
- Служебные: `/openapi.json`, `/docs`, `/redoc`, `/assets/*`, SPA catch-all `/{full_path:path}`

## Ключевые для мобильного клиента

| Маршрут | Назначение (клиент) |
|---|---|
| GET /api/status | health/версия — runtime-детектор рассинхрона (03 §D7) |
| GET/POST /api/sessions; GET /api/sessions/{id}; GET /api/sessions/{id}/messages; DELETE /api/sessions/{id} | сессии + replay-восстановление (03 §D9б) |
| GET/PUT /api/config; GET /api/config/raw | конфиг: провайдеры, key_env (03 §D4) |
| GET/PUT/DELETE /api/env; POST /api/env/reveal | ключи провайдеров (08 §2.5) |
| GET /api/model/info; GET /api/model/options; POST /api/model/set | модель (08 §2.5) |
| GET /api/health-подобные /api/status | диагностика |
| WS /api/ws | JSON-RPC чат (03 §D1) |

## Полный реестр

См. `routes_raw.txt` (raw-выгрузка `app.routes` демона; 119 строк с заголовком).

Снято при: hermes-agent 0.15.2 + FIX_SCRIPT (патч совместимости dashboard_auth), Python 3.14.8, port 8000 loopback.
