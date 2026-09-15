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

/**
 * Draws nothing over the app.
 *
 * The real bubble registers `ActivityLifecycleCallbacks` for the life of the
 * process and adds a view to every activity. A shipped app gets neither.
 */
public object ScryBubble {
    @JvmStatic
    @JvmOverloads
    public fun attach(
        application: android.app.Application,
        corner: BubbleCorner = BubbleCorner.BOTTOM_END,
    ): Unit = Unit

    @JvmStatic
    public fun detach(): Unit = Unit

    @JvmStatic
    public fun setVisible(visible: Boolean): Unit = Unit

    @JvmStatic
    public fun isVisible(): Boolean = false
}

/** Posts nothing. No channel is created, so users never see a Scry notification. */
public object ScryNotification {
    @JvmStatic
    public fun show(context: Context): Boolean = false

    @JvmStatic
    public fun hide(context: Context): Unit = Unit
}

/** Publishes no shortcut. */
public object ScryAppShortcut {
    @JvmStatic
    public fun add(context: Context): Unit = Unit

    @JvmStatic
    public fun remove(context: Context): Unit = Unit
}

/**
 * Toggles nothing.
 *
 * There is no launcher trampoline in a release build to enable — which is the
 * point: the exported component the real module ships disabled does not exist
 * here at all.
 */
public object ScryLauncherIcon {
    @JvmStatic
    public fun enable(context: Context): Unit = Unit

    @JvmStatic
    public fun disable(context: Context): Unit = Unit

    @JvmStatic
    public fun isEnabled(context: Context): Boolean = false
}
