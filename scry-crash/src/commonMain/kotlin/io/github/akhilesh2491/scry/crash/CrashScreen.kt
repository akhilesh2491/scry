package io.github.akhilesh2491.scry.crash

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.akhilesh2491.scry.ui.ScryDestructiveAction
import io.github.akhilesh2491.scry.ui.ScryDivider
import io.github.akhilesh2491.scry.ui.ScryEmptyState
import io.github.akhilesh2491.scry.ui.ScryScreenBar
import io.github.akhilesh2491.scry.ui.ScryShareAction

@Composable
internal fun CrashScreen(plugin: CrashPlugin) {
    val records by plugin.records.collectAsState()
    var selectedId by remember { mutableStateOf<String?>(null) }

    val selected = records.firstOrNull { it.id == selectedId }
    if (selected != null) {
        CrashDetail(selected, onBack = { selectedId = null })
        return
    }

    Column(Modifier.fillMaxSize()) {
        ScryScreenBar(
            title = if (records.isEmpty()) "Nothing captured" else "${records.size} captured",
            actions = {
                ScryShareAction(
                    fileName = "scry-crashes.txt",
                    enabled = records.isNotEmpty(),
                    description = "Share every report",
                    // Oldest last, matching the list, and separated so a reader
                    // can tell where one failure ends and the next begins.
                    text = { records.joinToString("\n\n${"-".repeat(60)}\n\n") { it.toReportText() } },
                )
                ScryDestructiveAction(
                    title = "Clear crash reports?",
                    message = "Removes the ${records.size} reports Scry has captured. " +
                        "Crash handlers stay installed.",
                    confirmLabel = "Clear",
                    description = "Clear crash reports",
                    enabled = records.isNotEmpty(),
                    onConfirm = { plugin.onClear() },
                )
            },
        )

        if (records.isEmpty()) {
            ScryEmptyState(
                title = "No crashes or ANRs captured.",
                hint = "Scry records them as they happen — there is nothing to do here yet.",
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(records, key = { it.id }) { record ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selectedId = record.id }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            record.kind.name,
                            color = when (record.kind) {
                                CrashKind.ANR -> MaterialTheme.colorScheme.tertiary
                                CrashKind.HANDLED -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.error
                            },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(end = 10.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(record.headline, maxLines = 2)
                            Text(
                                "${record.threadName} · ${record.sections.size} context sections",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    ScryDivider()
                }
            }
        }
    }
}

@Composable
private fun CrashDetail(record: CrashRecord, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScryScreenBar(
            title = record.kind.name,
            subtitle = record.headline,
            onBack = onBack,
            actions = {
                // The whole point of the plugin: one tap turns a crash into
                // something a developer can act on without a reproduction.
                ScryShareAction(
                    fileName = "scry-report-${record.timestampMillis}.txt",
                    description = "Share bug report",
                    text = { record.toReportText() },
                )
            },
        )
        SelectionContainer {
            Box(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                Text(
                    record.toReportText(),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
