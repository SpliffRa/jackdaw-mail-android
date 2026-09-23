---
name: android-build-verify
description: >-
  Use this skill when building, testing, linting, or deploying the Jackdaw Mail Android application,
  including running Gradle checks, installing APK via ADB, checking Logcat, and validating zero-regression releases.
---

# Android Build & Verification Workflow

This skill guides you through the exact verification steps, diagnostic commands, and release gatekeeping procedures for Jackdaw Mail on Android (Kotlin 2.2, Gradle 9.0).

---

## 1. Quick Verification Pipeline (Run in PowerShell)

Always execute verification from the `android/` directory:

```powershell
cd android

# Step 1: Run static analysis (Android Lint)
.\gradlew.bat lintDebug

# Step 2: Run unit tests
.\gradlew.bat testDebugUnitTest

# Step 3: Compile debug APK
.\gradlew.bat assembleDebug
```

> [!IMPORTANT]
> If any lint error or test failure occurs, **do not proceed** to release or installation. Fix the underlying issue first.

---

## 2. Deploying & Testing on Device / Emulator

### Install Debug APK
```powershell
adb install -r android\app\build\outputs\apk\debug\app-debug.apk
```

### Launch Application
```powershell
adb shell am start -n app.jackdaw.client/.MainActivity
```

### Stream Live Logs (Filtering for Jackdaw)
```powershell
adb logcat -v time -s Jackdaw:* AndroidRuntime:E
```

### Force-Stop App (For Testing Process Death / Cold Start)
```powershell
adb shell am force-stop app.jackdaw.client
```

---

## 3. Fast Incremental Builds & Caching Tips

- Gradle daemon is enabled by default. To speed up builds:
  `.\gradlew.bat assembleDebug --build-cache --parallel`
- If Gradle lock issues or stale cache occurs:
  ```powershell
  cd android
  .\gradlew.bat --stop
  .\gradlew.bat clean
  ```

---

## 4. Lint Issue Triage Checklist

When `lintDebug` reports errors in `android/app/build/reports/lint-results-debug.html`:
1. **Unused resources / imports:** Remove immediately.
2. **Missing string translations:** Ensure fallback strings exist in `res/values/strings.xml`.
3. **Typography & Material styling:** Avoid hardcoded colors or styles that break Dynamic Theming / Dark Mode.
4. **Coroutine scope leak warnings:** Verify all coroutines launch inside `viewModelScope` or `rememberCoroutineScope()`.

---

## 5. Pre-Release Gatekeeper Rules

Before any pull request or tag:
- [ ] `lintDebug` returns `BUILD SUCCESSFUL` with 0 errors.
- [ ] `testDebugUnitTest` passes 100% of unit tests.
- [ ] `assembleDebug` builds cleanly.
- [ ] `versionCode` in `android/app/build.gradle.kts` incremented by `+1`.
- [ ] No binary artifacts or `.apk` files staged in git.
