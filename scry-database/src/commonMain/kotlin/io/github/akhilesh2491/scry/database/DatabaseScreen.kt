package io.github.akhilesh2491.scry.database

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.akhilesh2491.scry.ui.ScryDestructiveAction
import io.github.akhilesh2491.scry.ui.ScryScreenBar
import io.github.akhilesh2491.scry.ui.ScryShareAction

private const val PAGE_SIZE = 100

@Composable
internal fun DatabaseScreen(plugin: DatabasePlugin) {
    val databases by plugin.databases.collectAsState()
    var selected by remember { mutableStateOf<SqlDatabase?>(null) }

    LaunchedEffect(selected) { if (selected == null) plugin.refresh() }

    val db = selected
    when {
        db == null -> DatabaseList(databases) { selected = it }
        else -> DatabaseDetail(db, onBack = { selected = null })
    }
}

@Composable
private fun DatabaseList(databases: List<SqlDatabase>, onSelect: (SqlDatabase) -> Unit) {
    if (databases.isEmpty()) {
        Centered("No databases found.")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(databases, key = { it.name }) { db ->
            Column(
                Modifier.fillMaxWidth().clickable { onSelect(db) }.padding(16.dp),
            ) {
                Text(db.name)
                Text(
                    "${db.tableNames().size} tables" +
                        if (db.isWritable) "" else " · read-only",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun DatabaseDetail(db: SqlDatabase, onBack: () -> Unit) {
    var tab by remember(db) { mutableIntStateOf(0) }
    var table by remember(db) { mutableStateOf<String?>(null) }

    // Bumped by any mutation so the open table re-queries. Held here rather than
    // inside TableRows so a "delete all rows" launched from the header reaches
    // the grid below it.
    var revision by remember(db) { mutableIntStateOf(0) }

    // Only while the Tables tab is showing it: the SQL console has its own
    // export, and offering "delete all rows in users" from a screen displaying
    // an unrelated query result is how the wrong table gets emptied.
    val openTable = table.takeIf { tab == 0 }

    Column(Modifier.fillMaxSize()) {
        ScryScreenBar(
            title = openTable ?: db.name,
            subtitle = if (db.isWritable) null else "read-only",
            onBack = { if (table != null) table = null else onBack() },
            actions = {
                if (openTable != null) {
                    TableActions(db, openTable, revision) { revision++ }
                }
            },
        )
        TabRow(selectedTabIndex = tab) {
            listOf("Tables", "SQL").forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
            }
        }
        when (tab) {
            0 -> openTable
                ?.let { TableRows(db, it, revision) { revision++ } }
                ?: TableList(db, revision) { table = it }
            else -> SqlConsole(db)
        }
    }
}

/**
 * Export and bulk-delete for the open table.
 *
 * The export runs the query itself rather than reusing the page on screen: an
 * export that silently stopped at row 100 because that is what was scrolled into
 * view is worse than no export.
 */
@Composable
private fun TableActions(
    db: SqlDatabase,
    table: String,
    revision: Int,
    onChanged: () -> Unit,
) {
    val rows = remember(db, table, revision) { db.rowCount(table) }

    ScryShareAction(
        fileName = "scry-$table.csv",
        enabled = (rows ?: 0) > 0,
        description = "Export table as CSV",
        text = { db.query("SELECT * FROM ${quoteIdentifier(table)}").csvExport() },
    )
    if (db.isWritable) {
        ScryDestructiveAction(
            title = "Delete all rows in $table?",
            message = "Removes ${rows ?: "every"} rows from the app's own database. " +
                "Scry cannot undo this.",
            confirmLabel = "Delete all",
            description = "Delete all rows",
            enabled = (rows ?: 0) > 0,
            onConfirm = {
                db.execute("DELETE FROM ${quoteIdentifier(table)}")
                onChanged()
            },
        )
    }
}

@Composable
private fun TableList(db: SqlDatabase, revision: Int, onSelect: (String) -> Unit) {
    val tables = remember(db, revision) { db.tableNames() }
    if (tables.isEmpty()) {
        Centered("No tables.")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(tables, key = { it }) { name ->
            val count = remember(db, name, revision) { db.rowCount(name) }
            Row(
                Modifier.fillMaxWidth().clickable { onSelect(name) }.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(name)
                Text(
                    count?.let { "$it rows" } ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun TableRows(db: SqlDatabase, table: String, revision: Int, onChanged: () -> Unit) {
    var page by remember(table) { mutableIntStateOf(0) }
    var editing by remember(table) { mutableStateOf<CellEdit?>(null) }
    var deleting by remember(table) { mutableStateOf<ResultRow?>(null) }

    val schema = remember(db, table) { db.columns(table) }
    val result = remember(db, table, page, revision) {
        db.query(
            "SELECT * FROM ${quoteIdentifier(table)} LIMIT $PAGE_SIZE OFFSET ${page * PAGE_SIZE}",
        )
    }

    if (result.isError) {
        Centered("Query failed: ${result.error}")
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "rows ${page * PAGE_SIZE + 1}–${page * PAGE_SIZE + result.rows.size}",
                style = MaterialTheme.typography.labelSmall,
            )
            Row {
                TextButton(enabled = page > 0, onClick = { page-- }) { Text("Prev") }
                TextButton(
                    enabled = result.rows.size == PAGE_SIZE,
                    onClick = { page++ },
                ) { Text("Next") }
            }
        }

        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            Column {
                Row(
                    Modifier.background(MaterialTheme.colorScheme.surfaceVariant),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Leading gutter aligning the header with the delete column.
                    if (db.isWritable) Box(Modifier.width(DELETE_COLUMN_WIDTH))
                    result.columns.forEach { column ->
                        val info = schema.firstOrNull { it.name == column }
                        Text(
                            text = column + if (info?.primaryKey == true) " ⚿" else "",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(160.dp).padding(8.dp),
                        )
                    }
                }
                HorizontalDivider()
                LazyColumn {
                    items(result.rows.size) { rowIndex ->
                        val row = result.rows[rowIndex]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // A dedicated column rather than a long-press: tapping
                            // a cell already means "edit it", and overloading the
                            // same row with a hidden destructive gesture is how
                            // someone deletes a row they meant to inspect.
                            if (db.isWritable) {
                                IconButton(
                                    onClick = { deleting = row },
                                    modifier = Modifier.width(DELETE_COLUMN_WIDTH),
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Delete row ${rowIndex + 1}",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            row.values.forEachIndexed { colIndex, value ->
                                val column = result.columns.getOrNull(colIndex).orEmpty()
                                Text(
                                    text = value ?: "NULL",
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (value == null) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    modifier = Modifier
                                        .width(160.dp)
                                        .let {
                                            if (db.isWritable) {
                                                it.clickable {
                                                    editing = CellEdit(row, column, value)
                                                }
                                            } else {
                                                it
                                            }
                                        }
                                        .padding(8.dp),
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    editing?.let { edit ->
        CellEditDialog(
            edit = edit,
            onDismiss = { editing = null },
            onSave = { newValue ->
                val outcome = updateCell(db, table, schema, result.columns, edit, newValue)
                editing = null
                if (!outcome.isError) onChanged()
            },
        )
    }

    deleting?.let { row ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this row?") },
            text = {
                Column {
                    Text(
                        "Removes it from the app's own database. Scry cannot undo this.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    // Show what is about to go. Without a primary key the row is
                    // matched on every column, and seeing the values is the only
                    // way to tell that duplicates would all match.
                    Text(
                        result.columns.zip(row.values)
                            .joinToString("\n") { (column, value) -> "$column = ${value ?: "NULL"}" },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val outcome = deleteRow(db, table, schema, result.columns, row)
                    deleting = null
                    if (!outcome.isError) onChanged()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

/** Width of the leading delete gutter, matching an [IconButton]'s touch target. */
private val DELETE_COLUMN_WIDTH = 44.dp

internal class CellEdit(
    val row: ResultRow,
    val column: String,
    val value: String?,
)

@Composable
private fun CellEditDialog(
    edit: CellEdit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember(edit) { mutableStateOf(edit.value.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(edit.column) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A `WHERE` clause and its bindings, or null when the row cannot be pinned down. */
internal class RowPredicate(val sql: String, val bindings: List<String>)

/**
 * Builds the clause that identifies one row.
 *
 * By primary key where there is one, and otherwise by matching every column
 * value. The fallback is imperfect on duplicate rows — hence the `LIMIT`-free
 * `WHERE` on all columns and the affected-row count returned to callers, so a
 * surprising number of matches is visible rather than silent.
 *
 * Shared by update and delete on purpose: a DELETE that identified rows even
 * slightly differently from the UPDATE beside it would eventually remove a row
 * the user had just edited a different one of.
 */
internal fun identifyingPredicate(
    schema: List<ColumnInfo>,
    columns: List<String>,
    row: ResultRow,
): RowPredicate? {
    val primaryKeys = schema.filter { it.primaryKey }.map { it.name }
    val identifying = if (primaryKeys.isNotEmpty()) primaryKeys else columns

    val bindings = mutableListOf<String>()
    val predicates = mutableListOf<String>()
    identifying.forEach { column ->
        val index = columns.indexOf(column)
        if (index < 0) return@forEach
        val value = row.values.getOrNull(index)
        if (value == null) {
            predicates += "${quoteIdentifier(column)} IS NULL"
        } else {
            predicates += "${quoteIdentifier(column)} = ?"
            bindings += value
        }
    }
    if (predicates.isEmpty()) return null

    return RowPredicate(predicates.joinToString(" AND "), bindings)
}

/** Writes one cell back. */
internal fun updateCell(
    db: SqlDatabase,
    table: String,
    schema: List<ColumnInfo>,
    columns: List<String>,
    edit: CellEdit,
    newValue: String,
): QueryResult {
    val predicate = identifyingPredicate(schema, columns, edit.row)
        ?: return QueryResult.error("Cannot identify this row — no primary key and no columns.")

    return db.execute(
        "UPDATE ${quoteIdentifier(table)} SET ${quoteIdentifier(edit.column)} = ? " +
            "WHERE ${predicate.sql}",
        listOf(newValue) + predicate.bindings,
    )
}

/** Removes one row, identified exactly as [updateCell] would identify it. */
internal fun deleteRow(
    db: SqlDatabase,
    table: String,
    schema: List<ColumnInfo>,
    columns: List<String>,
    row: ResultRow,
): QueryResult {
    val predicate = identifyingPredicate(schema, columns, row)
        ?: return QueryResult.error("Cannot identify this row — no primary key and no columns.")

    return db.execute(
        "DELETE FROM ${quoteIdentifier(table)} WHERE ${predicate.sql}",
        predicate.bindings,
    )
}

@Composable
private fun SqlConsole(db: SqlDatabase) {
    var sql by remember(db) { mutableStateOf("") }
    var result by remember(db) { mutableStateOf<QueryResult?>(null) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            value = sql,
            onValueChange = { sql = it },
            label = { Text("SQL") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                enabled = sql.isNotBlank(),
                onClick = {
                    val trimmed = sql.trim()
                    // SELECT/PRAGMA/EXPLAIN read; everything else writes and is
                    // routed through execute() so the read-only guard applies.
                    result = if (trimmed.startsWithAnyIgnoreCase("select", "pragma", "explain", "with")) {
                        db.query(trimmed)
                    } else {
                        db.execute(trimmed)
                    }
                },
            ) { Text("Run") }
            ScryShareAction(
                fileName = "scry-query.csv",
                enabled = result?.rows?.isNotEmpty() == true,
                description = "Export result as CSV",
                label = "Export",
                text = { result?.csvExport().orEmpty() },
            )
        }

        result?.let { r ->
            when {
                r.isError -> Text(
                    r.error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
                r.columns.isEmpty() -> Text("${r.rowsAffected} row(s) affected")
                else -> Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                    Column {
                        Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                            r.columns.forEach {
                                Text(
                                    it,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.width(160.dp).padding(6.dp),
                                )
                            }
                        }
                        LazyColumn {
                            items(r.rows.size) { index ->
                                Row {
                                    r.rows[index].values.forEach { value ->
                                        Text(
                                            value ?: "NULL",
                                            fontFamily = FontFamily.Monospace,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            modifier = Modifier.width(160.dp).padding(6.dp),
                                        )
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun String.startsWithAnyIgnoreCase(vararg prefixes: String): Boolean =
    prefixes.any { startsWith(it, ignoreCase = true) }

@Composable
private fun Centered(message: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
