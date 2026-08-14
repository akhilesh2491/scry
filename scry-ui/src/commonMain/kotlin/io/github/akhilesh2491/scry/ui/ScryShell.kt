package io.github.akhilesh2491.scry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.akhilesh2491.scry.core.Scry
import io.github.akhilesh2491.scry.core.ScryInstance
import io.github.akhilesh2491.scry.core.ScryPlugin

/**
 * The Scry UI: a plugin list that drills into a single plugin's screen.
 *
 * Knows nothing about any specific plugin. Adding a new one requires no change
 * here — that is the test of whether the plugin boundary is real.
 */
@Composable
public fun ScryShell(
    instance: ScryInstance,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf<ScryPlugin?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    ScryTheme {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.fillMaxSize()) {
                val current = selected
                ScryTopBar(
                    title = current?.displayName ?: "Scry",
                    subtitle = current?.id,
                    onBack = current?.let { { selected = null } },
                    onClear = if (current == null) {
                        { confirmClear = true }
                    } else {
                        null
                    },
                    onDismiss = onDismiss,
                )
                ScryDivider()

                val contentInsets = Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                )

                if (current == null) {
                    Box(contentInsets) { PluginList(instance.plugins) { selected = it } }
                } else {
                    Box(Modifier.fillMaxSize().then(contentInsets)) {
                        when (current) {
                            is ScryUiPlugin -> current.Content()
                            else -> ScryEmptyState(
                                title = "${current.displayName} has no screen.",
                                hint = "It implements ScryPlugin but not ScryUiPlugin.",
                            )
                        }
                    }
                }
            }
        }

        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text("Clear captured data?") },
                text = {
                    Text(
                        "Removes everything Scry has captured. Your app's preferences and " +
                            "databases are not touched.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                confirmButton = {
                    TextButton(onClick = { Scry.clear(); confirmClear = false }) { Text("Clear") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
                },
            )
        }
    }
}

@Composable
private fun ScryTopBar(
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)?,
    onClear: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            // Background before insets: the surface fills to the top edge so the
            // status bar matches the toolbar instead of showing a different
            // colour above it.
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
            )
            .padding(horizontal = scrySpacing.xs, vertical = scrySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        } else {
            Box(Modifier.size(scrySpacing.md))
        }

        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = scryPalette.muted)
            }
        }

        onClear?.let {
            IconButton(onClick = it) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Clear captured data",
                    tint = scryPalette.muted,
                )
            }
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close Scry")
        }
    }
}

@Composable
private fun PluginList(plugins: List<ScryPlugin>, onSelect: (ScryPlugin) -> Unit) {
    if (plugins.isEmpty()) {
        ScryEmptyState(
            title = "No plugins registered.",
            hint = "Scry.install(context) { plugin(NetworkPlugin()) }",
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(plugins, key = { it.id }) { plugin ->
            PluginRow(plugin) { onSelect(plugin) }
            ScryDivider()
        }
    }
}

@Composable
private fun PluginRow(plugin: ScryPlugin, onClick: () -> Unit) {
    val badge by plugin.badge.collectAsState()

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = scrySpacing.lg, vertical = scrySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A monogram rather than an icon set: plugins are contributed by third
        // parties, and requiring every author to supply artwork is friction that
        // would just produce a wall of identical placeholders.
        Box(
            Modifier
                .size(30.dp)
                .background(scryPalette.badgeSurface, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                plugin.displayName.take(1).uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Column(
            Modifier.weight(1f).padding(horizontal = scrySpacing.md),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                plugin.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(plugin.id, style = scryMonoStyle, color = scryPalette.muted)
        }

        badge?.let { ScryPill(it, MaterialTheme.colorScheme.primary) }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = scryPalette.muted,
            modifier = Modifier.padding(start = scrySpacing.sm),
        )
    }
}
