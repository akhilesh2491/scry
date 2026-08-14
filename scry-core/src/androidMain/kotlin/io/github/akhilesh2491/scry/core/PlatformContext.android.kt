package io.github.akhilesh2491.scry.core

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
