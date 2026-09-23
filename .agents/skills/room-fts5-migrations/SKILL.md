---
name: room-fts5-migrations
description: >-
  Use this skill when modifying Room database entities, writing Room schema migrations,
  optimizing SQLite FTS5 full-text search, or diagnosing database query performance in Jackdaw Mail.
---

# Room SQLite & FTS5 Migrations Playbook

This skill covers database schema evolution, safe migrations, and ultra-fast FTS5 search indexing in Jackdaw Mail.

---

## 1. Zero Data Loss Migration Policy

Every modification to any `@Entity` requires an explicit `Migration(fromVersion, toVersion)`.

> [!CAUTION]
> Never use `fallbackToDestructiveMigration()` in production builds. It wipes user accounts, cached emails, and pending outbox queues.

### Migration Template
```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Example: Add a new column with safe default value
        db.execSQL("ALTER TABLE emails ADD COLUMN conversation_index TEXT DEFAULT NULL")
        
        // Example: Add an index on frequently queried columns
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_emails_conversation_index ON emails(conversation_index)")
    }
}
```

### Registering Migrations in Database Builder
In `JackdawDatabase.kt`:
```kotlin
Room.databaseBuilder(context, JackdawDatabase::class.java, "jackdaw_db")
    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING) // Ensure WAL mode
    .build()
```

---

## 2. Full-Text Search (FTS5) Synchronization

Jackdaw Mail utilizes SQLite FTS5 for sub-10ms instantaneous search across subjects, bodies, and senders.

### FTS Entity Definition
```kotlin
@Entity(tableName = "emails_fts")
@Fts4(contentEntity = EmailEntity::class) // or @Fts5 depending on SQLite wrapper
data class EmailFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowid: Long,
    val subject: String?,
    val snippet: String?,
    val sender: String?
)
```

### Prefix and Tokenized Search Query
```kotlin
@Dao
interface EmailDao {
    @Query("""
        SELECT emails.* FROM emails
        JOIN emails_fts ON emails.rowid = emails_fts.rowid
        WHERE emails_fts MATCH :query || '*'
        ORDER BY emails.date DESC
        LIMIT :limit OFFSET :offset
    """)
    fun searchEmails(query: String, limit: Int = 50, offset: Int = 0): Flow<List<EmailEntity>>
}
```

---

## 3. High-Throughput Batch Transactions

When syncing 500+ emails from Exchange/IMAP:
- **Never insert items one by one.** Always wrap bulk writes in `@Transaction` or `withTransaction { ... }`.
- Use `OnConflictStrategy.REPLACE` or custom Upsert to keep existing metadata intact.
- Keep transaction windows short to avoid holding WAL write locks.

```kotlin
@Transaction
suspend fun syncBatch(newEmails: List<EmailEntity>, folderId: String) {
    upsertEmails(newEmails)
    updateFolderLastSync(folderId, System.currentTimeMillis())
}
```

---

## 4. Migration Verification Checklist

Before committing a Room database change:
1. Update `@Database(version = N)` to `N + 1`.
2. Write corresponding unit test in `android/app/src/test/` using `MigrationTestHelper`.
3. Verify `lintDebug` and `testDebugUnitTest` pass without warnings.
