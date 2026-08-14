@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.akhilesh2491.scry.core

import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * iOS has no ambient application context, so this carries what Scry needs.
 *
 * Defaults read from the main bundle, which is what an app wants; the
 * constructor parameters exist for tests and app extensions.
 */
public actual class PlatformContext(
    public val applicationId: String = defaultApplicationId(),
    public val storageRoot: String = defaultStorageRoot(),
) {
    public companion object {
        /** The app's bundle identifier, or a stable fallback. */
        public fun defaultApplicationId(): String =
            NSBundle.mainBundle.bundleIdentifier ?: "scry-ios-app"

        /**
         * The Documents directory.
         *
         * Not Caches: iOS may evict Caches under storage pressure, and a crash
         * report that vanishes before the next launch is worse than useless.
         */
        public fun defaultStorageRoot(): String =
            NSSearchPathForDirectoriesInDomains(
                NSDocumentDirectory,
                NSUserDomainMask,
                true,
            ).firstOrNull() as? String ?: NSFileManager.defaultManager.currentDirectoryPath
    }
}

internal actual fun PlatformContext.scryStorageDirectory(): String {
    val path = "$storageRoot/scry/$applicationId"
    NSFileManager.defaultManager.createDirectoryAtPath(
        path = path,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    return path
}

/**
 * iOS carries no debuggable flag comparable to Android's.
 *
 * Reporting true keeps Scry usable; the release guard remains meaningful where
 * it can be enforced, and the Gradle plugin is what actually keeps Scry out of
 * shipped builds.
 */
internal actual fun PlatformContext.isDebuggableBuild(): Boolean = true

internal actual fun PlatformContext.applicationId(): String = applicationId
