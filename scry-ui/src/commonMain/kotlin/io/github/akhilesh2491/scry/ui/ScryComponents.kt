package io.github.akhilesh2491.scry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.akhilesh2491.scry.core.Scry
import io.github.akhilesh2491.scry.core.shareScryFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared building blocks.
 *
 * Every plugin draws from these rather than styling ad hoc, so a status colour
 * or a row height means the same thing everywhere. A debugging tool is scanned,
 * not read — consistency is what makes scanning work.
 */

/** A small coloured capsule: HTTP status, log level, crash kind. */
@Composable
public fun ScryPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** A neutral badge for HTTP methods and similar short tokens. */
@Composable
public fun ScryBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(scryPalette.badgeSurface, RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** A dense list row: leading status, a two-line body, trailing metadata. */
@Composable
public fun ScryListRow(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = scrySpacing.md, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.widthIn(min = 44.dp), contentAlignment = Alignment.CenterStart) { leading() }
        Column(
            Modifier.weight(1f).padding(horizontal = scrySpacing.sm),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        trailing?.let {
            Column(horizontalAlignment = Alignment.End) { it() }
        }
    }
}

/** Hairline divider, thinner than Material's default to suit dense lists. */
@Composable
public fun ScryDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 0.5.dp, color = scryPalette.divider)
}

/** An uppercase section heading inside a detail screen. */
@Composable
public fun ScrySectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = scryPalette.muted,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = scrySpacing.lg, bottom = scrySpacing.xs),
    )
}

/**
 * A label/value pair.
 *
 * The value is monospace and selectable — the whole point of showing a header or
 * an id is that someone copies it somewhere else.
 */
@Composable
public fun ScryKeyValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(scrySpacing.sm),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = scryPalette.muted,
            modifier = Modifier.weight(0.34f),
        )
        SelectionContainer(Modifier.weight(0.66f)) {
            Text(
                value,
                style = scryMonoStyle,
                color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** A scrollable, selectable code block for payloads and stack traces. */
@Composable
public fun ScryCodeBlock(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    SelectionContainer {
        Box(
            modifier
                .fillMaxWidth()
                .background(scryPalette.codeSurface, RoundedCornerShape(8.dp))
                .border(0.5.dp, scryPalette.divider, RoundedCornerShape(8.dp))
                .padding(scrySpacing.sm)
                .horizontalScroll(rememberScrollState()),
        ) {
            Text(text, style = scryMonoStyle, color = color ?: MaterialTheme.colorScheme.onSurface)
        }
    }
}

/**
 * The empty state.
 *
 * Always says what to do next rather than just reporting emptiness — "no data"
 * with no next step is the most common way a debug tool wastes someone's time.
 */
@Composable
public fun ScryEmptyState(
    title: String,
    hint: String? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.fillMaxSize().padding(scrySpacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(scrySpacing.xs),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            hint?.let {
                Text(
                    it,
                    style = scryMonoStyle,
                    color = scryPalette.muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The standard filter field used at the top of every list. */
@Composable
public fun ScrySearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(placeholder, style = MaterialTheme.typography.bodySmall, color = scryPalette.muted)
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
    )
}

// --- Screen chrome ----------------------------------------------------------

/**
 * The header row every plugin screen starts with.
 *
 * Exists so back, title and actions land in the same place with the same
 * metrics on every screen. Before this, each plugin hand-rolled its own `Row`
 * and they drifted — which is how Share ended up present on four screens and
 * missing from three.
 *
 * Pass [center] to put something other than a title in the flexible slot; the
 * list screens put their filter field there. Whatever it emits must carry
 * `Modifier.weight(1f)`, or the actions are pushed off the end of the row.
 */
@Composable
public fun ScryScreenBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    center: @Composable (RowScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = scrySpacing.xs, vertical = scrySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        } else {
            Box(Modifier.width(scrySpacing.sm))
        }

        if (center != null) {
            center()
        } else {
            Column(Modifier.weight(1f).padding(horizontal = scrySpacing.sm)) {
                title?.let { Text(it, style = MaterialTheme.typography.titleSmall, maxLines = 1) }
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = scryPalette.muted,
                        maxLines = 1,
                    )
                }
            }
        }

        actions()
    }
}

// --- Actions ----------------------------------------------------------------

/**
 * Where a screen reports the outcome of an action the user cannot otherwise see.
 *
 * Provided by [ScryShell]. Defaults to a no-op so a screen still composes in a
 * preview or a test that does not host the shell.
 */
public val LocalScryFeedback: ProvidableCompositionLocal<(String) -> Unit> =
    staticCompositionLocalOf { {} }

/** One entry in a [ScryShareAction] with more than one export format. */
public class ScryShareFormat(
    /** Menu label, e.g. `"JSON"`. */
    public val label: String,
    public val fileName: String,
    public val text: () -> String,
)

