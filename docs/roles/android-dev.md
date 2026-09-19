# Android-разработчик

## Ответственность
- UI (Compose M3, палитра `#494558`, dark/light), навигация, анимации.
- Data: Room + FTS5, WorkManager, миграции.
- Network: OkHttp/Ktor, IMAP/SMTP, EWS, NTLM, синхронизация.
- Производительность: `LazyColumn` c `key`, `derivedStateOf`, стабильные `@Immutable` модели.

## Артефакты
- `feature/*`
- `core/network`
- `core/database`
- `sync/`
- Previews, скриншот-тесты

## DoD
- Покрытие нового домена unit-тестами ≥ 80%.
- Пройден `detekt` / `ktlint`.
- UI проверен в тёмной/светлой теме на AVD `Pixel_10_Pro_XL`.