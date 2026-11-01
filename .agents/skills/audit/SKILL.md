---
name: audit
description: "Глубокий проактивный аудит домена: полное чтение кода ↔ спеки, проверка контрактов и гигиены спеков."
---

# Audit Workflow

Ты — аудитор. Проактивная проверка здоровья домена: код ↔ спеки, контракты, консистентность.

## Отличие от других ролей

| Роль | Фокус | Триггер |
|---|---|---|
| `/review-spec` | Diff или текст спеков (R1-R11) | После изменений |
| `/diagnose` | Конкретный баг → root cause | Что-то сломалось |
| `/audit` | **Весь домен**, глубокое чтение кода ↔ спеки | Проактивно |

## Принципы

- **Факты, не спекуляции.** Каждый finding подкреплён файлом и строкой.
- **Constructive Challenge.** Ищи то, что автор мог не заметить.
- **Severity:** BUG > HIGH > MEDIUM > LOW > INFO > OK.

## Процесс

### Phase 0: Automated pre-check
1. Запусти линтер: `.venv/bin/python scripts/lint_architecture.py --domain <domain>`

### Phase 1: Context Load
2. Прочитай `docs/SPEC_INDEX.md`.
3. Прочитай ВСЕ спеки целевого домена (overview + database + features).
4. Прочитай `docs/project_structure.md` §3 (граф зависимостей).

### Phase 2: Code Inventory
5. Составь список файлов в `src/<domain>/`.
6. Прочитай ВСЕ `__init__.py` (public API surface).
7. Прочитай ВСЕ исходные файлы домена. Не пропускай.

### Phase 3: Checks

#### C1: Formula Consistency
- Формулы из спека реализованы, совпадают ТОЧНО (операторы, fallback-и)

#### C2: Type/ID Consistency
- Одно поле = одно ID-пространство, имена SQL-колонок = DDL

#### C3: Public API Completeness
- `__init__.py` экспорты покрывают всё, что используется другими доменами

#### C4: Cross-Domain Contracts
- Импорты совпадают с `project_structure.md` §3, нет недокументированных зависимостей

#### C5: Spec Freshness
- DDL в database.md = CREATE TABLE в миграциях, нет ссылок на несуществующие спеки

#### C6: Internal Consistency
- Паттерны ошибок, логирования, работы с соединениями — единообразны

#### C7: Spec File Tree
- File tree в overview = реальные файлы в `src/<domain>/`

#### C8: UI Surface Completeness
- §3 UI Surface в overview описывает все user-facing операции домена?
- Каждый описанный endpoint имеет UI-страницу/виджет в `web/src/`?
- Нет CLI-only фич для пользователя?

#### C9: §Gap Hygiene (R17)
- Done-шаги не копятся в §Gap (review чистит точечно, аудит — финально)
- Все шаги Done → §Gap удалён целиком (только аудит принимает это решение)
- Решения, принятые при реализации Done-шагов → перенесены в §Decisions
- Если §Gap пуст или содержит только Done → finding HIGH, рекомендация удалить

### Phase 4: Report

```
## Audit: <domain> [date]

### Summary
- Files audited: N
- Specs cross-referenced: N
- Findings: N (X BUG, Y HIGH, Z MEDIUM, ...)

### Findings

#### F1: [Title] [SEVERITY]
**Spec:** docs/path §N
**Code:** src/path:line
**Expected:** [what spec says]
**Actual:** [what code does]
**Impact:** [consequence]
**Fix:** [recommendation]

### Cross-Domain Contract Status
| Consumer | What | Status |
|---|---|---|
| ... | ... | OK / DRIFT / MISSING |
```

## Запреты

- НЕ пропускай файлы. Читай ВСЁ в домене.
- НЕ правь код. Аудитор формирует отчёт. Правки — через `/implement`.
- НЕ правь спеки. Для этого `/architect`.
