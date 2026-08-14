package io.github.akhilesh2491.scry.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.akhilesh2491.scry.core.Scry

/**
 * Hosts the Scry UI on Android.
 *
 * A separate activity rather than an overlay window: an overlay would need
 * `SYSTEM_ALERT_WINDOW`, and asking users of a *debug* library to grant a
 * draw-over-other-apps permission is a non-starter. An activity also gets back
 * handling, rotation and process death for free.
 *
 * Declared in this library's manifest, so apps do not register anything.
 */
public class ScryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val instance = Scry.instance
        if (instance == null) {
            // Scry was uninstalled, or this is the no-op build. Nothing to show.
            finish()
            return
        }
        setContent {
            ScryShell(instance = instance, onDismiss = { finish() })
        }
    }

    override fun finish() {
        Scry.hide()
        super.finish()
    }

    public companion object {
        /** Intent that opens Scry. Useful for a debug-menu entry or an ADB launch. */
        @JvmStatic
        public fun intent(context: Context): Intent =
            Intent(context, ScryActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
