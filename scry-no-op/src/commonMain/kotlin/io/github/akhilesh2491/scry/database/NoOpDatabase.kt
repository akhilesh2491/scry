@file:Suppress("UNUSED_PARAMETER", "unused")

package io.github.akhilesh2491.scry.database

import androidx.sqlite.SQLiteConnection
import io.github.akhilesh2491.scry.core.ScryPlugin
import io.github.akhilesh2491.scry.core.ScryScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Inert mirrors of `scry-database`.
 *
 * Keeps `androidx.sqlite` (interfaces only, a few KB) because [DriverSqlDatabase]
 * names `SQLiteConnection` in its public signature. It drops
 * `androidx.sqlite:sqlite-bundled`, which is the multi-megabyte native SQLite
 * build — along with every line of code that could read or write app data.
 */

public class ResultRow(public val values: List<String?>)

public class QueryResult(
    public val columns: List<String>,
    public val rows: List<ResultRow>,
    public val rowsAffected: Int = 0,
    public val error: String? = null,
) {
    public val isError: Boolean get() = error != null

    public companion object {
        public fun error(message: String): QueryResult =
            QueryResult(emptyList(), emptyList(), error = message)
    }
}

public class ColumnInfo(
    public val name: String,
    public val type: String,
    public val notNull: Boolean,
    public val primaryKey: Boolean,
    public val defaultValue: String?,
)

public interface SqlDatabase {
    public val name: String
    public val isWritable: Boolean
    public fun query(sql: String, bindings: List<String> = emptyList()): QueryResult
    public fun execute(sql: String, bindings: List<String> = emptyList()): QueryResult
    public fun close()
}

public fun interface DatabaseProvider {
    public fun databases(): List<SqlDatabase>
}

public class DriverSqlDatabase(
    override val name: String,
    connection: SQLiteConnection,
    override val isWritable: Boolean,
    ownsConnection: Boolean,
) : SqlDatabase {
    override fun query(sql: String, bindings: List<String>): QueryResult =
        QueryResult.error("Scry is the no-op artifact")

    override fun execute(sql: String, bindings: List<String>): QueryResult =
        QueryResult.error("Scry is the no-op artifact")

    override fun close(): Unit = Unit

    public companion object {
        public fun openFile(
            name: String,
            path: String,
            writable: Boolean = false,
        ): SqlDatabase = error("Scry is the no-op artifact; no database is opened")
    }
}

public class DatabasePluginBuilder internal constructor() {
    public fun autoDiscover(enabled: Boolean): Unit = Unit
    public fun allowFileModeWrites(allow: Boolean): Unit = Unit
    public fun database(database: SqlDatabase): Unit = Unit
    public fun provider(provider: DatabaseProvider): Unit = Unit
}

/** Discovers nothing. No file is opened, so nothing can be read or written. */
public class DatabasePlugin(
    configure: DatabasePluginBuilder.() -> Unit = {},
) : ScryPlugin {
    override val id: String = PLUGIN_ID
    override val displayName: String = "Database"
    override val badge: StateFlow<String?> = MutableStateFlow(null)

    public val databases: StateFlow<List<SqlDatabase>> = MutableStateFlow(emptyList())

    override fun onInstall(scope: ScryScope): Unit = Unit
    override fun onClear(): Unit = Unit
    public fun refresh(): Unit = Unit

    public companion object {
        public const val PLUGIN_ID: String = "scry.database"
    }
}
