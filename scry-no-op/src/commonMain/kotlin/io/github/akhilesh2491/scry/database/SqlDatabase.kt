package io.github.akhilesh2491.scry.database

/**
 * Top-level declarations must live in a file named like the real module's, so
 * the JVM facade class (`SqlDatabaseKt`) matches for Java callers. The parity
 * gate has now caught this same mistake four times — mirror file names whenever
 * a file carries public top-level declarations.
 */

public fun SqlDatabase.tableNames(): List<String> = emptyList()

public fun SqlDatabase.rowCount(table: String): Int? = null

public fun SqlDatabase.columns(table: String): List<ColumnInfo> = emptyList()

public fun quoteIdentifier(name: String): String = name
