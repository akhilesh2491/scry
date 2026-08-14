package io.github.akhilesh2491.scry.ui

import androidx.compose.runtime.Composable

/**
 * No-op on iOS.
 *
 * Scry is presented as a page sheet, so the status bar belongs to the host app
 * behind it and is not Scry's to restyle. Changing it would leave the host with
 * the wrong appearance after Scry is dismissed.
 */
@Composable
internal actual fun ScrySystemBars(darkTheme: Boolean): Unit = Unit
