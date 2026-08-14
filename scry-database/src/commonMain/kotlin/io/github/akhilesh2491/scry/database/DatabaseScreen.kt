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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (table != null) table = null else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                table ?: db.name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
        }
        TabRow(selectedTabIndex = tab) {
            listOf("Tables", "SQL").forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
            }
        }
        when (tab) {
            0 -> table?.let { TableRows(db, it) } ?: TableList(db) { table = it }
            else -> SqlConsole(db)
        }
    }
}

@Composable
private fun TableList(db: SqlDatabase, onSelect: (String) -> Unit) {
    val tables = remember(db) { db.tableNames() }
    if (tables.isEmpty()) {
        Centered("No tables.")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(tables, key = { it }) { name ->
            val count = remember(db, name) { db.rowCount(name) }
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
private fun TableRows(db: SqlDatabase, table: String) {
    var revision by remember(table) { mutableIntStateOf(0) }
    var page by remember(table) { mutableIntStateOf(0) }
    var editing by remember(table) { mutableStateOf<CellEdit?>(null) }

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
                Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
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
                        Row {
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
                if (!outcome.isError) revision++
            },
        )
    }
}

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

/**
 * Writes one cell back.
 *
 * Identifies the row by primary key where there is one, and otherwise by
 * matching every column value. The fallback is imperfect on duplicate rows —
 * hence the explicit `LIMIT`-free `WHERE` on all columns and the row count
 * returned to the caller, so a surprising number of affected rows is visible
 * rather than silent.
 */
internal fun updateCell(
    db: SqlDatabase,
    table: String,
    schema: List<ColumnInfo>,
    columns: List<String>,
    edit: CellEdit,
    newValue: String,
): QueryResult {
    val primaryKeys = schema.filter { it.primaryKey }.map { it.name }
    val identifying = if (primaryKeys.isNotEmpty()) primaryKeys else columns

    val bindings = mutableListOf(newValue)
    val predicates = mutableListOf<String>()
    identifying.forEach { column ->
        val index = columns.indexOf(column)
        if (index < 0) return@forEach
        val value = edit.row.values.getOrNull(index)
        if (value == null) {
            predicates += "${quoteIdentifier(column)} IS NULL"
        } else {
            predicates += "${quoteIdentifier(column)} = ?"
            bindings += value
        }
    }
    if (predicates.isEmpty()) {
        return QueryResult.error("Cannot identify this row — no primary key and no columns.")
    }

    return db.execute(
        "UPDATE ${quoteIdentifier(table)} SET ${quoteIdentifier(edit.column)} = ? " +
            "WHERE ${predicates.joinToString(" AND ")}",
        bindings,
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
        Row {
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
