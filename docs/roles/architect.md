# Архитектор / Tech Lead

## Ответственность
- Целостность Clean Architecture + MVI/MVVM, границы модулей
  (`feature/*` не знает о `core/database` напрямую).
- Выбор и версии зависимостей (Compose BOM, AGP, Kotlin, KSP, Hilt, Room, WorkManager).
- ADR-документы по спорным решениям (OkHttp vs Ktor, Hilt vs Koin, навигация).
- Правила ревью: ни одного PR без теста, ни одного `!!`, обязательные миграции Room.

## Артефакты
- `docs/adr/`
- Схема модулей
- `libs.versions.toml`
- Чек-лист ревью

## DoD
- Любое изменение архитектуры описано в ADR.
- Сборка `./gradlew assembleDebug` проходит без error.