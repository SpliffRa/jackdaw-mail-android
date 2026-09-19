## Что сделано

<!-- Краткое описание изменений. -->

## Зачем

<!-- Ссылка на issue / задачу / ADR. -->

Closes #

## Тип изменения

- [ ] feat (новая функциональность)
- [ ] fix (багфикс)
- [ ] refactor
- [ ] test
- [ ] docs
- [ ] chore / build / ci
- [ ] perf

## Модули

- [ ] app
- [ ] core/designsystem
- [ ] core/network
- [ ] core/database
- [ ] core/model
- [ ] feature/mail
- [ ] feature/composer
- [ ] feature/sla
- [ ] feature/related
- [ ] feature/calendar
- [ ] feature/contacts
- [ ] feature/settings
- [ ] sync

## Чек-лист автора

- [ ] `./gradlew assembleDebug` проходит.
- [ ] `./gradlew testDebugUnitTest` проходит.
- [ ] `./gradlew detekt ktlintCheck` проходит.
- [ ] Новый домен покрыт unit-тестами ≥ 80%
      (`SlaCalculator` / `RelatedEmailFinder` ≥ 90%).
- [ ] Room-миграции описаны и протестированы (если применимо).
- [ ] UI проверен в тёмной/светлой теме.
- [ ] Проверено на AVD `Pixel_10_Pro_XL`.
- [ ] Нет секретов, `!!`, `GlobalScope`, `runBlocking` в UI.
- [ ] Обновлены `CHANGELOG.md` и ADR (если нужно).

## Скриншоты / видео

<!-- Для UI-изменений: до/после, тёмная и светлая темы. -->

## Как тестировать

<!-- Пошагово: что нажать, что ожидать. -->

## Риски и побочные эффекты

<!-- Что может сломаться, что стоит перепроверить. -->