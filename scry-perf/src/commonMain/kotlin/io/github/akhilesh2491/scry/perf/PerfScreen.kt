package io.github.akhilesh2491.scry.perf

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.akhilesh2491.scry.ui.ScryCard
import io.github.akhilesh2491.scry.ui.ScryDestructiveAction
import io.github.akhilesh2491.scry.ui.ScryEmptyState
import io.github.akhilesh2491.scry.ui.ScryKeyValue
import io.github.akhilesh2491.scry.ui.ScryPill
import io.github.akhilesh2491.scry.ui.ScryScreenBar
import io.github.akhilesh2491.scry.ui.ScryShareAction
import io.github.akhilesh2491.scry.ui.ScryShareFormat
import io.github.akhilesh2491.scry.ui.ScryStat
import io.github.akhilesh2491.scry.ui.ScryStatGrid
import io.github.akhilesh2491.scry.ui.scryPalette
import io.github.akhilesh2491.scry.ui.scrySpacing

/** How many recent frames the sparkline shows. */
private const val SPARKLINE_FRAMES = 120

/**
 * One sub-section of the current launch.
 *
 * The screen used to render all of these into a single scroll. Everything was
 * present and nothing was findable: five section headers, four charts and two
 * dozen numbers competing for the same column. Splitting them means each tab
 * answers one question.
 */
private enum class PerfSection(val label: String) {
    OVERVIEW("Overview"),
    STARTUP("Startup"),
    SCREENS("Screens"),
    FRAMES("Frames"),
    SPANS("Spans"),
}

@Composable
internal fun PerfScreen(plugin: PerfPlugin) {
    val session by plugin.session.collectAsState()
    val previous by plugin.previousSessions.collectAsState()
    val framesUnsupportedReason by plugin.framesUnsupportedReason.collectAsState()
    var tab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        ScryScreenBar(
            // The device and build these numbers came from, not a restatement of
            // the tab below — a startup time means nothing without them.
            title = session.deviceModel ?: session.platform,
            subtitle = session.appVersion,
            actions = {
                val all = listOf(session) + previous
                ScryShareAction(
                    // Both formats, because they answer different questions: JSON
                    // for a diff or a script, CSV for the spreadsheet next to last
                    // week's run.
                    formats = listOf(
                        ScryShareFormat("Export as JSON", "scry-perf-${session.id}.json") {
                            all.toJson()
                        },
                        ScryShareFormat("Export as CSV", "scry-perf-${session.id}.csv") {
                            all.toCsv()
                        },
                    ),
                    description = "Share performance data",
                )
                ScryDestructiveAction(
                    title = "Clear performance history?",
                    message = "Discards this launch's measurements and the " +
                        "${previous.size} earlier launches kept for comparison.",
                    confirmLabel = "Clear",
                    description = "Clear performance history",
                    onConfirm = { plugin.onClear() },
                )
            },
        )

        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("This launch") })
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = {
                    Text(if (previous.isEmpty()) "Sessions" else "Sessions ${previous.size}")
                },
            )
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
    // Only offer sections that hold something. A tab that always leads to "no
    // data" trains people to stop opening tabs.
    val sections = buildList {
        add(PerfSection.OVERVIEW)
        if (session.startup != null || session.hotStarts.isNotEmpty()) add(PerfSection.STARTUP)
        if (session.screens.isNotEmpty()) add(PerfSection.SCREENS)
        if (session.frames.isNotEmpty() || framesUnsupportedReason != null) add(PerfSection.FRAMES)
        if (session.spans.isNotEmpty()) add(PerfSection.SPANS)
    }

    // Overview is always present, so a lone section means nothing was measured.
    if (sections.size == 1) {
        ScryEmptyState(
            title = "Nothing measured yet.",
            hint = "Navigate around the app, or wrap work in ScryTrace.measure(\"name\") { }",
        )
        return
    }

    var section by remember(sections.size) { mutableIntStateOf(0) }
    val current = sections.getOrElse(section) { PerfSection.OVERVIEW }

    if (sections.size > 1) {
        ScrollableTabRow(selectedTabIndex = section, edgePadding = scrySpacing.md) {
            sections.forEachIndexed { index, entry ->
                Tab(
                    selected = section == index,
                    onClick = { section = index },
                    text = { Text(entry.label) },
                )
            }
        }
    }

    val budget = plugin.config.budget
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = scrySpacing.md),
        contentPadding = PaddingValues(vertical = scrySpacing.sm),
    ) {
        when (current) {
            PerfSection.OVERVIEW -> overviewSection(session)
            PerfSection.STARTUP -> startupSection(session, budget)
            PerfSection.SCREENS -> screensSection(session, budget)
            PerfSection.FRAMES -> framesSection(plugin, session, budget, framesUnsupportedReason)
            PerfSection.SPANS -> spansSection(session)
        }
    }
}

