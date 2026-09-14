package io.github.akhilesh2491.scry.analytics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.akhilesh2491.scry.ui.ScryCard
import io.github.akhilesh2491.scry.ui.ScryDestructiveAction
import io.github.akhilesh2491.scry.ui.ScryDivider
import io.github.akhilesh2491.scry.ui.ScryEmptyState
import io.github.akhilesh2491.scry.ui.ScryKeyValue
import io.github.akhilesh2491.scry.ui.ScryPill
import io.github.akhilesh2491.scry.ui.ScryScreenBar
import io.github.akhilesh2491.scry.ui.ScrySearchField
import io.github.akhilesh2491.scry.ui.ScryShareAction
import io.github.akhilesh2491.scry.ui.ScryShareFormat
import io.github.akhilesh2491.scry.ui.scryPalette
import io.github.akhilesh2491.scry.ui.scrySpacing

/**
 * The three questions this screen answers, one per tab.
 *
 * Split rather than stacked for the same reason as the performance screen: a
 * single scroll holding a live event feed *and* a per-screen report means the
 * feed pushes the report out of view exactly when it turns red.
 */
private enum class AnalyticsSection(val label: String) {
    EVENTS("Events"),
    SCREENS("Screens"),
    ISSUES("Issues"),
}

@Composable
internal fun AnalyticsScreen(plugin: AnalyticsPlugin) {
    val events by plugin.events.collectAsState()
    val visits by plugin.visits.collectAsState()
    val issues by plugin.issues.collectAsState()
    val currentScreen by plugin.currentScreen.collectAsState()
    var tab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        ScryScreenBar(
            title = currentScreen,
            subtitle = "${events.size} events · ${issues.size} issues",
            actions = {
                ScryShareAction(
                    formats = listOf(
                        ScryShareFormat("Export as JSON", "scry-analytics.json") { visits.toJson() },
                        ScryShareFormat("Export as CSV", "scry-analytics.csv") { visits.toCsv() },
                    ),
                    enabled = events.isNotEmpty(),
                    description = "Share analytics",
                )
                ScryDestructiveAction(
                    title = "Clear analytics?",
                    message = "Removes the ${events.size} events Scry has captured. " +
                        "The app carries on reporting to its own analytics SDK.",
                    confirmLabel = "Clear",
                    description = "Clear analytics",
                    enabled = events.isNotEmpty(),
                    onConfirm = { plugin.onClear() },
                )
            },
        )

        TabRow(selectedTabIndex = tab) {
            AnalyticsSection.entries.forEachIndexed { index, section ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(section.label, style = MaterialTheme.typography.labelMedium) },
                )
            }
        }

        when (AnalyticsSection.entries[tab]) {
            AnalyticsSection.EVENTS -> EventsTab(events, currentScreen)
            AnalyticsSection.SCREENS -> ScreensTab(plugin, visits)
            AnalyticsSection.ISSUES -> IssuesTab(issues)
        }
    }
}

