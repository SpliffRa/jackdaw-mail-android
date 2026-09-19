# Instructions for AI Agents

## Overview & Repository Architecture

Jackdaw Mail is a multi-platform email client with high-performance search, conversation threading, and SLA tracking.

- `android/` — Native Android client (Kotlin 2.2, Jetpack Compose Material 3, Room SQLite with FTS, WorkManager, Gradle 9.0).
- `app/` — Svelte web UI + shared client logic.
- `desktop/` — Electron desktop application (backend, JPC, OTA).
- `dist/` — Built distribution packages and release artifacts (ignored by git).
- `docs/` — Architecture documentation, roles, and release guides.

---

## 1. Android Development (`android/`)

### Architecture & Conventions
- **Language & UI:** Kotlin + Jetpack Compose (Material 3).
- **Architecture:** Clean Architecture / MVI-MVVM with offline-first repository.
- **Database:** AndroidX Room with FTS4/FTS5 full-text search and WAL mode.
- **Background tasks:** `WorkManager` (periodic sync, battery and network constraints).
- **Structure in `android/app/src/main/java/app/jackdaw/client/`:**
  - `core/` — design system, theme, domain models, update manager.
  - `data/` — Room database, entities, DAOs, repository, network sync engine, workers.
  - `ui/` — reusable components, screens (mail list, detail, compose, settings), view models.

### Build & Verification Commands (Windows / PowerShell)
- **Assemble debug APK:**
  ```powershell
  cd android
  .\gradlew.bat assembleDebug
  ```
- **Install & Launch on Emulator/Device:**
  ```powershell
  adb install -r android\app\build\outputs\apk\debug\app-debug.apk
  adb shell am start -n app.jackdaw.client/.MainActivity
  ```
- **Clean build cache:**
  ```powershell
  cd android
  .\gradlew.bat clean
  ```

### Versioning Policy (Android)
- `versionCode` (integer) increments by 1 with each release.
- `versionName` follows semantic versioning:
  - Minor feature updates: `1.2`, `1.3`, `1.4`, etc.
  - Major architectural updates: `2.0`, `3.0`, etc.

---

## 2. Desktop & Web Releases (`desktop/` & `app/`)

### Before changing desktop releases or auto-update
**Required reading:** [docs/systems/desktop-build/ota-jackdaw.md](docs/systems/desktop-build/ota-jackdaw.md)

Jackdaw Mail OTA includes:
- Private GitHub Releases + `JACKDAW_GH_UPDATE_TOKEN` (optional on public repo)
- CI job `prepare` that must create the release **before** parallel Mac/Windows publish
- **Mac:** DMG download + quit-then-install script (no Apple Developer signing on prerelease)
- **Windows:** standard `electron-updater` + `latest.yml`

> [!IMPORTANT]
> Do **not** revert to parallel publish without the `prepare` release shell (causes duplicate releases and broken Windows OTA).

### Build & Verification
- Web UI: from `app/`, `npm run build` generates `app/dist/`.
- Desktop package: from `desktop/`, `npm run build` followed by electron-builder for the target platform.

---

## 3. General Rules & Secrets (Never Commit)

- Never commit tokens or secrets (e.g. `JACKDAW_GH_UPDATE_TOKEN`, OAuth client secrets, private keys).
- Keep `.gitignore` clean: never commit binary APKs/AABs, build directories (`android/**/build`, `.gradle`), or machine-specific configs (`local.properties`).
- No direct network calls or blocking operations on the Android Main thread.
- Avoid force unwraps (`!!`) in Kotlin; use safe calls, `requireNotNull`, or `Result` types.