/** The numbers worth seeing before deciding which section to open. */
private fun LazyListScope.overviewSection(session: PerfSession) {
    if (session.violations.isNotEmpty()) {
        item {
            ScryCard(title = "Over budget") {
                session.violations.forEach { violation ->
                    Text(
                        text = violation.format(),
                        style = MaterialTheme.typography.bodySmall,
                        color = scryPalette.danger,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }

    item {
        ScryCard(title = "This launch") {
            ScryStatGrid {
                ScryStat(
                    label = "startup",
                    value = session.startup?.let { "${it.totalMillis} ms" } ?: "—",
                )
                ScryStat(
                    label = "jank",
                    value = "${session.overallJankPercent.roundTo(1)}%",
                    color = if (session.overallJankPercent > 0) scryPalette.warning else null,
                )
                ScryStat(label = "screens", value = session.screens.size.toString())
                ScryStat(
                    label = "frames",
                    value = session.frames.sumOf { it.frameCount }.toString(),
                )
                ScryStat(
                    label = "frozen",
                    value = session.frames.sumOf { it.frozenFrames }.toString(),
                    color = if (session.frames.any { it.frozenFrames > 0 }) scryPalette.danger else null,
                )
                ScryStat(label = "spans", value = session.spans.size.toString())
            }
        }
    }

    item {
        ScryCard(title = "Device") {
            ScryKeyValue("Platform", session.platform)
            session.deviceModel?.let { ScryKeyValue("Device", it) }
            session.appVersion?.let { ScryKeyValue("App version", it) }
            ScryKeyValue("Started", epochMillisToIso8601(session.startedAtMillis))
        }
    }
}

private fun LazyListScope.startupSection(
    session: PerfSession,
    budget: PerfBudget,
) {
    session.startup?.let { startup ->
        item { StartupCard(startup, budget) }
    }

    if (session.hotStarts.isNotEmpty()) {
        item {
            ScryCard(
                title = "Returns to foreground",
                subtitle = "${session.hotStarts.size} recorded",
            ) {
                session.hotStarts.asReversed().forEach { hot ->
                    ScryKeyValue(
                        label = epochMillisToIso8601(hot.timestampMillis),
                        value = "${hot.totalMillis} ms",
                    )
                }
            }
        }
    }
}

private fun LazyListScope.screensSection(
    session: PerfSession,
    budget: PerfBudget,
) {
    items(session.screens.asReversed()) { screen -> ScreenCard(screen, budget) }
}

private fun LazyListScope.framesSection(
    plugin: PerfPlugin,
    session: PerfSession,
    budget: PerfBudget,
    unsupportedReason: String?,
) {
    if (unsupportedReason != null) {
        item {
            ScryCard(title = "Frame timing unavailable") {
                Text(
                    text = "$unsupportedReason\nStartup, screen loads and spans still work.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scryPalette.muted,
                )
            }
        }
        return
    }

    items(session.frames) { stats ->
        FrameCard(stats, plugin.recentFrames(stats.screen, SPARKLINE_FRAMES), budget)
    }
}

private fun LazyListScope.spansSection(session: PerfSession) {
    item {
        ScryCard(title = "Spans", subtitle = "${session.spans.size} measured") {
            session.spans.asReversed().forEach { span ->
                ScryKeyValue(
                    label = "  ".repeat(span.depth) + span.name,
                    value = "${span.durationMillis} ms",
                )
            }
        }
    }
}

@Composable
private fun StartupCard(startup: StartupMetric, budget: PerfBudget) {
    ScryCard(
        title = "${startup.totalMillis} ms",
        subtitle = if (startup.truncated) "measured from Scry's earliest visible point" else null,
        trailing = {
            ScryPill(
                text = startup.kind.name,
                color = when (startup.kind) {
                    StartupKind.COLD -> scryPalette.info
                    StartupKind.WARM -> scryPalette.warning
                    StartupKind.HOT -> scryPalette.success
                },
            )
        },
    ) {
        BudgetBar(
            value = startup.totalMillis.toDouble(),
            limit = budget.coldStartupMillis?.toDouble()
                ?.takeIf { startup.kind == StartupKind.COLD },
            modifier = Modifier.padding(bottom = scrySpacing.md),
        )

        WaterfallBar(startup.phases, startup.totalMillis)
        Column(Modifier.padding(top = scrySpacing.sm)) {
            startup.phases.forEach { ScryKeyValue(it.name, "${it.durationMillis} ms") }
        }

        if (startup.truncated) {
            Text(
                text = "This excludes some of the real launch cost — Scry cannot observe the " +
                    "process before it is installed.",
                style = MaterialTheme.typography.labelSmall,
                color = scryPalette.muted,
                modifier = Modifier.padding(top = scrySpacing.xs),
            )
        }
    }
}

@Composable
private fun ScreenCard(metric: ScreenLoadMetric, budget: PerfBudget) {
    ScryCard(
        title = metric.screen,
        subtitle = metric.kind.name.lowercase(),
    ) {
        ScryStatGrid(Modifier.padding(bottom = scrySpacing.sm)) {
            ScryStat(label = "TTID", value = "${metric.ttidMillis} ms")
            metric.ttfdMillis?.let { ScryStat(label = "TTFD", value = "$it ms") }
        }
        BudgetBar(
            value = (metric.ttfdMillis ?: metric.ttidMillis).toDouble(),
            limit = budget.screenLoadMillis?.toDouble(),
        )
    }
}

@Composable
private fun FrameCard(stats: FrameStats, recent: List<Double>, budget: PerfBudget) {
    ScryCard(
        title = stats.screen,
        subtitle = "${stats.frameCount} frames · ${stats.budgetMillis.roundTo(1)} ms budget",
    ) {
        FrameSparkline(
            durationsMillis = recent,
            budgetMillis = stats.budgetMillis,
            modifier = Modifier.padding(bottom = scrySpacing.md),
        )

        ScryStatGrid {
            ScryStat(label = "p50", value = "${stats.p50Millis.roundTo(1)} ms")
            ScryStat(label = "p90", value = "${stats.p90Millis.roundTo(1)} ms")
            ScryStat(label = "p99", value = "${stats.p99Millis.roundTo(1)} ms")
            ScryStat(label = "worst", value = "${stats.worstMillis.roundTo(1)} ms")
            ScryStat(
                label = "slow",
                value = "${stats.slowFrames} (${stats.jankPercent.roundTo(1)}%)",
                color = budget.slowFramePercent
                    ?.takeIf { stats.jankPercent > it }
                    ?.let { scryPalette.danger },
            )
            ScryStat(
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

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = scrySpacing.md),
        contentPadding = PaddingValues(vertical = scrySpacing.sm),
    ) {
        item { SessionCard(current, comparison = null, isCurrent = true) }
        items(previous) { session ->
            SessionCard(session, comparison = current.diff(session), isCurrent = false)
        }
    }
}

@Composable
private fun SessionCard(
    session: PerfSession,
    comparison: PerfComparison?,
    isCurrent: Boolean,
) {
    ScryCard(
        title = epochMillisToIso8601(session.startedAtMillis),
        subtitle = session.appVersion,
        trailing = if (isCurrent) {
            { ScryPill("NOW", MaterialTheme.colorScheme.primary) }
        } else {
            null
        },
    ) {
        ScryStatGrid {
            ScryStat(
                label = "startup",
                value = session.startup?.let { "${it.totalMillis} ms" } ?: "—",
            )
            ScryStat(label = "jank", value = "${session.overallJankPercent.roundTo(1)}%")
            ScryStat(label = "screens", value = session.screens.size.toString())
        }

        // Deltas are stated from the current launch's point of view: negative
        // means this launch is faster than the one on this card.
        comparison?.startupDeltaMillis?.let { delta ->
            Text(
                text = if (delta <= 0) "$delta ms vs now" else "+$delta ms vs now",
                style = MaterialTheme.typography.labelSmall,
                color = if (delta <= 0) scryPalette.success else scryPalette.danger,
            )
        }
    }
}
