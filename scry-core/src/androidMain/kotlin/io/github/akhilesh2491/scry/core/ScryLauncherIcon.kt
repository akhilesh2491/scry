package io.github.akhilesh2491.scry.core

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * A "Scry" icon in the launcher drawer, next to the app's own.
 *
 * **Off by default, and the only launcher that is.** The drawer can only start
 * an `exported="true"` component, and the Scry UI shows request bodies, shared
 * preferences and database rows. Enabling this means any app on the device can
 * start that UI, so it is a decision a host makes deliberately — most teams want
 * it on a shared QA build and off on their own machine.
 *
 * The component it toggles is a trampoline in `scry-ui` that re-checks the build
 * is debuggable before forwarding, so a release build cannot be opened this way
 * even if the setting is somehow left enabled.
 *
 * ```kotlin
 * Scry.install(this) { launchers { launcherIcon = true } }
 * // or, at runtime:
 * ScryLauncherIcon.enable(context)
 * ```
 *
 * The config wins on the next [Scry.install]: component enablement survives
 * reinstalls, so installing with `launcherIcon = false` actively turns the icon
 * off rather than leaving a stale one behind. Enable it at runtime for a session;
 * set the flag for a build.
 */
public object ScryLauncherIcon {

    private const val LAUNCHER_ACTIVITY_CLASS =
        "io.github.akhilesh2491.scry.ui.ScryLauncherActivity"

    /** Shows the icon. Takes effect immediately; the launcher re-reads the list. */
    @JvmStatic
    public fun enable(context: Context) {
        setEnabled(context, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
    }

    /** Hides the icon again. */
    @JvmStatic
    public fun disable(context: Context) {
        setEnabled(context, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
    }

    /** Whether the drawer icon is currently showing. */
    @JvmStatic
    public fun isEnabled(context: Context): Boolean {
        val appContext = context.applicationContext
        val component = ComponentName(appContext, LAUNCHER_ACTIVITY_CLASS)
        return runCatching {
            appContext.packageManager.getComponentEnabledSetting(component) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }.getOrDefault(false)
    }

    private fun setEnabled(context: Context, state: Int) {
        val appContext = context.applicationContext
        val component = ComponentName(appContext, LAUNCHER_ACTIVITY_CLASS)
        // Fails when `scry-ui` is absent — a release build, or a host that took
        // core only. Nothing to show in that case, so the failure is the answer.
        runCatching {
            appContext.packageManager.setComponentEnabledSetting(
                component,
                state,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
