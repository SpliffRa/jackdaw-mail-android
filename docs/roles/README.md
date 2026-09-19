# Роли проекта Jackdaw Android

Минимальный набор ролей для разработки нативного Android-клиента на базе Jackdaw Mail.

| Роль | Файл | Ключевая ответственность |
|------|------|--------------------------|
| Архитектор / Tech Lead | [architect.md](architect.md) | Архитектура, ADR, версии, ревью |
| AI-ассистент (Gemini) | [ai-assistant.md](ai-assistant.md) | Генерация кода, объяснение протоколов |
| Android-разработчик | [android-dev.md](android-dev.md) | UI (Compose), Data (Room), Network |
| Domain-инженер | [domain.md](domain.md) | SlaCalculator, RelatedEmailFinder |
| QA-инженер | [qa.md](qa.md) | Тест-план, регресс, баг-репорты |
| SDET / Автотесты | [sdet.md](sdet.md) | Unit, Room, Compose UI Test, CI |
| AppSec | [appsec.md](appsec.md) | Keystore, Network Security Config, NTLM |

## Definition of Done (общий)

- [ ] `./gradlew assembleDebug` без error.
- [ ] Unit-тесты нового домена ≥ 80%.
- [ ] `detekt` / `ktlint` пройдены.
- [ ] Room-миграции описаны и протестированы.
- [ ] UI проверен в тёмной/светлой теме на `Pixel_10_Pro_XL`.
- [ ] Нет секретов и `TODO` без issue.
- [ ] Обновлены `CHANGELOG.md` и, при необходимости, ADR.