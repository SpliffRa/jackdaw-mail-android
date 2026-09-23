---
name: jetpack-compose-performance
description: >-
  Use this skill when developing, refactoring, or optimizing Jetpack Compose UI screens, components,
  and animations in Jackdaw Mail, focusing on 120fps fluid performance, recomposition pruning, and ergonomics.
---

# Jetpack Compose Performance & Ergonomics Playbook

This skill outlines mandatory engineering patterns for building fluid, zero-jank Jetpack Compose UI (Material 3) in Jackdaw Mail.

---

## 1. Recomposition Optimization & Stability

Unnecessary recompositions drop frame rates from 120fps to <30fps in long mail threads and calendar views.

### Rule 1: Mark State Holders and Data Classes as `@Immutable` or `@Stable`
```kotlin
// GOOD: Compose compiler recognizes all properties as immutable
@Immutable
data class MailItemUiState(
    val id: String,
    val subject: String,
    val sender: String,
    val snippet: String,
    val isRead: Boolean,
    val tags: List<String> = emptyList() // Prefer kotlinx.collections.immutable or ImmutableList
)
```

### Rule 2: Always Provide `key` and `contentType` in Lazy Lists
```kotlin
LazyColumn(
    modifier = Modifier.fillMaxSize(),
    state = listState
) {
    items(
        items = emails,
        key = { email -> email.id }, // Prevents re-measuring when list items shift
        contentType = { email -> if (email.hasAttachments) "mail_with_attach" else "mail_standard" }
    ) { email ->
        EmailCard(email = email, onClick = { onSelect(email.id) })
    }
}
```

### Rule 3: Use `derivedStateOf` for Scroll State Observations
Never trigger recompositions on raw scroll offsets:
```kotlin
// GOOD: Only recalculates when boolean threshold flips
val showScrollToTop by remember {
    derivedStateOf { listState.firstVisibleItemIndex > 3 }
}
```

---

## 2. Thumb Zone & One-Hand Ergonomics

For a mobile mail client, users operate the device on-the-go with one hand:
- **Bottom Navigation & Actions:** Primary actions (Compose, Search, Filter, Multi-select action bar) must reside in the bottom third of the screen (`OutlookBottomBar`, bottom floating action buttons, modal bottom sheets).
- **Minimum Touch Targets:** All interactive icons, chips, and list action buttons must meet at least `48.dp x 48.dp` touch boundaries (`Modifier.minimumInteractiveComponentSize()`).
- **Swipe Physics:** Swipe gestures (e.g. swipe to archive/delete) must provide distinct haptic feedback (`LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)`) upon reaching the action threshold.

---

## 3. High Contrast & Dark Mode (WCAG AAA)

Mail clients present diverse email content and varied lighting environments:
- **Never hardcode `#000000` or `#FFFFFF`** for text. Always use theme semantic tokens:
  `MaterialTheme.colorScheme.onSurface`, `onSurfaceVariant`, `surfaceContainer`.
- **Contrast Ratios:** Text to background ratio must exceed `7:1` for body text and `4.5:1` for badges/captions.
- **Dark Mode Backgrounds:** Use deep Charcoal (`#0B0F14`) or true AMOLED dark with distinct card surface elevation (`surfaceContainerLow`, `surfaceContainerHigh`) to avoid muddy borders.
- **Link & Snippet Highlighting:** Avoid harsh underlines or washed-out blue links in dark mode.

---

## 4. Diagnostics & Compose Metrics

To inspect unstable Composable functions and skipping rates:
```powershell
cd android
.\gradlew.bat assembleRelease -PcomposeCompilerReports=true
```
Check `android/app/build/compose_compiler/` for:
- `*-composables.txt` (look for `restartable skippable` vs `unskippable`)
- `*-classes.txt` (ensure UI models are marked `stable`)
