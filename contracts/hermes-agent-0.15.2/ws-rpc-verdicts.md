# hermes-agent 0.15.2 — верификация WS-RPC stopGeneration (D8)

> Источник: прямой WS-зонд к демону через adb forward (raw-socket handshake + JSON-RPC),
> 2026-10-26. Проба: session_id="probe_nonexistent" (существующая сессия не нужна для
> проверки существования метода). Ответы демона — дословные.

## Вердикты

| RPC (клиент отправляет) | Ответ демона | Статус метода |
|---|---|---|
| `session.interrupt` | `{"jsonrpc":"2.0","id":N,"error":{"code":4001,"message":"session not found"}}` | **СУЩЕСТВУЕТ** — 4001 = семантическая ошибка (валидный метод, сессии нет). Для реальной сессии — прерывает генерацию |
| `prompt.cancel` | `{"error":{"code":-32601,"message":"unknown method: prompt.cancel"}}` | **НЕ СУЩЕСТВУЕТ** |
| `session.abort` | `{"error":{"code":-32601,"message":"unknown method: session.abort"}}` | **НЕ СУЩЕСТВУЕТ** |
| `chat.abort` | `{"error":{"code":-32601,"message":"unknown method: chat.abort"}}` | **НЕ СУЩЕСТВУЕТ** |

## Сопутствующие факты

- Handshake: `GET /api/ws?token=hermes_mobile_local` + `Sec-WebSocket-Protocol: hermes.jsonrpc.v1` → 101 (query-токен работает, header — нет, см. 08 §2.5/G3).
- Подключение немедленно получает event `gateway.ready` (skin/config payload) — клиент корректно игнорирует незнакомые поля.
- JSON-RPC error-коды демона: 4001 (session not found), -32601 (unknown method, стандартный JSON-RPC).

## Approval payload (risk-поля)

Формат `approval.pending`/`approval.received` — по полевым данным (08 §2.5, flat-ветка
`ToolApprovalRequestDto` с `@SerialName("risk_level")`); полная верификация — при первом
реальном approval на устройстве (демон не воспроизводит событие без tool-вызова).
Клиент парсит кандидаты: `risk_level` | `risk` | `level` (строка lowercase:
critical | medium | low; отсутствие поля → LOW, не CRITICAL — реализовано 2026-10-26, 07 §G9).

## Следствие для клиента (реализовано 2026-10-26)

`stopGeneration` сокращён до единственного проверенного RPC `session.interrupt`
(+ локальный флаг остановки UI — сохранился). Залп из 4 RPC удалён.
