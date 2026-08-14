package io.github.akhilesh2491.scry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

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
