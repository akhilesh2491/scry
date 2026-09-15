package io.github.akhilesh2491.scry.core

import java.io.File

/**
 * Desktop has no ambient application context, so this carries the little Scry
 * needs: an id to namespace storage under, and where that storage lives.
 */
public actual class PlatformContext @JvmOverloads public constructor(
    public val applicationId: String = DEFAULT_APPLICATION_ID,
    public val storageRoot: String = defaultStorageRoot(),
) {
    public companion object {
        public const val DEFAULT_APPLICATION_ID: String = "scry-desktop-app"

        /** `~/.scry` — outside the working directory so repeated runs share state. */
        public fun defaultStorageRoot(): String =
            File(System.getProperty("user.home") ?: ".", ".scry").absolutePath
    }
}

internal actual fun PlatformContext.scryStorageDirectory(): String =
    File(storageRoot, applicationId).apply { mkdirs() }.absolutePath

/**
 * Desktop builds carry no debuggable flag, so the release guard cannot apply.
 * Reporting `true` keeps Scry usable on desktop; the guard remains meaningful
 * where it matters, which is shipped mobile binaries.
 */
internal actual fun PlatformContext.isDebuggableBuild(): Boolean = true

internal actual fun PlatformContext.applicationId(): String = applicationId

/**
 * Nothing to start here.
 *
 * A desktop tray icon and a floating launcher window are Compose windows, and a
 * Compose window can only be created inside `application { }` — code Scry does
 * not own. `ScryDesktopWindow`, which hosts already place there, reads
 * [ScryConfig.launchers] and puts them up itself.
 */
internal actual fun PlatformContext.startLaunchers(config: LauncherConfig): Unit = Unit

internal actual fun stopLaunchers(): Unit = Unit
