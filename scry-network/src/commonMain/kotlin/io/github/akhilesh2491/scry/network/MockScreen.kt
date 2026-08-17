package io.github.akhilesh2491.scry.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.akhilesh2491.scry.ui.ScryDestructiveAction
import io.github.akhilesh2491.scry.ui.ScryDivider
import io.github.akhilesh2491.scry.ui.ScryEmptyState
import io.github.akhilesh2491.scry.ui.ScryPill
import io.github.akhilesh2491.scry.ui.ScrySectionHeader
import io.github.akhilesh2491.scry.ui.ScryShareAction
import io.github.akhilesh2491.scry.ui.scryMonoStyle
import io.github.akhilesh2491.scry.ui.scryPalette
import io.github.akhilesh2491.scry.ui.scrySpacing

/**
 * Controls for mocking, throttling and offline simulation.
 *
 * Shown as a tab beside the transaction list, so the rule and the traffic it
 * affects are one tap apart.
 */
@Composable
internal fun MockScreen(plugin: NetworkPlugin) {
    val config by plugin.mocks.configuration.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = scrySpacing.md)) {

        ScrySectionHeader("Conditions")

        Row(
            Modifier.fillMaxWidth().padding(vertical = scrySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Offline", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Fail every request, as if the device had no network",
                    style = MaterialTheme.typography.bodySmall,
                    color = scryPalette.muted,
                )
            }
            Switch(
                checked = config.offline,
                onCheckedChange = { plugin.mocks.setOffline(it) },
            )
        }

        Text("Throttle", style = MaterialTheme.typography.bodyMedium)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(vertical = scrySpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(scrySpacing.xs),
        ) {
            ThrottleProfile.entries.forEach { profile ->
                FilterChip(
                    selected = config.throttle == profile,
                    onClick = { plugin.mocks.setThrottle(profile) },
                    label = { Text(profile.label, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScrySectionHeader("Rules")
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showAdd = true }) { Text("Add") }
                TextButton(onClick = { showImport = true }) { Text("Import") }
                // Export is the point of the feature: a scenario nobody else can
                // reproduce is not worth configuring.
                ScryShareAction(
                    fileName = "scry-mocks.json",
                    enabled = config.rules.isNotEmpty(),
                    description = "Export rules",
                    label = "Export",
                    text = { plugin.mocks.exportJson() },
                )
                ScryDestructiveAction(
                    title = "Delete all rules?",
                    message = "Removes the ${config.rules.size} rules configured here. " +
                        "Offline and throttle settings are left alone.",
                    confirmLabel = "Delete",
                    description = "Delete all rules",
                    enabled = config.rules.isNotEmpty(),
                    onConfirm = { plugin.mocks.clearRules() },
                )
            }
        }

        if (config.rules.isEmpty()) {
            ScryEmptyState(
                title = "No rules yet.",
                hint = "Add one to return a canned response, inject a failure or add latency.",
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(config.rules, key = { it.id }) { rule ->
                    RuleRow(rule, plugin)
                    ScryDivider()
                }
            }
        }
    }

    if (showAdd) {
        AddRuleDialog(
            onDismiss = { showAdd = false },
            onAdd = { rule -> plugin.mocks.addRule(rule); showAdd = false },
        )
    }
    if (showImport) {
        ImportDialog(
            onDismiss = { showImport = false },
            onImport = { text -> plugin.mocks.importJson(text).also { showImport = false } },
        )
    }
}

@Composable
private fun RuleRow(rule: MockRule, plugin: NetworkPlugin) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = scrySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(scrySpacing.xs)) {
                ScryPill(rule.action.pillLabel(), rule.action.pillColour())
                Text(rule.method ?: "ANY", style = MaterialTheme.typography.labelSmall, color = scryPalette.muted)
            }
            Text(rule.displayName, style = scryMonoStyle)
        }
        Switch(
            checked = rule.enabled,
            onCheckedChange = { plugin.mocks.setRuleEnabled(rule.id, it) },
        )
        TextButton(onClick = { plugin.mocks.removeRule(rule.id) }) { Text("Delete") }
    }
}

@Composable
private fun MockAction.pillColour() = when (this) {
    is MockAction.Respond -> ScryStatusColorsFor(statusCode)
    is MockAction.Fail -> scryPalette.danger
    is MockAction.Delay -> scryPalette.warning
}

@Composable
private fun ScryStatusColorsFor(code: Int) =
    io.github.akhilesh2491.scry.ui.ScryStatusColors.forStatus(code)

private fun MockAction.pillLabel(): String = when (this) {
    is MockAction.Respond -> statusCode.toString()
    is MockAction.Fail -> "FAIL"
    is MockAction.Delay -> "${delayMillis}ms"
}

@Composable
private fun AddRuleDialog(onDismiss: () -> Unit, onAdd: (MockRule) -> Unit) {
    var pattern by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("503") }
    var body by remember { mutableStateOf("{}") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(scrySpacing.sm)) {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("URL glob") },
                    placeholder = { Text("*/orders*", style = scryMonoStyle) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = method,
                    onValueChange = { method = it },
                    label = { Text("Method (blank = any)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = status,
                    onValueChange = { status = it },
                    label = { Text("Status code") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Response body") },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = pattern.isNotBlank() && status.toIntOrNull() != null,
                onClick = {
                    onAdd(
                        MockRule(
                            id = newTransactionId(),
                            urlPattern = pattern,
                            method = method.takeIf { it.isNotBlank() },
                            action = MockAction.Respond(
                                statusCode = status.toInt(),
                                body = body,
                            ),
                        ),
                    )
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ImportDialog(onDismiss: () -> Unit, onImport: (String) -> Boolean) {
    var text by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import rules") },
        text = {
            Column {
                Text(
                    "Paste a scry-mocks.json exported from another device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scryPalette.muted,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; failed = false },
                    isError = failed,
                    supportingText = if (failed) {
                        { Text("Not valid rule JSON") }
                    } else {
                        null
                    },
                    textStyle = scryMonoStyle,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (!onImport(text)) failed = true }) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