/**
 * The one way a Scry screen shares something.
 *
 * Centralised because the failure mode it replaces was invisible: every screen
 * used to call `shareScryFile` and drop the boolean it returns, so a platform
 * that refused to present a share sheet was indistinguishable from a button
 * that had not been wired up. This always says which of the two happened.
 *
 * Give [formats] more than one entry to offer a format menu instead.
 */
@Composable
public fun ScryShareAction(
    fileName: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    description: String = "Share",
    label: String? = null,
    text: () -> String,
) {
    val share = rememberShare()
    ScryActionButton(
        modifier = modifier,
        icon = Icons.Default.Share,
        description = description,
        label = label,
        enabled = enabled,
        onClick = { share(fileName, text) },
    )
}

/** [ScryShareAction] with a choice of export formats. */
@Composable
public fun ScryShareAction(
    formats: List<ScryShareFormat>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    description: String = "Share",
) {
    val share = rememberShare()
    var open by remember { mutableStateOf(false) }

    Box(modifier) {
        ScryActionButton(
            icon = Icons.Default.Share,
            description = description,
            label = null,
            enabled = enabled,
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            formats.forEach { format ->
                DropdownMenuItem(
                    text = { Text(format.label) },
                    onClick = {
                        open = false
                        share(format.fileName, format.text)
                    },
                )
            }
        }
    }
}

/**
 * An action that destroys data, behind a confirmation.
 *
 * Every destructive path in Scry goes through this — clearing captured traffic,
 * emptying a preferences store, deleting a database row. One component means one
 * confirmation dialog, so "Clear" never once means "clear immediately".
 */
@Composable
public fun ScryDestructiveAction(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.DeleteOutline,
    description: String = title,
    confirmLabel: String = "Delete",
    label: String? = null,
    enabled: Boolean = true,
    onConfirm: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }

    ScryActionButton(
        modifier = modifier,
        icon = icon,
        description = description,
        label = label,
        enabled = enabled,
        onClick = { confirming = true },
    )

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(title) },
            text = { Text(message, style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                TextButton(onClick = { confirming = false; onConfirm() }) {
                    Text(confirmLabel, color = scryPalette.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("Cancel") }
            },
        )
    }
}

/** Icon button, or a text button when [label] is given. Same behaviour either way. */
@Composable
private fun ScryActionButton(
    icon: ImageVector,
    description: String,
    label: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (label == null) {
        IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            Icon(icon, contentDescription = description)
        }
    } else {
        TextButton(onClick = onClick, enabled = enabled, modifier = modifier) { Text(label) }
    }
}

/**
 * Shares through the installed instance and reports what happened.
 *
 * Building the payload happens off the main thread: a HAR of a long session, or
 * a table of a few thousand rows, is megabytes of string building, and a
 * debugging tool that drops frames while you export is not one you trust to tell
 * you about dropped frames. Handing it to the platform stays on the main thread
 * — UIKit refuses to present a share sheet from anywhere else.
 */
@Composable
private fun rememberShare(): (String, () -> String) -> Unit {
    val feedback = LocalScryFeedback.current
    val scope = rememberCoroutineScope()

    return remember(feedback, scope) {
        { fileName, text ->
            val instance = Scry.instance
            if (instance == null) {
                feedback("Scry is not installed.")
            } else {
                scope.launch {
                    val payload = withContext(Dispatchers.Default) { text() }
                    // The platform refuses when there is no share target, no
                    // handler for the type, or no window to present from. Saying
                    // so beats a button that looks dead.
                    val shared = shareScryFile(instance.platformContext, fileName, payload)
                    feedback(if (shared) "Exported $fileName" else "Couldn't share $fileName")
                }
            }
        }
    }
}

// --- Cards and statistics ---------------------------------------------------

/**
 * A bordered group of related content.
 *
 * Introduced for the performance screen, where a flat scroll of section headers
 * gave no indication of where one measurement ended and the next began.
 */
@Composable
public fun ScryCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = scrySpacing.xs)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
            .border(0.5.dp, scryPalette.divider, RoundedCornerShape(10.dp))
            .padding(scrySpacing.md),
    ) {
        if (title != null || trailing != null) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = scrySpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    title?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                        )
                    }
                    subtitle?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = scryPalette.muted)
                    }
                }
                trailing?.invoke()
            }
        }
        content()
    }
}

/**
 * One number with its name.
 *
 * The label sits *above* the value at full contrast rather than trailing it in
 * 11sp grey. The old inline form let six statistics share a single row, which is
 * how a screen ends up unreadable while technically showing everything.
 */
@Composable
public fun ScryStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    Column(modifier.widthIn(min = 76.dp).padding(end = scrySpacing.md, bottom = scrySpacing.xs)) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = scryPalette.muted,
            maxLines = 1,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            color = color ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/**
 * Statistics laid out so they wrap rather than compress.
 *
 * A [FlowRow] and not a fixed grid: the cells hold "1.2 ms" and "412 (18.3%)"
 * alike, and a fixed column count guarantees one of the two is truncated.
 */
@Composable
public fun ScryStatGrid(
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit,
) {
    FlowRow(modifier.fillMaxWidth(), content = content)
}
