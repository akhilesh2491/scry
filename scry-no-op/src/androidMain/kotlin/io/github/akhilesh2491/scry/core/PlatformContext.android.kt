@file:Suppress("UNUSED_PARAMETER", "unused")

package io.github.akhilesh2491.scry.core

import android.content.Context

/** Inert mirrors of the Android surface of `scry-core`. */

public actual class PlatformContext(context: Context) {
    public val androidContext: Context = context.applicationContext
}

public fun Scry.install(
    context: Context,
    configure: ScryConfigBuilder.() -> Unit = {},
): ScryInstance? = null

public object ScryAndroid {
    @JvmStatic
    public fun installer(context: Context): ScryInstaller = Scry.installer(PlatformContext(context))

    @JvmStatic
    public fun context(context: Context): PlatformContext = PlatformContext(context)
}

/**
 * Registers no sensor listener.
 *
 * The single most valuable thing this stub removes: the real detector holds an
 * accelerometer registration for the life of the app, and that is exactly the
 * kind of background cost you must not ship to users.
 */
public class ShakeToOpen @JvmOverloads constructor(
    context: Context,
    thresholdG: Float = 2.7f,
    debounceMillis: Long = 1_000L,
) {
    public fun start(): Unit = Unit
    public fun stop(): Unit = Unit
}
