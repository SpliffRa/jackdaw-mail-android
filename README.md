# Jackdaw Mail Android

<div align="center">

**Современный высокопроизводительный почтовый клиент для Android**

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Database](https://img.shields.io/badge/Room-SQLite%20FTS5-orange?logo=sqlite&logoColor=white)](https://developer.android.com/training/data-storage/room)
[![Version](https://img.shields.io/badge/Version-1.5.4%20(Build%2063)-blue)]()
[![Author](https://img.shields.io/badge/Author-SpliffRa-black?logo=github)](https://github.com/SpliffRa)

[Возможности](#-ключевые-возможности) • [Архитектура](#-архитектура-приложения) • [Сборка и запуск](#-сборка-и-установка) • [Автор](#-автор)

</div>

---

## 📱 О проекте

**Jackdaw Mail Android** — это нативный клиент электронной почты, разработанный на **Kotlin** и **Jetpack Compose (Material 3)**. Приложение построено по принципу **Offline-First**, обеспечивая максимальную скорость работы (120 FPS), мгновенный полнотекстовый поиск по письмам через **Room SQLite FTS5**, живой контроль корпоративных дедлайнов (SLA) и фоновую синхронизацию через **WorkManager**.

> [!NOTE]
> **Оригинальная идея и концепция:**  
> Концепция и дизайн-система Jackdaw Mail изначально заимствованы у автора **[Uugsx](https://github.com/Uugsx)** (оригинальный проект: [Uugsx/jackdaw-mail](https://github.com/Uugsx/jackdaw-mail)). Первоначальная идея и реализация десктопного решения принадлежат ему.  
> В рамках данного репозитория **[SpliffRa](https://github.com/SpliffRa)** занимается разработкой нативного мобильного приложения для платформы **Android**.

Фирменный дизайн выполнен в контрастной темной палитре **Charcoal & Jackdaw Amber**.

---

## ✨ Ключевые возможности

### 🔔 Умные уведомления и статус «Просмотрено»
- **Отсечение повторных звуков:** система отслеживает время последнего просмотра ящика (`lastViewedTimestamp`) и ID оповещённых писем (`notifiedIds`). Если письма остались непрочитанными, повторные фоновые звуки о них больше не звучат.
- **Честная дельта синхронизации:** звуковые оповещения срабатывают только при поступлении действительно новых писем, отсутствовавших в локальной базе Room.
- **Интеллектуальная шторка уведомлений:** при открытии приложения уведомления из системной шторки аккуратно гасятся, не отвлекая пользователя во время работы с почтой.

### 🚀 Мгновенный запуск без скачков интерфейса
- **Плавный холодный старт:** устранено мерцание экрана авторизации при запуске приложения — список писем открывается мгновенно и бесшовно.
- **Деликатная индикация синхронизации:** убран нависающий над карточками писем спиннер. Статус синхронизации отображается аккуратным янтарным текстом под названием открытой папки.

### 📬 Реактивная система непрочитанных писем
- **Динамический подсчет в реальном времени:** счетчики непрочитанных и общего числа писем для всех папок («Входящие», «SLA Контроль», «Черновики», «Архив», «Корзина») вычисляются реактивно через Room Flow.
- **Умные бейджи папок:** при прочтении писем бейдж в боковом меню (`FolderDrawer`) плавно уменьшается, а при нуле непрочитанных — автоматически исчезает.
- **Индикация непрочитанных сообщений:** акцентная янтарная точка на аватаре и рядом с именем отправителя.
- **Быстрое управление статусом прочтения:** кнопка в верхнем тулбаре детального просмотра письма позволяет одним касанием вернуть или снять статус непрочитанного.

### 📄 Полный просмотр и корректный рендеринг писем
- Нативный скроллинг тела письма без обрезки по высоте.
- Автоматическая адаптация контрастности HTML для темной темы (исключен невидимый темный текст на темном фоне).
- Резолвинг встроенных CID-изображений и логотипов прямо в теле сообщения.
- Отображение точных имен и реальных размеров вложений из Exchange без синтетических заглушек.

### 👆 Безопасные и плавные свайп-жесты
- **Высокий порог намеренного свайпа:** порог срабатывания настроен на 65% ширины экрана (`positionalThreshold = 0.65f`). Случайные микродвижения пальца при вертикальной прокрутке списка полностью исключены.
- **Плавный визуальный отклик:** иконки действий масштабируются от `0.75x` до `1.15x`, а фон плавно набирает цвет по мере приближения к порогу срабатывания.
- **Защита от случайного удаления:** свайп влево (удаление) плавно возвращает карточку на место и открывает диалог подтверждения (`AlertDialog`) с деталями удаляемого письма.
- **Свайп в архив:** свайп вправо плавно перемещает сообщение в папку архива с возможностью мгновенной отмены через снекбар.

### 🔍 Мгновенный полнотекстовый поиск (FTS5)
- Индексация в локальной базе данных SQLite через FTS4/FTS5.
- Полнотекстовый поиск по теме, отправителю, получателям, сниппету и телу письма за доли миллисекунды.

### ⏱️ Интерактивный контроль SLA и дедлайнов
- Автоматический расчет 30-минутного регламента ответа на входящие письма.
- Цветовые уровни критичности:
  - 🟢 **NORMAL**
  - 🟡 **WARNING**
  - 🔴 **URGENT**
  - ⚫ **BREACHED** (просрочено)
- Визуальный бейдж обратного отсчета в карточке каждого письма и отдельная папка «SLA Контроль».

### 🔄 Offline-First архитектура и фоновая синхронизация
- Локальная база данных Room работает в режиме WAL (Write-Ahead Logging).
- Фоновый воркер `MailSyncWorker` (WorkManager) с учетом заряда батареи и наличия сетевого подключения.
- Очередь исходящих писем (`Outbox`) с автоматической отправкой при появлении связи.

### 👥 Поддержка нескольких аккаунтов
- Быстрое переключение между рабочими и личными почтовыми профилями через боковое меню.
- Раздельные папки, настройки и локальные кэши.

---

## 🛠 Архитектура приложения

Приложение построено в соответствии с принципами **Clean Architecture** и паттерном **MVI-MVVM**:

```
android/app/src/main/java/app/jackdaw/client/
├── core/
│   ├── designsystem/theme/    # Палитра Amber/Charcoal, Typography, Shape
│   ├── model/                 # Доменные модели: EmailMessage, Folder, Account, SLA
│   ├── notification/          # NotificationHelper, NotificationTracker, SoundNotificationManager
│   └── update/                # Менеджер OTA-обновлений через GitHub Releases
├── data/
│   ├── local/
│   │   ├── converter/         # TypeConverters для Room (списки, статусы, даты)
│   │   ├── dao/               # EmailDao, FolderDao, AccountDao, AttachmentDao
│   │   ├── entity/            # Сущности SQLite (EmailEntity, FolderEntity, FTS)
│   │   └── JackdawDatabase.kt # Singleton базы данных Room
│   ├── network/               # Почтовый протокольный движок OWA/Exchange и синхронизация
│   ├── repository/            # OfflineFirstMailRepository (единый источник правды)
│   └── worker/                # WorkManager (фоновая синхронизация)
└── ui/
    ├── components/            # EmailCard, SwipeableEmailCard, FolderDrawer, SlaBadge
    ├── navigation/            # Compose Navigation: MailList, MailDetail, Compose, Settings
    ├── screens/
    │   ├── maillist/          # Экран списка писем с фильтрами и свайпами
    │   ├── maildetail/        # Экран детального чтения с управлением статусом
    │   ├── compose/           # Экран написания и отправки письма
    │   └── settings/          # Настройки, аккаунты и проверка обновлений
    └── viewmodel/             # MailViewModel (StateFlow, Coroutines)
```

---

## 🚀 Сборка и установка

### Системные требования
- **JDK:** 17 или выше
- **Android SDK:** Compile SDK 35, Target SDK 35, Min SDK 26 (Android 8.0+)
- **Gradle:** 9.0 (Gradle Wrapper включен в репозиторий)
- **Android Gradle Plugin (AGP):** 8.12.1

### Команды для сборки (PowerShell / Командная строка)

1. **Клонирование репозитория:**
   ```bash
   git clone https://github.com/SpliffRa/jackdaw-mail-android.git
   cd jackdaw-mail-android/android
   ```

2. **Сборка Debug APK:**
   ```powershell
   .\gradlew.bat assembleDebug
   ```
   Готовый APK файл формируется по пути:
   `android/app/build/outputs/apk/debug/app-debug.apk`

3. **Установка на подключенное устройство / эмулятор:**
   ```powershell
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   adb shell am start -n app.jackdaw.client/.MainActivity
   ```

4. **Очистка кэша сборки:**
   ```powershell
   .\gradlew.bat clean
   ```

---

## 👤 Автор и благодарности

- **Разработка Android-приложения:** [SpliffRa](https://github.com/SpliffRa)
- **Email для связи:** `spliffraida@gmail.com`
- **Репозиторий Android-клиента:** [SpliffRa/jackdaw-mail-android](https://github.com/SpliffRa/jackdaw-mail-android)
- **Оригинальная идея и концепция Jackdaw Mail:** [Uugsx](https://github.com/Uugsx) (проект [Uugsx/jackdaw-mail](https://github.com/Uugsx/jackdaw-mail))

---

<div align="center">
<sub>Jackdaw Mail Android · Developed by SpliffRa · Originally inspired by Uugsx</sub>
</div>
