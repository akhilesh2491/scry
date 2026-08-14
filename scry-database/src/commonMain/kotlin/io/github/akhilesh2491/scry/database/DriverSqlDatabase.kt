package io.github.akhilesh2491.scry.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/**
 * A [SqlDatabase] backed by an `androidx.sqlite` connection.
 *
 * Used for file mode, and available to integrators who already hold a
 * [SQLiteConnection] and want to pass it in (instance mode) rather than let
 * Scry open a second one.
 */
public class DriverSqlDatabase(
    override val name: String,
    private val connection: SQLiteConnection,
    override val isWritable: Boolean,
    private val ownsConnection: Boolean,
) : SqlDatabase {

    override fun query(sql: String, bindings: List<String>): QueryResult = runCatching {
        connection.prepare(sql).use { statement ->
            bindings.forEachIndexed { index, value -> statement.bindText(index + 1, value) }
            readAll(statement)
        }
    }.getOrElse { QueryResult.error(it.message ?: it::class.simpleName ?: "query failed") }

    override fun execute(sql: String, bindings: List<String>): QueryResult {
        if (!isWritable) {
            return QueryResult.error(
                "This database is open read-only. Scry opened the file directly, and a second " +
                    "writer can block the app; pass the app's own connection to enable writes.",
            )
        }
        return runCatching {
            connection.prepare(sql).use { statement ->
                bindings.forEachIndexed { index, value -> statement.bindText(index + 1, value) }
                statement.step()
            }
            val changes = connection.prepare("SELECT changes()").use {
                if (it.step()) it.getInt(0) else 0
            }
            QueryResult(emptyList(), emptyList(), rowsAffected = changes)
        }.getOrElse { QueryResult.error(it.message ?: "statement failed") }
    }

    private fun readAll(statement: SQLiteStatement): QueryResult {
        val rows = mutableListOf<ResultRow>()
        var columns: List<String> = emptyList()
        var first = true
        while (statement.step()) {
            if (first) {
                columns = (0 until statement.getColumnCount()).map { statement.getColumnName(it) }
                first = false
            }
            rows += ResultRow(
                (0 until statement.getColumnCount()).map { index ->
                    if (statement.isNull(index)) null else statement.getText(index)
                },
            )
        }
        if (first) {
            // No rows stepped, so column names were never read. Ask anyway, so an
            // empty table still renders its headers instead of a blank screen.
            columns = runCatching {
                (0 until statement.getColumnCount()).map { statement.getColumnName(it) }
            }.getOrDefault(emptyList())
        }
        return QueryResult(columns, rows)
    }

    override fun close() {
        if (ownsConnection) connection.close()
    }

    public companion object {
        /**
         * Opens a database file directly.
         *
         * [writable] defaults to false on purpose: opening a second connection to
         * a database the app is actively using risks `SQLITE_BUSY` on a non-WAL
         * file, and a debugger that deadlocks the app it is debugging is worse
         * than no debugger.
         */
        public fun openFile(
            name: String,
            path: String,
            writable: Boolean = false,
        ): SqlDatabase = DriverSqlDatabase(
            name = name,
            connection = BundledSQLiteDriver().open(path),
            isWritable = writable,
            ownsConnection = true,
        )
    }
}
