package io.github.akhilesh2491.scry.ui

import androidx.compose.runtime.Composable

/**
 * Matches the platform's status-bar icons to Scry's theme.
 *
 * Scry hosts itself in a bare activity, so it inherits whatever appearance the
 * host app last set. Without this, a light-themed app opening Scry in dark mode
 * gets black icons on a near-black bar — invisible.
 */
@Composable
internal expect fun ScrySystemBars(darkTheme: Boolean)