@Composable
private fun EventsTab(events: List<AnalyticsEvent>, currentScreen: String) {
    var query by remember { mutableStateOf("") }
    var thisScreenOnly by remember { mutableStateOf(false) }

    val filtered = remember(events, query, thisScreenOnly, currentScreen) {
        events.filter {
            it.matches(query) && (!thisScreenOnly || it.screen == currentScreen)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = scrySpacing.md, vertical = scrySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(scrySpacing.sm),
        ) {
            ScrySearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Filter by name, param or value",
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = thisScreenOnly,
                onClick = { thisScreenOnly = !thisScreenOnly },
                label = { Text("This screen") },
            )
        }

        if (filtered.isEmpty()) {
            ScryEmptyState(
                title = if (events.isEmpty()) "No analytics events yet." else "No matches.",
                hint = if (events.isEmpty()) {
                    "Call ScryAnalytics.track(name, params) from your analytics wrapper."
                } else {
                    null
                },
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { event ->
                    EventRow(event)
                    ScryDivider()
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: AnalyticsEvent) {
    var expanded by remember(event.id) { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth()
            .clickable(enabled = event.params.isNotEmpty()) { expanded = !expanded }
            .padding(horizontal = scrySpacing.md, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScryPill(
                event.screen,
                scryPalette.info,
                Modifier.padding(end = scrySpacing.sm),
            )
            Text(event.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                clockTime(event.timestampMillis),
                style = MaterialTheme.typography.labelSmall,
                color = scryPalette.muted,
            )
        }

        if (event.params.isEmpty()) {
            Text(
                "no parameters",
                style = MaterialTheme.typography.labelSmall,
                color = scryPalette.muted,
            )
        } else if (expanded) {
            Column(Modifier.padding(top = scrySpacing.xs)) {
                event.params.forEach { (key, value) ->
                    ScryKeyValue(label = key, value = "${value.display()}  (${value.typeName()})")
                }
                event.destination?.let { ScryKeyValue(label = "destination", value = it) }
            }
        } else {
            Text(
                // Collapsed: the parameters are the reason anyone opens this
                // screen, so a count would be useless. One line of the real
                // values, truncated, is what makes the list scannable.
                event.params.entries.joinToString(", ") { "${it.key}=${it.value.display()}" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ScreensTab(plugin: AnalyticsPlugin, visits: List<ScreenVisit>) {
    if (visits.all { it.events.isEmpty() } && plugin.spec.isEmpty) {
        ScryEmptyState(
            title = "No screens to report on yet.",
            hint = "Declare what a screen should emit with expect(\"Checkout\") { … }.",
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(scrySpacing.md),
    ) {
        items(visits, key = { it.id }) { visit ->
            VisitCard(plugin, visit)
        }
    }
}

@Composable
private fun VisitCard(plugin: AnalyticsPlugin, visit: ScreenVisit) {
    val expectation = plugin.spec.forScreen(visit.screen)

    ScryCard(
        title = visit.screen,
        subtitle = "${clockTime(visit.enteredAtMillis)} · ${visit.events.size} events",
        trailing = { ScryPill(visit.verdict.label(), visit.verdict.color()) },
    ) {
        if (expectation == null) {
            Text(
                "No expectation declared for this screen.",
                style = MaterialTheme.typography.bodySmall,
                color = scryPalette.muted,
            )
        } else {
            expectation.events.forEach { declared ->
                val fired = visit.events.count { it.name == declared.name }
                val failed = visit.issues.any { it.eventName == declared.name }
                val ok = fired > 0 && !failed
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (ok) "✓" else if (fired == 0 && !declared.required) "–" else "✕",
                        color = when {
                            ok -> scryPalette.success
                            fired == 0 && !declared.required -> scryPalette.muted
                            else -> scryPalette.danger
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = scrySpacing.sm),
                    )
                    Text(
                        declared.name,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (declared.params.isNotEmpty()) {
                        Text(
                            declared.params.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.labelSmall,
                            color = scryPalette.muted,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        visit.issues.forEach { issue ->
            Text(
                issue.format(),
                style = MaterialTheme.typography.bodySmall,
                color = scryPalette.danger,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        if (!visit.isSettled) {
            TextButton(onClick = { plugin.settleCurrentVisit() }) { Text("Check now") }
        }
    }
}

@Composable
private fun IssuesTab(issues: List<AnalyticsIssue>) {
    if (issues.isEmpty()) {
        ScryEmptyState(
            title = "No issues.",
            hint = "Every event so far matched the screen it fired on.",
        )
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(issues, key = { "${it.timestampMillis}-${it.kind}-${it.eventName}-${it.paramName}" }) { issue ->
            Column(Modifier.fillMaxWidth().padding(horizontal = scrySpacing.md, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScryPill(
                        issue.kind.name.replace('_', ' '),
                        scryPalette.danger,
                        Modifier.padding(end = scrySpacing.sm),
                    )
                    Text(
                        clockTime(issue.timestampMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = scryPalette.muted,
                    )
                }
                Text(
                    issue.format(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            ScryDivider()
        }
    }
}

private fun AnalyticsVerdict.label(): String = when (this) {
    AnalyticsVerdict.PENDING -> "WATCHING"
    AnalyticsVerdict.PASS -> "PASS"
    AnalyticsVerdict.FAIL -> "FAIL"
    AnalyticsVerdict.NO_SPEC -> "NO SPEC"
}

/**
 * Verdict colours from the shared palette.
 *
 * [AnalyticsVerdict.NO_SPEC] is muted, never green: an undeclared screen has not
 * passed anything, and colouring it like a pass is how it stays undeclared.
 */
@Composable
private fun AnalyticsVerdict.color(): Color = when (this) {
    AnalyticsVerdict.PENDING -> scryPalette.info
    AnalyticsVerdict.PASS -> scryPalette.success
    AnalyticsVerdict.FAIL -> scryPalette.danger
    AnalyticsVerdict.NO_SPEC -> scryPalette.muted
}
