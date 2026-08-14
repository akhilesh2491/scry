package io.github.akhilesh2491.scry.perf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.akhilesh2491.scry.core.Scry
import io.github.akhilesh2491.scry.core.shareScryFile
import io.github.akhilesh2491.scry.ui.ScryDivider
import io.github.akhilesh2491.scry.ui.ScryEmptyState
import io.github.akhilesh2491.scry.ui.ScryKeyValue
import io.github.akhilesh2491.scry.ui.ScryPill
import io.github.akhilesh2491.scry.ui.ScrySectionHeader
import io.github.akhilesh2491.scry.ui.scryPalette
import io.github.akhilesh2491.scry.ui.scrySpacing

/** How many recent frames the sparkline shows. */
private const val SPARKLINE_FRAMES = 120

@Composable
internal fun PerfScreen(plugin: PerfPlugin) {
    val session by plugin.session.collectAsState()
    val previous by plugin.previousSessions.collectAsState()
    val framesUnsupportedReason by plugin.framesUnsupportedReason.collectAsState()
    var tab by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TabRow(selectedTabIndex = tab, modifier = Modifier.weight(1f)) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("This launch") })
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(if (previous.isEmpty()) "Sessions" else "Sessions ${previous.size}") },
                )
            }
            IconButton(
                onClick = {
                    Scry.instance?.let { instance ->
                        val all = listOf(session) + previous
                        shareScryFile(
                            context = instance.platformContext,
                            fileName = "scry-perf-${session.id}.json",
                            text = all.toJson(),
                        )
                    }
                },
            ) { Icon(Icons.Default.Share, contentDescription = "Share performance data") }
        }

        when (tab) {
            0 -> CurrentSessionTab(plugin, session, framesUnsupportedReason)
            else -> SessionsTab(session, previous)
        }
    }
}

