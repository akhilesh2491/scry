package io.github.akhilesh2491.scry.logs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.akhilesh2491.scry.core.Scry
import io.github.akhilesh2491.scry.core.shareScryFile
import io.github.akhilesh2491.scry.ui.ScryDivider
import io.github.akhilesh2491.scry.ui.ScryPill
import io.github.akhilesh2491.scry.ui.ScrySearchField
import io.github.akhilesh2491.scry.ui.scryMonoStyle
import io.github.akhilesh2491.scry.ui.scryPalette
import io.github.akhilesh2491.scry.ui.scrySpacing

@Composable
internal fun LogScreen(plugin: LogPlugin) {
    val entries by plugin.entries.collectAsState()
    var query by remember { mutableStateOf("") }
    var minimumLevel by remember { mutableStateOf(LogLevel.VERBOSE) }

    val filtered = remember(entries, query, minimumLevel) {
        entries.filter { it.level >= minimumLevel && it.matches(query) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScrySearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Filter by tag or message",
                modifier = Modifier.weight(1f).padding(scrySpacing.md),
            )
            IconButton(
                enabled = filtered.isNotEmpty(),
                onClick = {
                    Scry.instance?.let { instance ->
                        shareScryFile(
                            context = instance.platformContext,
                            fileName = "scry-logs.txt",
                            // Oldest first in the export: a log file reads forwards.
                            text = filtered.asReversed().joinToString("\n") { it.format() },
                        )
                    }
                },
            ) { Icon(Icons.Default.Share, contentDescription = "Share logs") }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LogLevel.entries.forEach { level ->
                FilterChip(
                    selected = minimumLevel == level,
                    onClick = { minimumLevel = level },
                    label = { Text(level.label) },
                )
            }
        }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (entries.isEmpty()) "No logs yet." else "No matches.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            SelectionContainer {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(filtered, key = { it.id }) { entry ->
                        LogRow(entry)
                        ScryDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    var expanded by remember(entry.id) { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = entry.stackTrace != null) { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        ScryPill(entry.level.label, entry.level.color(), Modifier.padding(end = scrySpacing.sm))
        Column(Modifier.weight(1f)) {
            Text(
                entry.tag,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                entry.message,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            entry.stackTrace?.let { trace ->
                Text(
                    if (expanded) trace else trace.lineSequence().first() + "  (tap to expand)",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Level colours drawn from the shared palette.
 *
 * Same semantics as HTTP status colour elsewhere in Scry, so "orange means pay
 * attention" holds across every screen.
 */
@Composable
private fun LogLevel.color(): Color = when (this) {
    LogLevel.VERBOSE -> scryPalette.muted
    LogLevel.DEBUG -> scryPalette.info
    LogLevel.INFO -> scryPalette.success
    LogLevel.WARN -> scryPalette.warning
    LogLevel.ERROR, LogLevel.ASSERT -> scryPalette.danger
}
