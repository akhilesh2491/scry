@file:Suppress("UNUSED_PARAMETER")

package io.github.akhilesh2491.scry.core

import platform.UIKit.UIApplicationShortcutItem

/** Inert mirror of the real iOS [PlatformContext]; same shape, no bundle lookups. */
public actual class PlatformContext(
    public val applicationId: String = DEFAULT_APPLICATION_ID,
    public val storageRoot: String = "",
) {
    public companion object {
        public const val DEFAULT_APPLICATION_ID: String = "scry-ios-app"
        public fun defaultApplicationId(): String = DEFAULT_APPLICATION_ID
        public fun defaultStorageRoot(): String = ""
    }
}

/** Creates no overlay window. A shipped app has no debug button floating over it. */
public object ScryBubble {
    public fun attach(corner: BubbleCorner = BubbleCorner.BOTTOM_END): Unit = Unit
    public fun detach(): Unit = Unit
    public fun isVisible(): Boolean = false
}

/**
 * Publishes no home-screen action, and handles none.
 *
 * [handle] returning false is the contract that matters: the app delegate line
 * a host writes once stays compiling in release and simply reports "not mine",
 * so the host's own shortcut handling still runs.
 */
public object ScryQuickAction {
    public fun add(): Unit = Unit
    public fun remove(): Unit = Unit
    public fun handle(shortcutItem: UIApplicationShortcutItem): Boolean = false
}
