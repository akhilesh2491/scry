package io.github.akhilesh2491.scry.prefs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import io.github.akhilesh2491.scry.ui.ScrySearchField
import io.github.akhilesh2491.scry.ui.ScryShareAction

@Composable
internal fun PreferencesScreen(plugin: PreferencesPlugin) {
    val stores by plugin.stores.collectAsState()
    var selected by remember { mutableStateOf<KeyValueStore?>(null) }

    // Re-scan whenever the list is shown, so stores created since install appear.
    LaunchedEffect(selected) {
        if (selected == null) plugin.refresh()
    }

    val store = selected
    if (store == null) {
        StoreList(stores) { selected = it }
    } else {
        StoreDetail(store, onBack = { selected = null })
    }
}

@Composable
private fun StoreList(stores: List<KeyValueStore>, onSelect: (KeyValueStore) -> Unit) {
    if (stores.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "No key-value stores found.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(stores, key = { it.name }) { store ->
            Row(
                Modifier.fillMaxWidth().clickable { onSelect(store) }.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(store.name)
                    Text(
                        "${store.keys().size} keys" + if (!store.isMutable) " · read-only" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun StoreDetail(store: KeyValueStore, onBack: () -> Unit) {
    // Bumped to force a re-read after any mutation or external change. Cheaper
    // and far less error-prone than mirroring the store into Compose state and
    // trying to keep the mirror honest.
    var revision by remember(store) { mutableIntStateOf(0) }
    var query by remember(store) { mutableStateOf("") }
    var editing by remember(store) { mutableStateOf<Pair<String, PreferenceValue>?>(null) }
    var adding by remember(store) { mutableStateOf(false) }

    // Live updates while you use the app in another window/screen.
    LaunchedEffect(store) {
        val handle = store.observe { revision++ }
        try {
            kotlinx.coroutines.awaitCancellation()
        } finally {
            handle?.close()
        }
    }

    val entries = remember(store, revision, query) {
        store.keys()
            .filter { query.isBlank() || it.contains(query, ignoreCase = true) }
            .mapNotNull { key -> store.read(key)?.let { key to it } }
    }

    // The export covers the whole store, not the filtered view: a preferences
    // file with half its keys missing is a trap for whoever reads it later.
    val allKeys = remember(store, revision) { store.keys() }

    Column(Modifier.fillMaxSize()) {
        ScryScreenBar(
            title = store.name,
            subtitle = "${allKeys.size} keys" + if (!store.isMutable) " · read-only" else "",
            onBack = onBack,
            actions = {
                if (store.isMutable) {
                    IconButton(onClick = { adding = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add key")
                    }
                }
                ScryShareAction(
                    fileName = "scry-prefs-${store.name.sanitisedForFileName()}.json",
                    enabled = allKeys.isNotEmpty(),
                    description = "Share store",
                    text = { store.jsonExport(allKeys) },
                )
                if (store.isMutable) {
                    ScryDestructiveAction(
                        title = "Clear ${store.name}?",
                        message = "Removes every key in this store. This affects the app, " +
                            "not just Scry.",
                        confirmLabel = "Clear",
                        description = "Clear store",
                        enabled = allKeys.isNotEmpty(),
                        onConfirm = { store.clear(); revision++ },
                    )
                }
            },
        )

        ScrySearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Filter keys",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No keys.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(entries, key = { it.first }) { (key, value) ->
                    PreferenceRow(
                        key = key,
                        value = value,
                        enabled = store.isMutable && value.isEditable,
                        onClick = { editing = key to value },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    editing?.let { (key, value) ->
        EditDialog(
            key = key,
            initial = value,
            onDismiss = { editing = null },
            onSave = { updated ->
                store.write(key, updated)
                editing = null
                revision++
            },
            onDelete = {
                store.remove(key)
                editing = null
                revision++
            },
        )
    }

    if (adding) {
        AddDialog(
            onDismiss = { adding = false },
            onAdd = { key, value ->
                store.write(key, value)
                adding = false
                revision++
            },
        )
    }

}

/**
 * Makes a store name safe to put in a file name.
 *
 * Store names come from the platform — an Android `SharedPreferences` file, an
 * `NSUserDefaults` suite — and a path separator in one would land the export
 * somewhere nobody asked for.
 */
private fun String.sanitisedForFileName(): String =
    map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '-' }
        .joinToString("")
        .trim('-')
        .ifEmpty { "store" }

@Composable
private fun PreferenceRow(
    key: String,
    value: PreferenceValue,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(key, fontWeight = FontWeight.Medium)
            Text(
                value.display.take(120),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            value.typeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun EditDialog(
    key: String,
    initial: PreferenceValue,
    onDismiss: () -> Unit,
    onSave: (PreferenceValue) -> Unit,
    onDelete: () -> Unit,
) {
    var text by remember(key) { mutableStateOf(initial.display) }
    var boolValue by remember(key) {
        mutableStateOf((initial as? PreferenceValue.BooleanValue)?.value ?: false)
    }
    var error by remember(key) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(key) },
        text = {
            Column {
                Text(initial.typeLabel, style = MaterialTheme.typography.labelSmall)
                if (initial is PreferenceValue.BooleanValue) {
                    Switch(checked = boolValue, onCheckedChange = { boolValue = it })
                } else {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it; error = null },
                        singleLine = initial !is PreferenceValue.StringSetValue,
                        isError = error != null,
                        supportingText = error?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (initial is PreferenceValue.StringSetValue) {
                        Text(
                            "Comma-separated",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (initial is PreferenceValue.BooleanValue) {
                    onSave(PreferenceValue.BooleanValue(boolValue))
                    return@TextButton
                }
                // Parse into the original type. Refusing bad input beats silently
                // rewriting an Int key as a String, which is how you get a
                // ClassCastException in the host app on next read.
                val parsed = parseAs(initial, text)
                if (parsed == null) error = "Not a valid ${initial.typeLabel}" else onSave(parsed)
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun AddDialog(onDismiss: () -> Unit, onAdd: (String, PreferenceValue) -> Unit) {
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Key") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Value (String)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(key, PreferenceValue.StringValue(value)) },
                enabled = key.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Parses [text] into the same type as [template]. Null when it does not fit. */
internal fun parseAs(template: PreferenceValue, text: String): PreferenceValue? = when (template) {
    is PreferenceValue.StringValue -> PreferenceValue.StringValue(text)
    is PreferenceValue.IntValue -> text.trim().toIntOrNull()?.let(PreferenceValue::IntValue)
    is PreferenceValue.LongValue -> text.trim().toLongOrNull()?.let(PreferenceValue::LongValue)
    is PreferenceValue.FloatValue -> text.trim().toFloatOrNull()?.let(PreferenceValue::FloatValue)
    is PreferenceValue.DoubleValue -> text.trim().toDoubleOrNull()?.let(PreferenceValue::DoubleValue)
    is PreferenceValue.BooleanValue -> text.trim().toBooleanStrictOrNull()
        ?.let(PreferenceValue::BooleanValue)
    is PreferenceValue.StringSetValue -> PreferenceValue.StringSetValue(
        text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
    )
    is PreferenceValue.Unsupported -> null
}
