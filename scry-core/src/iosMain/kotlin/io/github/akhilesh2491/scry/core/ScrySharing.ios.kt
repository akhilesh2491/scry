@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.akhilesh2491.scry.core

import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
// A category on UIViewController, so Kotlin/Native surfaces it as an extension
// property that has to be imported by name rather than coming with the class.
import platform.UIKit.popoverPresentationController

/**
 * Writes the export into the app's storage and presents a share sheet.
 *
 * Presenting from *the topmost* controller rather than the root is the whole
 * trick: Scry's own UI is itself presented modally on the root, and UIKit
 * silently refuses a second presentation on a controller that is already
 * presenting. A root-anchored share sheet therefore does nothing at all, on
 * every screen — which is exactly how this used to fail.
 */
public actual fun shareScryFile(
    context: PlatformContext,
    fileName: String,
    text: String,
): Boolean = runCatching {
    val path = "${context.scryStorageDirectory()}/$fileName"
    val written = (text as NSString).writeToFile(
        path = path,
        atomically = true,
        encoding = NSUTF8StringEncoding,
        error = null,
    )
    if (!written) return false

    val host = topmostViewController() ?: return false
    val controller = UIActivityViewController(
        activityItems = listOf(NSURL.fileURLWithPath(path)),
        applicationActivities = null,
    )

    // iPad presents this as a popover and raises rather than guessing an anchor.
    // Anchoring to the centre of the presenting view keeps one call site working
    // on both idioms; zero permitted arrow directions reads as a plain sheet.
    controller.popoverPresentationController?.let { popover ->
        popover.sourceView = host.view
        popover.sourceRect = host.view.bounds.useContents {
            CGRectMake(size.width / 2.0, size.height / 2.0, 0.0, 0.0)
        }
        popover.permittedArrowDirections = 0u
    }

    host.presentViewController(controller, animated = true, completion = null)
    true
}.onFailure {
    // Logged, not swallowed: a silent `false` is indistinguishable from a button
    // that was never wired up, which is how the last failure here went unnoticed.
    println("Scry: could not share $fileName — $it")
}.getOrDefault(false)

/**
 * The controller a modal can actually be presented from.
 *
 * Walks the presentation chain down from the key window's root, because
 * anything already presented — Scry itself, or one of the app's own sheets —
 * owns the screen until it is dismissed.
 */
private fun topmostViewController(): UIViewController? {
    var controller = keyWindow()?.rootViewController ?: return null
    while (true) {
        controller = controller.presentedViewController ?: return controller
    }
}

/**
 * The key window, scene-aware.
 *
 * `UIApplication.keyWindow` is deprecated and returns nil outright in a
 * scene-based app, which is every app built against a modern SDK — so the scene
 * graph is the primary lookup and the legacy property only a last resort.
 */
@Suppress("DEPRECATION")
private fun keyWindow(): UIWindow? {
    val application = UIApplication.sharedApplication
    val scenes = application.connectedScenes.filterIsInstance<UIWindowScene>()
    val scene = scenes.firstOrNull {
        it.activationState == UISceneActivationStateForegroundActive
    } ?: scenes.firstOrNull()

    val windows = scene?.windows?.filterIsInstance<UIWindow>().orEmpty()
    return windows.firstOrNull { it.isKeyWindow() }
        ?: windows.firstOrNull()
        ?: application.keyWindow
}
