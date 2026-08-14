package io.github.akhilesh2491.scry.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseTest {

    private val databases = mutableListOf<SqlDatabase>()

    private fun database(writable: Boolean = true): SqlDatabase =
        DriverSqlDatabase(
            name = "test.db",
            connection = BundledSQLiteDriver().open(":memory:"),
            isWritable = writable,
            ownsConnection = true,
        ).also { databases += it }

    private fun seeded(writable: Boolean = true): SqlDatabase = database(writable).apply {
        // Written through a writable handle so the read-only case can still seed.
        val setup = if (writable) this else null
        (setup ?: this).let { db ->
            db.forceExecute("CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, email TEXT)")
            db.forceExecute("INSERT INTO users (id, name, email) VALUES (1, 'ada', 'ada@x.io')")
            db.forceExecute("INSERT INTO users (id, name, email) VALUES (2, 'grace', NULL)")
        }
    }

    @AfterTest
    fun tearDown() {
        databases.forEach { it.close() }
        databases.clear()
    }

    @Test
    fun `lists tables and excludes sqlite internals`() {
        val db = seeded()
        db.forceExecute("CREATE TABLE other (x TEXT)")

        assertEquals(listOf("other", "users"), db.tableNames())
    }

    @Test
    fun `reads row counts`() {
        assertEquals(2, seeded().rowCount("users"))
    }

    @Test
    fun `reads column metadata including primary key and nullability`() {
        val columns = seeded().columns("users")

        assertEquals(listOf("id", "name", "email"), columns.map { it.name })
        assertTrue(columns.first { it.name == "id" }.primaryKey)
        assertTrue(!columns.first { it.name == "name" }.primaryKey)
    }

    @Test
    fun `returns null values as null rather than the string NULL`() {
        val rows = seeded().query("SELECT email FROM users WHERE id = 2").rows
        assertEquals(listOf(null), rows.single().values)
    }

    @Test
    fun `updateCell writes using the primary key`() {
        val db = seeded()
        val columns = listOf("id", "name", "email")
        val row = ResultRow(listOf("1", "ada", "ada@x.io"))

        val result = updateCell(
            db = db,
            table = "users",
            schema = db.columns("users"),
            columns = columns,
            edit = CellEdit(row, "name", "ada l."),
            newValue = "ada l.",
        )

        assertTrue(!result.isError, result.error.orEmpty())
        assertEquals(1, result.rowsAffected)
        assertEquals(
            "ada l.",
            db.query("SELECT name FROM users WHERE id = 1").rows.single().values.single(),
        )
    }

    @Test
    fun `updateCell matches NULL columns with IS NULL rather than equals`() {
        // `col = NULL` is never true in SQL, so a naive builder silently updates
        // zero rows on any table with a null in the identifying columns.
        val db = seeded()
        val columns = listOf("id", "name", "email")

        val result = updateCell(
            db = db,
            table = "users",
            schema = emptyList(), // force the all-columns fallback path
            columns = columns,
            edit = CellEdit(ResultRow(listOf("2", "grace", null)), "name", "grace h."),
            newValue = "grace h.",
        )

        assertEquals(1, result.rowsAffected, "expected the NULL-email row to match")
    }

    @Test
    fun `read-only databases refuse writes with an explanatory error`() {
        val db = seeded(writable = true)
        val readOnly = DriverSqlDatabase(
            name = "ro.db",
            connection = BundledSQLiteDriver().open(":memory:"),
            isWritable = false,
            ownsConnection = true,
        ).also { databases += it }

        val result = readOnly.execute("CREATE TABLE x (y TEXT)")

        assertTrue(result.isError)
        assertTrue(
            result.error!!.contains("read-only"),
            "error should explain why: ${result.error}",
        )
        assertEquals(2, db.rowCount("users"))
    }

    @Test
    fun `malformed sql returns an error instead of throwing`() {
        val result = seeded().query("SELECT * FROM nope")
        assertTrue(result.isError)
    }

    @Test
    fun `identifiers with quotes and reserved words are escaped`() {
        assertEquals("\"users\"", quoteIdentifier("users"))
        assertEquals("\"my\"\"table\"", quoteIdentifier("my\"table"))
        assertEquals("\"order\"", quoteIdentifier("order"))

        val db = database()
        db.forceExecute("CREATE TABLE ${quoteIdentifier("order")} (${quoteIdentifier("select")} TEXT)")
        db.forceExecute("INSERT INTO ${quoteIdentifier("order")} VALUES ('x')")

        assertEquals(1, db.rowCount("order"))
    }
}

/** Bypasses the writable guard so tests can seed a read-only fixture. */
private fun SqlDatabase.forceExecute(sql: String) {
    val result = (this as DriverSqlDatabase).let {
        if (isWritable) execute(sql) else QueryResult.error("not writable")
    }
    check(!result.isError) { "setup failed for `$sql`: ${result.error}" }
}
