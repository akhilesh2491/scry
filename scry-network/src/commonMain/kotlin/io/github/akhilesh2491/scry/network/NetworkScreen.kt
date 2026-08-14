package io.github.akhilesh2491.scry.network

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import io.github.akhilesh2491.scry.core.Scry
import io.github.akhilesh2491.scry.core.shareScryFile
import io.github.akhilesh2491.scry.ui.ScryCodeBlock
import io.github.akhilesh2491.scry.ui.ScryDivider
import io.github.akhilesh2491.scry.ui.ScryEmptyState
import io.github.akhilesh2491.scry.ui.ScryKeyValue
import io.github.akhilesh2491.scry.ui.ScryListRow
import io.github.akhilesh2491.scry.ui.ScryPill
import io.github.akhilesh2491.scry.ui.ScrySearchField
import io.github.akhilesh2491.scry.ui.ScrySectionHeader
import io.github.akhilesh2491.scry.ui.ScryStatusColors
import io.github.akhilesh2491.scry.ui.scryMonoStyle
import io.github.akhilesh2491.scry.ui.scryPalette
import io.github.akhilesh2491.scry.ui.scrySpacing

@Composable
internal fun NetworkScreen(plugin: NetworkPlugin) {
    var tab by remember { mutableIntStateOf(0) }
    val mockConfig by plugin.mocks.configuration.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Traffic") })
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = {
                    // Surface that mocking is on: a canned response you forgot
                    // about looks exactly like a backend that changed.
                    val active = plugin.mocks.isActive
                    Text(if (active) "Mocks •" else "Mocks")
                },
            )
        }
        when (tab) {
            0 -> TrafficTab(plugin)
            else -> MockScreen(plugin)
        }
    }
}

@Composable
private fun TrafficTab(plugin: NetworkPlugin) {
    val transactions by plugin.transactions.collectAsState()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    val selected = transactions.firstOrNull { it.id == selectedId }
    if (selected != null) {
        TransactionDetail(selected, onBack = { selectedId = null })
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScrySearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Filter by URL, method or status",
                modifier = Modifier.weight(1f).padding(scrySpacing.md),
            )
            IconButton(
                enabled = transactions.isNotEmpty(),
                onClick = {
                    // HAR imports into Charles, Proxyman, Insomnia and DevTools —
                    // the bridge from an on-device capture to desktop tooling.
                    Scry.instance?.let { instance ->
                        shareScryFile(
                            context = instance.platformContext,
                            fileName = "scry-session.har",
                            text = transactions.toHar(),
                        )
                    }
                },
            ) { Icon(Icons.Default.Share, contentDescription = "Export session as HAR") }
        }

        val filtered = remember(transactions, query) {
            if (query.isBlank()) {
                transactions
            } else {
                transactions.filter {
                    it.url.contains(query, ignoreCase = true) ||
                        it.statusLabel.contains(query, ignoreCase = true) ||
                        it.method.contains(query, ignoreCase = true)
                }
            }
        }

        if (filtered.isEmpty()) {
            if (transactions.isEmpty()) {
                ScryEmptyState(
                    title = "No requests captured yet.",
                    hint = "HttpClient { install(ScryKtor) }",
                )
            } else {
                ScryEmptyState(title = "No requests match \"$query\".")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { transaction ->
                    TransactionRow(transaction) { selectedId = transaction.id }
                    ScryDivider()
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(transaction: NetworkTransaction, onClick: () -> Unit) {
    ScryListRow(
        modifier = Modifier.clickable(onClick = onClick),
        leading = {
            ScryPill(
                text = transaction.statusLabel,
                // A transport failure has no status code, but it is not neutral —
                // forStatus(null) means "in flight", which this is not.
                color = if (transaction.error != null) {
                    scryPalette.danger
                } else {
                    ScryStatusColors.forStatus(transaction.responseCode)
                },
            )
        },
        title = "${transaction.method}  ${transaction.path}",
        subtitle = transaction.host,
        trailing = {
            transaction.durationMillis?.let {
                Text(
                    "$it ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            transaction.responseBodySize?.let {
                Text(formatBytes(it), style = MaterialTheme.typography.labelSmall, color = scryPalette.muted)
            }
        },
    )
}

@Composable
private fun TransactionDetail(transaction: NetworkTransaction, onBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to list")
            }
            Text(
                "${transaction.method} ${transaction.path}",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    Scry.instance?.let { instance ->
                        shareScryFile(
                            context = instance.platformContext,
                            fileName = "scry-request.sh",
                            text = transaction.toCurl(),
                        )
                    }
                },
            ) { Icon(Icons.Default.Share, contentDescription = "Share as cURL") }
        }
        TabRow(selectedTabIndex = tab) {
            listOf("Overview", "Request", "Response").forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
            }
        }
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
            when (tab) {
                0 -> OverviewTab(transaction)
                1 -> BodyTab(transaction.requestHeaders, transaction.requestBody, transaction.requestBodyTruncated)
                else -> BodyTab(transaction.responseHeaders, transaction.responseBody, transaction.responseBodyTruncated)
            }
        }
    }
}

@Composable
private fun OverviewTab(transaction: NetworkTransaction) {
    Column {
        ScryKeyValue("URL", transaction.url)
        ScryKeyValue("Method", transaction.method)
        ScryKeyValue("Status", transaction.responseMessage?.let { "${transaction.statusLabel} $it" } ?: transaction.statusLabel)
        ScryKeyValue("Secure", if (transaction.isSecure) "yes (https)" else "no (plaintext)")
        transaction.protocol?.let { ScryKeyValue("Protocol", it) }
        transaction.durationMillis?.let { ScryKeyValue("Duration", "$it ms") }
        transaction.requestBodySize?.let { ScryKeyValue("Request size", formatBytes(it)) }
        transaction.responseBodySize?.let { ScryKeyValue("Response size", formatBytes(it)) }
        transaction.error?.let { ScryKeyValue("Error", it) }
    }
}

@Composable
private fun BodyTab(headers: List<HttpHeader>, body: String?, truncated: Boolean) {
    Column {
        ScrySectionHeader("Headers")
        if (headers.isEmpty()) {
            Text("(none)", style = scryMonoStyle, color = scryPalette.muted)
        } else {
            headers.forEach { ScryKeyValue(it.name, it.value) }
        }

        ScrySectionHeader("Body")
        when {
            body.isNullOrEmpty() -> Text("(empty)", style = scryMonoStyle, color = scryPalette.muted)
            else -> {
                ScryCodeBlock(prettyPrintJson(body))
                if (truncated) {
                    Text(
                        "… truncated by Scry (see NetworkPlugin.maxBodyBytes)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            key,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f),
        )
        SelectionContainer(Modifier.weight(0.65f)) {
            Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}
