# AppSec / Безопасность

## Ответственность
- Хранение паролей/токенов: `EncryptedSharedPreferences` / Android Keystore.
- Network Security Config: whitelist доменов Exchange, без `cleartextTraffic`.
- NTLM: не логировать Type1/Type3, ограничить ретраи.
- R8 + правила для сериализуемых моделей.

## Артефакты
- Threat model, чек-лист безопасности.