@Composable
private fun CurrentSessionTab(
    plugin: PerfPlugin,
    session: PerfSession,
    framesUnsupportedReason: String?,
) {
    val budget = plugin.config.budget
    val hasAnything = session.startup != null ||
        session.screens.isNotEmpty() ||
        session.frames.isNotEmpty() ||
        session.spans.isNotEmpty()

    if (!hasAnything) {
        ScryEmptyState(
            title = "Nothing measured yet.",
            hint = "Navigate around the app, or wrap work in ScryTrace.measure(\"name\") { }",
        )
        return
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = scrySpacing.md)) {
        session.violations.takeIf { it.isNotEmpty() }?.let { violations ->
            item { ScrySectionHeader("Over budget") }
            items(violations) { violation ->
                Text(
                    text = violation.format(),
                    style = MaterialTheme.typography.bodySmall,
                    color = scryPalette.danger,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }

        session.startup?.let { startup ->
            item { ScrySectionHeader("Startup") }
            item { StartupCard(startup, budget) }
        }

        if (session.hotStarts.isNotEmpty()) {
            item { ScrySectionHeader("Returns to foreground") }
            items(session.hotStarts.asReversed()) { hot ->
                ScryKeyValue(
                    label = epochMillisToIso8601(hot.timestampMillis),
                    value = "${hot.totalMillis} ms",
                )
            }
        }

        if (session.screens.isNotEmpty()) {
            item { ScrySectionHeader("Screen load") }
            items(session.screens.asReversed()) { screen ->
                ScreenRow(screen, budget)
                ScryDivider()
            }
        }

        item { ScrySectionHeader("Frames") }
        if (framesUnsupportedReason != null) {
            item {
                Text(
                    text = "$framesUnsupportedReason\nStartup, screen loads and spans still work.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scryPalette.muted,
                    modifier = Modifier.padding(vertical = scrySpacing.sm),
                )
            }
        } else if (session.frames.isEmpty()) {
            item {
                Text(
                    text = "No frames drawn yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scryPalette.muted,
                    modifier = Modifier.padding(vertical = scrySpacing.sm),
                )
            }
        } else {
            items(session.frames) { stats ->
                FrameCard(stats, plugin.recentFrames(stats.screen, SPARKLINE_FRAMES), budget)
                ScryDivider()
            }
        }

        if (session.spans.isNotEmpty()) {
            item { ScrySectionHeader("Spans") }
            items(session.spans.asReversed()) { span ->
                ScryKeyValue(
                    label = "  ".repeat(span.depth) + span.name,
                    value = "${span.durationMillis} ms",
                )
            }
        }

        item {
            ScrySectionHeader("Device")
            ScryKeyValue("Platform", session.platform)
            session.deviceModel?.let { ScryKeyValue("Device", it) }
            session.appVersion?.let { ScryKeyValue("App version", it) }
        }
    }
}

@Composable
private fun StartupCard(startup: StartupMetric, budget: PerfBudget) {
    Column(Modifier.fillMaxWidth().padding(vertical = scrySpacing.sm)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(scrySpacing.sm),
        ) {
            ScryPill(
                text = startup.kind.name,
                color = when (startup.kind) {
                    StartupKind.COLD -> scryPalette.info
                    StartupKind.WARM -> scryPalette.warning
                    StartupKind.HOT -> scryPalette.success
                },
            )
            Text(
                text = "${startup.totalMillis} ms",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        BudgetBar(
            value = startup.totalMillis.toDouble(),
            limit = budget.coldStartupMillis?.toDouble()?.takeIf { startup.kind == StartupKind.COLD },
            modifier = Modifier.padding(vertical = scrySpacing.xs),
        )

        WaterfallBar(startup.phases, startup.totalMillis)
        startup.phases.forEach {
            ScryKeyValue(it.name, "${it.durationMillis} ms")
        }

        if (startup.truncated) {
            Text(
                text = "Measured from the earliest point Scry can observe — " +
                    "this excludes some of the real launch cost.",
                style = MaterialTheme.typography.labelSmall,
                color = scryPalette.muted,
            )
        }
    }
}

@Composable
private fun ScreenRow(metric: ScreenLoadMetric, budget: PerfBudget) {
    Column(Modifier.fillMaxWidth().padding(vertical = scrySpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(metric.screen, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            StatCell("TTID", "${metric.ttidMillis} ms")
            metric.ttfdMillis?.let { StatCell("TTFD", "$it ms") }
        }
        BudgetBar(
            value = (metric.ttfdMillis ?: metric.ttidMillis).toDouble(),
            limit = budget.screenLoadMillis?.toDouble(),
        )
    }
}

@Composable
private fun FrameCard(stats: FrameStats, recent: List<Double>, budget: PerfBudget) {
    Column(Modifier.fillMaxWidth().padding(vertical = scrySpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stats.screen, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                text = "${stats.frameCount} frames @ ${stats.budgetMillis.roundTo(1)} ms budget",
                style = MaterialTheme.typography.labelSmall,
                color = scryPalette.muted,
            )
        }

        FrameSparkline(recent, stats.budgetMillis, Modifier.padding(vertical = scrySpacing.xs))

        Row(Modifier.fillMaxWidth()) {
            StatCell("p50", stats.p50Millis.roundTo(1))
            StatCell("p90", stats.p90Millis.roundTo(1))
            StatCell("p99", stats.p99Millis.roundTo(1))
            StatCell("worst", stats.worstMillis.roundTo(1))
        }
        Row(Modifier.fillMaxWidth()) {
            StatCell(
                label = "slow",
                value = "${stats.slowFrames} (${stats.jankPercent.roundTo(1)}%)",
                color = budget.slowFramePercent
                    ?.takeIf { stats.jankPercent > it }
                    ?.let { scryPalette.danger },
            )
            StatCell(
                label = "frozen",
                value = stats.frozenFrames.toString(),
                color = if (stats.frozenFrames > 0) scryPalette.danger else null,
            )
        }
    }
}

@Composable
private fun SessionsTab(current: PerfSession, previous: List<PerfSession>) {
    if (previous.isEmpty()) {
        ScryEmptyState(
            title = "No earlier launches recorded.",
            hint = "Relaunch the app — this session is kept for comparison.",
        )
        return
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = scrySpacing.md)) {
        item {
            ScrySectionHeader("This launch")
            SessionRow(current, comparison = null)
            ScryDivider()
            ScrySectionHeader("Earlier")
        }
        items(previous) { session ->
            SessionRow(session, comparison = current.diff(session))
            ScryDivider()
        }
    }
}

@Composable
private fun SessionRow(session: PerfSession, comparison: PerfComparison?) {
    Column(Modifier.fillMaxWidth().padding(vertical = scrySpacing.sm)) {
        Text(
            text = epochMillisToIso8601(session.startedAtMillis),
            style = MaterialTheme.typography.bodySmall,
        )
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            StatCell(
                label = "startup",
                value = session.startup?.let { "${it.totalMillis} ms" } ?: "—",
            )
            StatCell(label = "jank", value = "${session.overallJankPercent.roundTo(1)}%")
            StatCell(label = "screens", value = session.screens.size.toString())
        }

        // Deltas are stated from the current launch's point of view: negative
        // means this launch is faster than the one on this row.
        comparison?.startupDeltaMillis?.let { delta ->
            Text(
                text = if (delta <= 0) "$delta ms vs now" else "+$delta ms vs now",
                style = MaterialTheme.typography.labelSmall,
                color = if (delta <= 0) scryPalette.success else scryPalette.danger,
            )
        }
    }
}
