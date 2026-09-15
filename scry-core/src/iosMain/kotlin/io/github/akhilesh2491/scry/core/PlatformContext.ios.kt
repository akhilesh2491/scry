@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.akhilesh2491.scry.core

import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSThread
import platform.Foundation.NSUserDomainMask
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

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

/**
 * Puts the bubble and the home-screen quick action up.
 *
 * Both touch UIKit, so both are dispatched to the main queue: `Scry.install` is
 * commonly called from a shared-module entry point that Swift invokes off the
 * main thread, and UIKit called from anywhere else is a crash.
 */
internal actual fun PlatformContext.startLaunchers(config: LauncherConfig) {
    onMainQueue {
        if (config.bubble) ScryBubble.attach(config.bubbleCorner)
        if (config.appShortcut) ScryQuickAction.add()
    }
}

internal actual fun stopLaunchers() {
    onMainQueue {
        ScryBubble.detach()
        ScryQuickAction.remove()
    }
}

/** Runs [block] on the main queue, or right here when that is already where we are. */
private fun onMainQueue(block: () -> Unit) {
    if (NSThread.isMainThread) block() else dispatch_async(dispatch_get_main_queue(), block)
}
