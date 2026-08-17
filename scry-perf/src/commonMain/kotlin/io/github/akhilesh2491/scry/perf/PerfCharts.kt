package io.github.akhilesh2491.scry.perf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.akhilesh2491.scry.ui.scryPalette
import io.github.akhilesh2491.scry.ui.scrySpacing
import kotlin.math.max

/**
 * The plugin's drawing primitives.
 *
 * Kept here rather than in `scry-ui` until something outside this module needs
 * them — a shared component with one caller is a guess about the future, and
 * `scry-ui` is on the critical path of every other plugin.
 *
 * All of them degrade to nothing when handed no data, so a screen can lay them
 * out unconditionally instead of branching around empty states.
 */

/**
 * A horizontal bar whose fill is a value measured against a limit.
 *
 * Colour is the message: green under budget, amber approaching it, red over. The
 * bar is never empty — a zero-width fill reads as "broken", not as "fast".
 *
 * The caption states the two numbers the bar encodes. Without it the bar says
 * "this is bad" while making the reader hunt for how bad.
 */
@Composable
internal fun BudgetBar(
    value: Double,
    limit: Double?,
    modifier: Modifier = Modifier,
    showCaption: Boolean = true,
) {
    val palette = scryPalette
    // With no budget there is nothing to be over, so the bar is informational
    // only and scaled against the value itself.
    val ceiling = max(limit ?: value, 0.001)
    val fraction = (value / ceiling).coerceIn(0.0, 1.0).toFloat()
    val colour = when {
        limit == null -> palette.info
        value > limit -> palette.danger
        value > limit * WARNING_FRACTION -> palette.warning
        else -> palette.success
    }

    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(BAR_HEIGHT)) {
            val radius = CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(color = colour.copy(alpha = 0.18f), size = size, cornerRadius = radius)
            drawRoundRect(
                color = colour,
                size = Size(width = max(size.width * fraction, size.height), height = size.height),
                cornerRadius = radius,
            )
        }
        if (showCaption && limit != null) {
            Text(
                text = "${value.roundTo(0)} ms of ${limit.roundTo(0)} ms budget",
                style = MaterialTheme.typography.labelSmall,
                color = if (value > limit) palette.danger else palette.muted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * A startup breakdown drawn as stacked segments on one row, with a legend.
 *
 * A waterfall rather than a list of numbers because the question is almost never
 * "how long was startup" but "which part of it was long", and proportion answers
 * that faster than arithmetic does. The legend is what turns the colours from
 * decoration into something readable.
 */
@Composable
internal fun WaterfallBar(
    phases: List<PerfPhase>,
    totalMillis: Long,
    modifier: Modifier = Modifier,
) {
    if (phases.isEmpty() || totalMillis <= 0) return
    val colours = phaseColours()

    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(WATERFALL_HEIGHT)) {
            phases.forEachIndexed { index, phase ->
                val start = (phase.startOffsetMillis.toDouble() / totalMillis).coerceIn(0.0, 1.0)
                val width = (phase.durationMillis.toDouble() / totalMillis).coerceIn(0.0, 1.0)
                drawRect(
                    color = colours[index % colours.size],
                    topLeft = Offset(x = (start * size.width).toFloat(), y = 0f),
                    size = Size(width = (width * size.width).toFloat(), height = size.height),
                )
            }
        }

        FlowRow(
            Modifier.fillMaxWidth().padding(top = scrySpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(scrySpacing.md),
        ) {
            phases.forEachIndexed { index, phase ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(colours[index % colours.size], CircleShape),
                    )
                    Text(
                        text = " ${phase.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = scryPalette.muted,
                    )
                }
            }
        }
    }
}

/**
 * Frame durations as vertical bars, oldest to newest, with the budget marked.
 *
 * Bars over budget are drawn in the danger colour, so a burst of jank is visible
 * as a shape before any number is read.
 */
@Composable
internal fun FrameSparkline(
    durationsMillis: List<Double>,
    budgetMillis: Double,
    modifier: Modifier = Modifier,
) {
    if (durationsMillis.isEmpty()) return
    val palette = scryPalette
    // Scale to the budget or the worst frame, whichever is larger, so a calm
    // stretch does not get amplified into a wall of full-height bars.
    val ceiling = max(durationsMillis.max(), budgetMillis * 2)

    Canvas(modifier.fillMaxWidth().height(SPARKLINE_HEIGHT)) {
        val slot = size.width / durationsMillis.size
        val barWidth = max(slot - 1f, 1f)

        durationsMillis.forEachIndexed { index, duration ->
            val height = (duration / ceiling).coerceIn(0.0, 1.0).toFloat() * size.height
            drawRect(
                color = if (duration > budgetMillis) palette.danger else palette.success,
                topLeft = Offset(x = index * slot, y = size.height - height),
                size = Size(width = barWidth, height = height),
            )
        }

        val budgetY = size.height - (budgetMillis / ceiling).toFloat() * size.height
        drawRect(
            color = palette.muted.copy(alpha = 0.6f),
            topLeft = Offset(x = 0f, y = budgetY),
            size = Size(width = size.width, height = 1f),
        )
    }
}

/** Segment colours, in the order phases are drawn. */
@Composable
private fun phaseColours(): List<Color> {
    val palette = scryPalette
    return listOf(palette.info, palette.success, palette.warning, palette.danger)
}

/** Where a value stops being comfortably under budget and starts being a warning. */
private const val WARNING_FRACTION: Double = 0.8

private val BAR_HEIGHT = 8.dp
private val WATERFALL_HEIGHT = 20.dp

/**
 * Tall enough to read a shape in.
 *
 * At the previous 40dp a run of frames a few milliseconds over budget was a
 * couple of pixels taller than one under it — the chart was present without
 * being informative.
 */
private val SPARKLINE_HEIGHT = 64.dp
