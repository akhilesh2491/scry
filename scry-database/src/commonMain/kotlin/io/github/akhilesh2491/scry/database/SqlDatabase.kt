package io.github.akhilesh2491.scry.database

/** One row of a query result: column values in the order [QueryResult.columns] lists them. */
public class ResultRow(public val values: List<String?>)

/** The outcome of running a statement. */
public class QueryResult(
    public val columns: List<String>,
    public val rows: List<ResultRow>,
    /** Rows changed, for statements that are not queries. */
    public val rowsAffected: Int = 0,
    public val error: String? = null,
) {
    public val isError: Boolean get() = error != null

    public companion object {
        public fun error(message: String): QueryResult =
            QueryResult(emptyList(), emptyList(), error = message)
    }
}

/** A column in a table's schema. */
public class ColumnInfo(
    public val name: String,
    public val type: String,
    public val notNull: Boolean,
    public val primaryKey: Boolean,
    public val defaultValue: String?,
)

/**
 * A SQLite database Scry can inspect.
 *
 * The abstraction exists so both access modes look identical to the UI:
 *
 * - **Instance mode** — the app hands Scry its own connection. No lock
 *   contention, no stale reads, and encrypted (SQLCipher) databases work because
 *   the app already holds the passphrase and Scry never sees it.
 * - **File mode** — Scry opens the file itself. Zero configuration, but a second
 *   writer on a non-WAL database can block, so it defaults to read-only.
 *
 * Instance mode is the recommended path. That is a correctness statement, not a
 * style preference.
 */
public interface SqlDatabase {

    public val name: String

    /**
     * Whether Scry may execute statements that modify data.
     *
     * File mode reports false unless the integrator explicitly opts in.
     */
    public val isWritable: Boolean

    /** Runs [sql] and returns rows, or an error. Never throws. */
    public fun query(sql: String, bindings: List<String> = emptyList()): QueryResult

    /** Runs a statement that changes data. Never throws. */
    public fun execute(sql: String, bindings: List<String> = emptyList()): QueryResult

    public fun close()
}

/** Supplies databases to the plugin. */
public fun interface DatabaseProvider {
    public fun databases(): List<SqlDatabase>
}

/** Table and view names, excluding SQLite's own bookkeeping tables. */
public fun SqlDatabase.tableNames(): List<String> {
    val result = query(
        "SELECT name FROM sqlite_master WHERE type IN ('table','view') " +
            "AND name NOT LIKE 'sqlite_%' ORDER BY name",
    )
    return result.rows.mapNotNull { it.values.firstOrNull() }
}

/** Row count for [table], or null if it cannot be determined. */
public fun SqlDatabase.rowCount(table: String): Int? =
    query("SELECT COUNT(*) FROM ${quoteIdentifier(table)}")
        .rows.firstOrNull()?.values?.firstOrNull()?.toIntOrNull()

/** Column metadata for [table]. */
public fun SqlDatabase.columns(table: String): List<ColumnInfo> =
    query("PRAGMA table_info(${quoteIdentifier(table)})").rows.mapNotNull { row ->
        // PRAGMA table_info: cid, name, type, notnull, dflt_value, pk
        val v = row.values
        if (v.size < 6) return@mapNotNull null
        ColumnInfo(
            name = v[1].orEmpty(),
            type = v[2].orEmpty().ifEmpty { "BLOB" },
            notNull = v[3] == "1",
            primaryKey = (v[5]?.toIntOrNull() ?: 0) > 0,
            defaultValue = v[4],
        )
    }

/**
 * Quotes an identifier for interpolation.
 *
 * Table and column names cannot be bound as parameters, so they have to be
 * interpolated — and this tool runs whatever table names the host app happens to
 * have. Double-quoting with internal quotes doubled is the SQLite-correct way to
 * keep a name like `my"table` or a reserved word from breaking the statement.
 */
public fun quoteIdentifier(name: String): String = "\"" + name.replace("\"", "\"\"") + "\""
