package io.github.akhilesh2491.scry.core

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL

/** One persisted item belonging to a plugin. */
public class StoredRecord(
    public val id: String,
    public val pluginId: String,
    public val createdAtMillis: Long,
    public val payload: String,
) {
    public val sizeBytes: Int get() = payload.length
}

/**
 * Scry's shared persistent store.
 *
 * Deliberately a *generic* record store keyed by plugin rather than a set of
 * feature-specific tables. Core stays plugin-agnostic — a third-party plugin gets
 * exactly the same storage the built-ins do — and there is one retention policy
 * covering everything rather than one per feature that each need to be right.
 *
 * Backed by `androidx.sqlite`'s [BundledSQLiteDriver], which compiles SQLite from
 * source so every platform runs an identical engine. It is also the driver the
 * database-inspector plugin needs for raw SQL access, so this is one dependency
 * serving both purposes.
 *
 * All methods are non-suspending and internally synchronised — capture happens on
 * whatever thread the app's HTTP client used, which is frequently not a coroutine.
 */
public class ScryStore internal constructor(
    private val connection: SQLiteConnection,
    private val retention: Retention,
) {
    private val lock = ScryLock()
    private var closed = false

    /**
     * Inserts or replaces a record, then enforces retention.
     *
     * Eviction happens here, on the write path, rather than in a periodic sweep:
     * a background cleaner still allows the store to spike arbitrarily between
     * runs, which is exactly the unbounded-growth complaint this is meant to
     * prevent.
     */
    public fun put(
        pluginId: String,
        id: String,
        payload: String,
        createdAtMillis: Long = currentTimeMillis(),
    ) {
        lock.withLock {
            if (closed) return@withLock
            connection.prepare(SQL_UPSERT).use { statement ->
                statement.bindText(1, id)
                statement.bindText(2, pluginId)
                statement.bindLong(3, createdAtMillis)
                statement.bindLong(4, payload.length.toLong())
                statement.bindText(5, payload)
                statement.step()
            }
            enforceRetentionLocked()
        }
    }

    /** Most recent records for a plugin, newest first. */
    public fun read(pluginId: String, limit: Int = Int.MAX_VALUE): List<StoredRecord> =
        lock.withLock {
            if (closed) return@withLock emptyList()
            buildList {
                connection.prepare(SQL_SELECT_BY_PLUGIN).use { statement ->
                    statement.bindText(1, pluginId)
                    statement.bindLong(2, limit.toLong())
                    while (statement.step()) {
                        add(
                            StoredRecord(
                                id = statement.getText(0),
                                pluginId = statement.getText(1),
                                createdAtMillis = statement.getLong(2),
                                payload = statement.getText(3),
                            ),
                        )
                    }
                }
            }
        }

    /** Removes a single record. */
    public fun delete(id: String) {
        lock.withLock {
            if (closed) return@withLock
            connection.prepare("DELETE FROM records WHERE id = ?").use {
                it.bindText(1, id)
                it.step()
            }
        }
    }

    /** Removes every record belonging to one plugin. */
    public fun clear(pluginId: String) {
        lock.withLock {
            if (closed) return@withLock
            connection.prepare("DELETE FROM records WHERE plugin_id = ?").use {
                it.bindText(1, pluginId)
                it.step()
            }
        }
    }

    /** Removes everything. */
    public fun clearAll() {
        lock.withLock {
            if (closed) return@withLock
            connection.execSQL("DELETE FROM records")
        }
    }

    /** Number of records for a plugin. */
    public fun count(pluginId: String): Int = lock.withLock {
        if (closed) return@withLock 0
        connection.prepare("SELECT COUNT(*) FROM records WHERE plugin_id = ?").use {
            it.bindText(1, pluginId)
            if (it.step()) it.getInt(0) else 0
        }
    }

    /** Total payload bytes across all plugins. */
    public fun totalSizeBytes(): Long = lock.withLock {
        if (closed) return@withLock 0L
        connection.prepare("SELECT COALESCE(SUM(size_bytes), 0) FROM records").use {
            if (it.step()) it.getLong(0) else 0L
        }
    }

    internal fun close() {
        lock.withLock {
            if (closed) return@withLock
            closed = true
            connection.close()
        }
    }

    /**
     * Applies all three retention limits, oldest-first. Must hold [lock].
     *
     * Age first (cheapest and most predictable), then entry count, then total
     * size. Size last because it is the limit most likely to be hit by a single
     * enormous response, and trimming by count first usually resolves it.
     */
    private fun enforceRetentionLocked() {
        val cutoff = currentTimeMillis() - retention.maxAge.inWholeMilliseconds
        connection.prepare("DELETE FROM records WHERE created_at < ?").use {
            it.bindLong(1, cutoff)
            it.step()
        }

        connection.execSQL(
            """
            DELETE FROM records WHERE id NOT IN (
                SELECT id FROM records ORDER BY created_at DESC LIMIT ${retention.maxEntries}
            )
            """.trimIndent(),
        )

        var total = connection.prepare("SELECT COALESCE(SUM(size_bytes), 0) FROM records")
            .use { if (it.step()) it.getLong(0) else 0L }
        while (total > retention.maxBytes) {
            val oldest = connection
                .prepare("SELECT id, size_bytes FROM records ORDER BY created_at ASC LIMIT 1")
                .use { if (it.step()) it.getText(0) to it.getLong(1) else null }
                ?: break
            connection.prepare("DELETE FROM records WHERE id = ?").use {
                it.bindText(1, oldest.first)
                it.step()
            }
            total -= oldest.second
        }
    }

    public companion object {
        private const val SQL_UPSERT =
            "INSERT OR REPLACE INTO records (id, plugin_id, created_at, size_bytes, payload) " +
                "VALUES (?, ?, ?, ?, ?)"

        private const val SQL_SELECT_BY_PLUGIN =
            "SELECT id, plugin_id, created_at, payload FROM records " +
                "WHERE plugin_id = ? ORDER BY created_at DESC LIMIT ?"

        internal fun open(directory: String, retention: Retention): ScryStore =
            openAt("$directory/scry.db", retention)

        private fun openAt(path: String, retention: Retention): ScryStore {
            val connection = BundledSQLiteDriver().open(path)
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS records (
                    id TEXT NOT NULL PRIMARY KEY,
                    plugin_id TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    size_bytes INTEGER NOT NULL,
                    payload TEXT NOT NULL
                )
                """.trimIndent(),
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_records_plugin_created " +
                    "ON records (plugin_id, created_at DESC)",
            )
            // WAL keeps readers from blocking the writer. It also matters for the
            // database-inspector plugin later, which opens app databases alongside
            // whatever connection the app already holds.
            connection.execSQL("PRAGMA journal_mode=WAL")
            return ScryStore(connection, retention)
        }

        /** In-memory store for tests. */
        public fun inMemory(retention: Retention = Retention.DEFAULT): ScryStore =
            openAt(":memory:", retention)
    }
}
