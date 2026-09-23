---
name: mail-sync-protocol
description: >-
  Use this skill when developing or debugging email sync engines, protocol adapters (Exchange OWA/EWS, ActiveSync, IMAP),
  delta sync tokens, offline outbox queuing, and RFC MIME parsing in Jackdaw Mail.
---

# Mail Protocol & Synchronization Playbook

This skill outlines the architecture and execution patterns for Exchange OWA/EWS, ActiveSync, and IMAP synchronization in Jackdaw Mail.

---

## 1. Incremental Delta Synchronization

Full mailbox scans consume excessive bandwidth and battery. Always prioritize delta sync:

1. **Persist Sync State:** Store `sync_key` or `delta_token` per folder in `FolderEntity`.
2. **Handle Invalid Token (`410 Gone` / `SyncKeyInvalid`):**
   - If server rejects the token (expired or reset), gracefully reset folder sync state.
   - Re-fetch the latest N (e.g. 50) messages without clearing user flags (e.g. pinned, starred).
3. **Paging & Limits:** Limit initial sync to 50 items per folder batch; fetch older messages only on-demand (infinite scroll / pull-up).

---

## 2. Offline Outbox Queue & Action Journal

When the device is offline or in flight mode, user actions must never fail or get dropped:

### Workflow
```
[User Action: Send/Delete/MarkRead]
       │
       ▼
[Write to Local SQLite (Optimistic UI)]
       │
       ▼
[Enqueue in OutboxEntity / ActionJournal]
       │
       ▼
[Trigger WorkManager (OneTimeWorkRequest with Connected constraint)]
       │
       ▼
[Network Available? -> Process Queue with Exponential Backoff]
```

### Exponential Backoff with Jitter Formula
To prevent thundering herd when server recovers:
```kotlin
fun calculateBackoffDelay(attempt: Int, baseDelayMs: Long = 1000L, maxDelayMs: Long = 60000L): Long {
    val exponential = (baseDelayMs * (1L shl attempt.coerceAtMost(6))).coerceAtMost(maxDelayMs)
    val jitter = (0..(exponential / 4)).random()
    return exponential + jitter
}
```

---

## 3. MIME & RFC 2822 / 5322 Handling

When parsing raw email payloads:
- **Multipart Structure:**
  - `multipart/alternative`: Extract `text/html` if available, fallback to `text/plain`.
  - `multipart/mixed`: Extract attachments, sanitize filenames, and compute content length.
  - `multipart/related`: Map inline `cid:` content identifiers to local cached attachment URIs.
- **Character Encodings:**
  - Detect `charset` header (UTF-8, ISO-8859-1, Windows-1251).
  - Always decode to standard Kotlin `String` (UTF-16 in-memory) safely without throwing `CharacterCodingException`.

---

## 4. Authentication & Token Refresh

- Check access token expiry before triggering any network batch.
- If HTTP 401 occurs during active sync:
  1. Trigger atomic token refresh via `AccountRepository`.
  2. Retry the failed batch once with the new bearer token.
  3. If refresh fails with invalid credentials, dispatch `AuthRequired` event to notify the user.
