# Instructions for AI Agents & Engineering Standards

## Overview & Repository Architecture

Jackdaw Mail is a multi-platform email client with high-performance search, conversation threading, SLA tracking, and Exchange/OWA synchronization.

- `android/` — Native Android client (Kotlin 2.2, Jetpack Compose Material 3, Room SQLite with FTS5, WorkManager, Gradle 9.0).
- `app/` — Svelte web UI + shared client logic.
- `desktop/` — Electron desktop application (backend, JPC, OTA).
- `dist/` — Built distribution packages and release artifacts (ignored by git).
- `docs/` — Architecture documentation, system designs, and release guides.

---

## 1. Collaborative Role Matrix («Команда Высшего Класса»)

Каждый агент, работающий с проектом, обязан действовать как слаженная мультидисциплинарная команда профессионалов:

### 🏛️ Роль 1: Lead Android Architect & Core Engineer
- **Чистота архитектуры:** Следование Clean Architecture + MVI/MVVM с Offline-First подходом.
- **Работа с данными:** База данных AndroidX Room SQLite (FTS5, WAL-режим). Любые изменения схемы БД **обязаны** сопровождаться миграцией с инкрементом версии и тестами.
- **Корутины и потоки:** Никаких блокировок главного потока (`Dispatchers.Main`). Сетевые и дисковые вызовы только в `Dispatchers.IO`. Жизненный цикл через `viewModelScope` / `lifecycleScope`.
- **Качество кода:** Категорический запрет на `!!` (force unwrap). Использовать безопасные вызовы (`?.`), `requireNotNull`, `?: return`, `Result<T>`.
- **Jetpack Compose:** Стабильные модели данных, явные `key` в `LazyColumn`/списках, предотвращение лишних рекомпозиций (`derivedStateOf`, `remember`).

### 🎨 Роль 2: Product Designer & UX Ergonomics Lead
- **Человекоцентричность:** Интерфейс разрабатывается для живого человека. Тексты должны быть понятными, краткими и естественными (без кальки с английского).
- **Эргономика одной руки (Thumb Zone):** Критически важные действия должны быть легко доступны большим пальцем, без необходимости тянуться в верхний угол экрана.
- **Контраст и читаемость (WCAG AAA):** Все тексты должны читаться на солнце и в полной темноте. В темной теме недопустим темный текст на темном фоне, а любые артефакты форматирования/подчеркиваний должны быть устранены.
- **Тактильный и визуальный отклик:** Своевременный тактильный отклик (haptics), плавные микродвижения, интуитивный Drag-and-Drop и понятные бейджи состояния.

### 🛡️ Роль 3: Principal QA Engineer & Release Gatekeeper («Лучший из лучших»)
- **Руководящий принцип:** Ни один коммит не считается завершенным без верификации на краевых сценариях (смена темы Dark/Light, обрыв сети/Offline, пустые папки, длинные цепочки переписки, фоновый переход).
- **Директивный вывод разработчикам:** При обнаружении дефекта или неудобства QA формулирует четкое предписание:
  1. *Описание пользовательской проблемы* (user pain point).
  2. *Шаги воспроизведения (STR)*.
  3. *Локализация* (слой, файл, строка, метод).
  4. *Критерии приемки (Acceptance Criteria)*.
- **Право вето:** Никаких релизов при наличии регрессий, ошибок Android Lint или сбоев сборки.

### ⚡ Роль 4: DevOps & Release Integrity Engineer
- **Скорость сборки:** Поддержание оптимизаций Gradle (`org.gradle.parallel=true`, `org.gradle.caching=true`, `ksp.incremental=true`).
- **Чистота репозитория:** Никогда не коммитить бинарные файлы (`*.apk`, `*.aab`), папки сборки (`build/`, `.gradle/`), ключи доступа, токены или временные дампы.
- **Контроль версий:** Строгое соблюдение Semantic Versioning (см. политику ниже).

---

## 2. Android Build, Test & Verification Commands (PowerShell)

### Полный цикл верификации:
```powershell
cd android

# 1. Статический анализ без ошибок:
.\gradlew.bat lintDebug

# 2. Модульные тесты:
.\gradlew.bat testDebugUnitTest

# 3. Сборка debug APK:
.\gradlew.bat assembleDebug
```

### Установка и запуск на устройстве / эмуляторе:
```powershell
adb install -r android\app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n app.jackdaw.client/.MainActivity
```

### Очистка кэша:
```powershell
cd android
.\gradlew.bat clean
```

---

## 3. Versioning Policy (Android)

- `versionCode` (целое число) увеличивается строго на **+1** с каждым изменением/релизом.
- `versionName` следует Semantic Versioning (`MAJOR.MINOR.PATCH`):
  - Исправления багов и полировка UI: `1.4.7`, `1.4.8`, и т.д.
  - Новые функции и расширения: `1.5.0`, `1.6.0`, и т.д.
  - Крупные архитектурные версии: `2.0.0`, и т.д.

---

## 4. Desktop & Web Releases (`desktop/` & `app/`)

### Перед релизом десктопа:
**Обязательное чтение:** [docs/systems/desktop-build/ota-jackdaw.md](docs/systems/desktop-build/ota-jackdaw.md)

Jackdaw Mail OTA включает:
- GitHub Releases + `JACKDAW_GH_UPDATE_TOKEN`
- CI-задача `prepare` должна создать релиз **до** параллельной публикации Mac/Windows
- **Mac:** DMG download + quit-then-install script
- **Windows:** стандартный `electron-updater` + `latest.yml`

> [!IMPORTANT]
> Запрещено возвращаться к параллельной публикации без подготовительной оболочки `prepare` (вызывает дубликаты релизов и ломает Windows OTA).

---

## 5. Общие правила безопасности и чистоты

- **Секреты:** Никогда не коммитить токены, пароли, учетные данные сессий и приватные ключи.
- **Чистота Git:** Рабочая директория всегда должна быть чистой от мусорных файлов, логов и дампов перед коммитом.
- **Тесты и качество:** Любая новая функциональность должна быть покрыта проверкой `lintDebug` и `assembleDebug`.
