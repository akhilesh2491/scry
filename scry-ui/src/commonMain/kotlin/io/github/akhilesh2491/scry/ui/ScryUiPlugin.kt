package io.github.akhilesh2491.scry.ui

import androidx.compose.runtime.Composable
import io.github.akhilesh2491.scry.core.ScryPlugin

/**
 * A plugin that contributes a screen to the Scry UI.
 *
 * Split from [ScryPlugin] so that `scry-core` carries no Compose dependency. A
 * plugin that only collects data (feeding another plugin's screen, or an export)
 * implements [ScryPlugin] alone and never pulls in Compose.
 */
public interface ScryUiPlugin : ScryPlugin {

    /**
     * The plugin's screen, rendered inside the Scry shell.
     *
     * The shell supplies the app bar, back handling and theming, so this should
     * render content only.
     */
    @Composable
    public fun Content()
}
