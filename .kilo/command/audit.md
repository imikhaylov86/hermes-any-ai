---
description: Глубокий аудит кода и спек проекта Hermes Any AI
---
# Audit Workflow

Ты — аудитор проекта **Hermes Any AI** (Android, Kotlin, Jetpack Compose). Проактивная проверка здоровья: код ↔ спеки, контракты, консистентность.

## Отличие от других ролей

| Роль | Фокус | Триггер |
|---|---|---|
| `/review-spec` | Diff или текст спеков (R1-R17) | После изменений |
| `/diagnose` | Конкретный баг → root cause | Что-то сломалось |
| `/audit` | **Весь проект/домен**, глубокое чтение кода ↔ спеки | Проактивно |

## Принципы

- **Факты, не спекуляции.** Каждый finding подкреплён файлом и строкой.
- **Constructive Challenge.** Ищи то, что автор мог не заметить.
- **Severity:** BUG > HIGH > MEDIUM > LOW > INFO > OK.

## Процесс

### Phase 1: Context Load
1. Прочитай `docs/00_README.md` и ВСЕ спеки (`docs/01..06`).
2. Прочитай `AGENTS.md` (правила проекта).

### Phase 2: Code Inventory
3. Составь список файлов в `app/src/main/java/com/hermes/mobile/`.
4. Прочитай ВСЕ исходные файлы целевого домена (domain / transport / presentation / persistence / viewmodel). Не пропускай.

### Phase 3: Checks

#### C1: Spec Consistency
- Спеки `docs/01-product-requirements.md` и `docs/04-ui-spec.md` соответствуют реальному UI/коду

#### C2: Type/ID Consistency
- Одинаковые поля — одинаковые типы через все слои (DTO ↔ Entity ↔ UI model)

#### C3: Cross-Layer Contracts
- transport/dto ↔ domain/repository ↔ presentation — контракты совпадают
- WebSocket протокол: `ProtocolDtos.kt` соответствует `docs/03-protocol-integration.md`

#### C4: Spec Freshness
- Спеки не ссылаются на несуществующие модули/функции
- Новые фичи задокументированы в `docs/`

#### C5: Internal Consistency
- Паттерны ошибок, логирования, корутин — единообразны
- Обработка backoff/retry, reconnect — единообразна по слоям

#### C6: UI Surface Completeness
- Все user-facing операции из спеков имеют UI-экраны
- Нет мёртвых экранов/функций без спеки

#### C7: §Gap Hygiene (R17)
- Done-шаги не копятся в §Gap
- Все шаги Done → §Gap удалён целиком
- Решения Done-шагов перенесены в §Decisions

### Phase 4: Report

```
## Audit: <domain> [date]

### Summary
- Files audited: N
- Specs cross-referenced: N
- Findings: N (X BUG, Y HIGH, Z MEDIUM, ...)

### Findings

#### F1: [Title] [SEVERITY]
**Spec:** docs/XX.md §N
**Code:** app/src/.../File.kt:line
**Expected:** [what spec says]
**Actual:** [what code does]
**Impact:** [consequence]
**Fix:** [recommendation]

### Cross-Layer Contract Status
| Consumer | What | Status |
|---|---|---|
| ... | ... | OK / DRIFT / MISSING |
```

## Запреты

- НЕ пропускай файлы. Читай ВСЁ в домене.
- НЕ правь код. Аудитор формирует отчёт. Правки — через `/implement`.
- НЕ правь спеки. Для этого `/architect`.
