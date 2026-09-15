package io.github.akhilesh2491.scry.ui

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.os.Bundle
import io.github.akhilesh2491.scry.core.Scry

/**
 * The drawer icon's entry point: starts [ScryActivity], then gets out of the way.
 *
 * Declared `enabled="false"` and switched on by
 * `io.github.akhilesh2491.scry.core.ScryLauncherIcon`, so an app only has a Scry
 * icon in the launcher if it asked for one.
 *
 * A trampoline rather than an `activity-alias` on [ScryActivity] itself: the
 * launcher can only start an exported component, and [ScryActivity] shows request
 * bodies, preferences and database rows. Exporting a two-line forwarder — which
 * re-checks the build is debuggable before it forwards — keeps the activity that
 * actually holds the data unexported, whatever this icon's state is.
 */
public class ScryLauncherActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isDebuggable() && Scry.isInstalled()) {
            startActivity(ScryActivity.intent(this))
        }
        finish()
    }

    private fun isDebuggable(): Boolean =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
