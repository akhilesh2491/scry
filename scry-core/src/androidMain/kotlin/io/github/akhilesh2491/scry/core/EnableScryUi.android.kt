package io.github.akhilesh2491.scry.core

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Makes [Scry.show] open the Scry UI.
 *
 * ```kotlin
 * Scry.install(this) { plugin(NetworkPlugin()) }
 * enableScryUi(this)
 * ```
 *
 * Lives in `scry-core`, not `scry-ui`, and starts the activity by **class name**
 * rather than class reference. That indirection is load-bearing: this is called
 * from `Application.onCreate`, which is main source code compiled into every
 * variant. If it referenced the Compose UI module directly, the release build
 * would need a no-op that also carried Compose — and shipping Compose to users
 * so a debug tool can be stubbed out is exactly backwards.
 *
 * When `scry-ui` is absent (a release build using `scry-no-op`), the activity
 * simply is not there and this does nothing.
 */
public fun enableScryUi(context: Context): Job? {
    val instance = Scry.instance ?: return null
    val appContext = context.applicationContext
    return CoroutineScope(Dispatchers.Main.immediate).launch {
        instance.isVisible.collect { visible ->
            if (!visible) return@collect
            runCatching {
                appContext.startActivity(
                    Intent()
                        .setClassName(appContext, SCRY_ACTIVITY_CLASS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}

private const val SCRY_ACTIVITY_CLASS = "io.github.akhilesh2491.scry.ui.ScryActivity"
