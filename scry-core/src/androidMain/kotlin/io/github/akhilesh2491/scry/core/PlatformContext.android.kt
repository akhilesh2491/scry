package io.github.akhilesh2491.scry.core

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import java.io.File

/**
 * Wraps the Android [Context] Scry needs.
 *
 * A wrapper rather than an `actual typealias` to `Context`: `Context` is
 * abstract, and an `expect class` cannot be actualised by an abstract type.
 * Callers rarely construct this directly — the [Scry.install] and
 * [ScryAndroid.installer] overloads below take a plain `Context`.
 */
public actual class PlatformContext(context: Context) {
    /** The host application's context. Always the application context. */
    public val androidContext: Context = context.applicationContext
}

internal actual fun PlatformContext.scryStorageDirectory(): String =
    File(androidContext.filesDir, "scry").apply { mkdirs() }.absolutePath

internal actual fun PlatformContext.isDebuggableBuild(): Boolean =
    (androidContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

internal actual fun PlatformContext.applicationId(): String = androidContext.packageName

// Held so `stopLaunchers` can undo exactly what was started, including the shake
// detector — an accelerometer listener that outlives the installation is the one
// leak this library cannot defend. Both are application-scoped, so neither
// retains an Activity.
private var startedShakeToOpen: ShakeToOpen? = null
private var launcherContext: Context? = null

internal actual fun PlatformContext.startLaunchers(config: LauncherConfig) {
    val context = androidContext
    launcherContext = context

    if (config.bubble) {
        // Only an Application can hand out lifecycle callbacks. A host that
        // installed from an Activity context still gets every other surface.
        (context as? Application)?.let { ScryBubble.attach(it, config.bubbleCorner) }
    }
    if (config.notification) ScryNotification.show(context)
    if (config.appShortcut) ScryAppShortcut.add(context)
    // Applied both ways, unlike the others. Component enablement is sticky —
    // it survives reinstalls — so a developer who tried the drawer icon once
    // could not get rid of it by flipping the flag back, which is exactly what
    // they would try.
    if (config.launcherIcon) ScryLauncherIcon.enable(context) else ScryLauncherIcon.disable(context)
    if (config.shake) startedShakeToOpen = ShakeToOpen(context).also { it.start() }
}

internal actual fun stopLaunchers() {
    startedShakeToOpen?.stop()
    startedShakeToOpen = null
    ScryBubble.detach()
    launcherContext?.let { context ->
        ScryNotification.hide(context)
        ScryAppShortcut.remove(context)
        // The drawer icon is left alone on purpose: it is an explicit, sticky
        // choice by the host (often "this is the QA build"), and uninstall is
        // usually a prelude to installing again.
    }
    launcherContext = null
}

/**
 * Installs Scry from an Android [Context].
 *
 * Keeps the idiomatic call site — `Scry.install(this) { ... }` from an
 * `Application` — despite [PlatformContext] being a wrapper.
 */
public fun Scry.install(
    context: Context,
    configure: ScryConfigBuilder.() -> Unit = {},
): ScryInstance? = install(PlatformContext(context), configure)

/**
 * Android entry points for Java callers.
 *
 * Kotlin extensions on an object compile to awkward static calls from Java
 * (`ScryKt.install(Scry.INSTANCE, ...)`), so Java gets real statics here.
 */
public object ScryAndroid {

    /** `ScryAndroid.installer(context).addPlugin(...).install()` */
    @JvmStatic
    public fun installer(context: Context): ScryInstaller =
        Scry.installer(PlatformContext(context))

    /** Wraps a [Context] when you need the [PlatformContext] directly. */
    @JvmStatic
    public fun context(context: Context): PlatformContext = PlatformContext(context)
}
