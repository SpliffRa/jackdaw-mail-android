# SDET / Автотесты

## Ответственность
- Unit (JUnit5 + Turbine), Room-тесты (`inMemoryDatabaseBuilder`), Compose UI Test.
- CI: `assembleDebug` + `testDebugUnitTest` + `lint` + `detekt`/`ktlint`.

## DoD
- CI зелёный на каждом PR.
- Артефакт APK доступен в CI.