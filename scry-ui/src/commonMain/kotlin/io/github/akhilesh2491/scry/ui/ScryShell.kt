package io.github.akhilesh2491.scry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.akhilesh2491.scry.core.Scry
import io.github.akhilesh2491.scry.core.ScryInstance
import io.github.akhilesh2491.scry.core.ScryPlugin
import kotlinx.coroutines.launch

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
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Hosted once, here, so any screen can report the outcome of an action the
    // user has no other way to observe — a share sheet the platform refused to
    // present being the case that prompted it.
    val feedback: (String) -> Unit = remember(snackbars, scope) {
        { message ->
            scope.launch {
                snackbars.currentSnackbarData?.dismiss()
                snackbars.showSnackbar(message, duration = SnackbarDuration.Short)
            }
        }
    }

    ScryTheme {
        CompositionLocalProvider(LocalScryFeedback provides feedback) {
            Surface(
                modifier = modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        val current = selected
                        ScryTopBar(
                            title = current?.displayName ?: "Scry",
                            subtitle = current?.id,
                            onBack = current?.let { { selected = null } },
                            onDismiss = onDismiss,
                            actions = {
                                // Global clear only on the list. A plugin's own
                                // screen carries the action that is right for it:
                                // clearing captured traffic and emptying the
                                // app's preferences are not the same button.
                                if (current == null) {
                                    ScryDestructiveAction(
                                        title = "Clear captured data?",
                                        message = "Removes everything Scry has captured. Your " +
                                            "app's preferences and databases are not touched.",
                                        confirmLabel = "Clear",
                                        description = "Clear captured data",
                                        onConfirm = { Scry.clear(); feedback("Cleared.") },
                                    )
                                }
                            },
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

                    SnackbarHost(
                        hostState = snackbars,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                ),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScryTopBar(
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)?,
    onDismiss: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
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

        actions()
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
