---
name: desktop-web-release
description: >-
  Use this skill when building, packaging, or releasing the Jackdaw Mail Electron desktop application
  or Svelte web application, including GitHub Releases OTA updates and multiplatform package verification.
---

# Desktop & Web Release Playbook

This skill outlines the packaging, Over-The-Air (OTA) update generation, and release protocols for Jackdaw Desktop (Electron) and Web (`app/` Svelte).

---

## 1. Electron & Web Architecture

- `desktop/` — Electron main/preload backend, JPC (JSON-RPC), auto-updater integration.
- `app/` — Svelte client UI, compiled and bundled into the Electron window or served on web.
- `docs/systems/desktop-build/ota-jackdaw.md` — Canonical documentation for Desktop OTA releases.

---

## 2. Desktop Packaging Commands

From repository root:

```bash
# 1. Install dependencies
cd app && npm install && npm run build
cd ../desktop && npm install

# 2. Package for Windows
npm run dist:win

# 3. Package for macOS
npm run dist:mac
```

---

## 3. OTA Release Protocol & Multiplatform CI

Jackdaw Mail utilizes GitHub Releases for automated client updates.

> [!IMPORTANT]
> **Sequential Release Protection (`prepare` job):**
> Do NOT publish Mac and Windows builds simultaneously without the preparatory CI shell.
> The CI task `prepare` must first create the draft GitHub Release. Mac and Windows runners then upload their respective artifacts into that single shared release tag.
> Violating this creates duplicate releases and permanently breaks Windows `electron-updater`.

### Artifact Checklist per Platform
- **Windows:**
  - Setup executable (`Jackdaw-Mail-Setup-<version>.exe`)
  - `latest.yml` (containing SHA-512 hashes and package size required by electron-updater)
  - Blockmap file (`.exe.blockmap`)
- **macOS:**
  - DMG installer (`Jackdaw-Mail-<version>.dmg`)
  - Zip package (`Jackdaw-Mail-<version>-mac.zip`)
  - `latest-mac.yml`
  - Quit-and-install helper script

---

## 4. Web Client Build Verification

```bash
cd app
npm run check
npm run build
```
Verify that `app/dist/` contains valid compiled assets (`index.html`, bundles, assets) with no broken relative asset paths.
