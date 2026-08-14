package io.github.akhilesh2491.scry.ui

import androidx.compose.runtime.Composable

/** Desktop windows have no system status bar to match. */
@Composable
internal actual fun ScrySystemBars(darkTheme: Boolean): Unit = Unit
