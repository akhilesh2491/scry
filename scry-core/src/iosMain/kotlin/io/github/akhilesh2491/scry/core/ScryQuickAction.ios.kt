package io.github.akhilesh2491.scry.core

import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationShortcutIcon
import platform.UIKit.UIApplicationShortcutIconType
import platform.UIKit.UIApplicationShortcutItem
import platform.UIKit.shortcutItems

/**
 * An "Open Scry" action under a long-press of the app icon.
 *
 * Registered at install time — no `Info.plist` entry needed, because the item is
 * dynamic. The one thing Scry cannot do for you is receive the launch: UIKit
 * delivers it to the app delegate, so forward it:
 *
 * ```swift
 * func application(
 *     _ application: UIApplication,
 *     performActionFor shortcutItem: UIApplicationShortcutItem,
 *     completionHandler: @escaping (Bool) -> Void
 * ) {
 *     completionHandler(ScryQuickAction.shared.handle(shortcutItem: shortcutItem))
 * }
 * ```
 *
 * That line is the reason the bubble, and not this, is the primary way in on iOS.
 */
public object ScryQuickAction {

    private const val SHORTCUT_TYPE: String = "io.github.akhilesh2491.scry.open"

    /** Adds the shortcut item, keeping any the app already published. */
    public fun add() {
        val application = UIApplication.sharedApplication
        val existing = application.shortcutItems.orEmpty()
            .filterIsInstance<UIApplicationShortcutItem>()
            .filterNot { it.type == SHORTCUT_TYPE }
        application.shortcutItems = existing + scryItem()
    }

    /** Removes the shortcut item, leaving the app's own alone. */
    public fun remove() {
        val application = UIApplication.sharedApplication
        application.shortcutItems = application.shortcutItems.orEmpty()
            .filterIsInstance<UIApplicationShortcutItem>()
            .filterNot { it.type == SHORTCUT_TYPE }
    }

    /**
     * Opens Scry if [shortcutItem] is Scry's.
     *
     * Returns whether it was handled, which is exactly what the app delegate's
     * completion handler wants.
     */
    public fun handle(shortcutItem: UIApplicationShortcutItem): Boolean {
        if (shortcutItem.type != SHORTCUT_TYPE) return false
        Scry.show()
        return true
    }

    private fun scryItem(): UIApplicationShortcutItem = UIApplicationShortcutItem(
        type = SHORTCUT_TYPE,
        localizedTitle = "Open Scry",
        localizedSubtitle = null,
        icon = UIApplicationShortcutIcon.iconWithType(
            UIApplicationShortcutIconType.UIApplicationShortcutIconTypeSearch,
        ),
        userInfo = null,
    )
}
