# Contributing — Jackdaw

Спасибо за интерес к проекту! Ниже приведены правила, которые помогают поддерживать чистоту и стабильность кодовой базы.

---

## Ветки

- `main` — стабильная релизная ветка, всегда собирается.
- `develop` — интеграционная ветка для активной разработки.
- `feature/<name>` — разработка новой функциональности.
- `fix/<name>` — исправление ошибок.
- `chore/<name>` — инфраструктурные задачи, обновление зависимостей.

---

## Формат коммитов (Conventional Commits)

Используйте формат: `<тип>(<область>): <описание>`

Примеры:
- `feat(android): add attachments chip to compose screen`
- `fix(room): resolve migration issue for account entity`
- `docs(agents): update build commands and instructions`
- `chore(deps): bump compose-bom to 2024.12.01`

Допустимые типы: `feat`, `fix`, `docs`, `refactor`, `perf`, `test`, `chore`.

---

## Чек-лист перед отправкой изменений

1. Проект собирается без ошибок:
   - Android: `cd android; .\gradlew.bat assembleDebug`
   - Desktop/Web: `cd desktop; npm run build`
2. Отсутствуют чувствительные данные (токены, пароли, приватные ключи).
3. Рабочая копия Git чиста: нет случайных бинарных файлов (`.apk`), дампов или отладочных логов.
4. Добавленные функции или изменения согласуются с архитектурой проекта и задокументированы.
