@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package io.github.akhilesh2491.scry.core

import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIApplication
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeCustom
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIFont
import platform.UIKit.UIGestureRecognizerStateBegan
import platform.UIKit.UIGestureRecognizerStateChanged
import platform.UIKit.UIPanGestureRecognizer
import platform.UIKit.UIScreen
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowLevelAlert
import platform.UIKit.UIWindowScene
import platform.UIKit.accessibilityLabel
import platform.darwin.NSObject

private const val BUBBLE_SIZE = 56.0
private const val BUBBLE_MARGIN = 16.0
private const val SCENE_DID_ACTIVATE = "UISceneDidActivateNotification"

/**
 * A draggable button floating over the host app that opens Scry.
 *
 * iOS has no shake hook Scry can install without the host overriding
 * `motionEnded` or the library swizzling `UIWindow`, and neither is something a
 * debug library should ask for. The bubble is the answer: it is visible from the
 * first launch and needs nothing from the app.
 *
 * It lives in its **own** `UIWindow`, sized to exactly the button. That sizing is
 * the whole trick — UIKit only delivers touches to a window when they land inside
 * its frame, so the app underneath stays fully interactive without any
 * `hitTest` override or passthrough view.
 *
 * Shown for you by [Scry.install] unless `launchers { bubble = false }`.
 */
public object ScryBubble {

    private var window: UIWindow? = null
    private var sceneObserver: Any? = null
    private var corner: BubbleCorner = BubbleCorner.BOTTOM_END
    private val handler = BubbleHandler()

    /** Shows the bubble, or waits for a scene to become active and then shows it. */
    public fun attach(corner: BubbleCorner = BubbleCorner.BOTTOM_END) {
        this.corner = corner
        if (window != null) return
        if (!present()) observeSceneActivation()
    }

    /** Removes the bubble. */
    public fun detach() {
        window?.hidden = true
        window = null
        sceneObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        sceneObserver = null
    }

    /** Whether the bubble is currently on screen. */
    public fun isVisible(): Boolean = window != null

    /**
     * Builds and shows the window. Returns false when there is no active scene yet.
     *
     * `Scry.install` runs from application start-up, which on a scene-based app is
     * before any scene has connected — so this genuinely does fail the first time
     * and has to be retried from the notification below.
     */
    private fun present(): Boolean {
        val scene = activeWindowScene() ?: return false

        val screen = UIScreen.mainScreen.bounds
        val screenWidth = screen.useContents { size.width }
        val screenHeight = screen.useContents { size.height }
        val x = when (corner) {
            BubbleCorner.TOP_START, BubbleCorner.BOTTOM_START -> BUBBLE_MARGIN
            BubbleCorner.TOP_END, BubbleCorner.BOTTOM_END ->
                screenWidth - BUBBLE_SIZE - BUBBLE_MARGIN
        }
        val y = when (corner) {
            BubbleCorner.TOP_START, BubbleCorner.TOP_END -> BUBBLE_MARGIN + TOP_INSET
            BubbleCorner.BOTTOM_START, BubbleCorner.BOTTOM_END ->
                screenHeight - BUBBLE_SIZE - BUBBLE_MARGIN - BOTTOM_INSET
        }

        val overlay = UIWindow(frame = CGRectMake(x, y, BUBBLE_SIZE, BUBBLE_SIZE))
        overlay.windowScene = scene
        // Below alert so a system alert is never covered by a debug button.
        overlay.windowLevel = UIWindowLevelAlert - 1
        overlay.backgroundColor = UIColor.clearColor
        // Never makeKeyAndVisible: taking key status away from the app's own
        // window breaks its keyboard and first-responder handling.
        overlay.rootViewController = UIViewController().apply {
            view?.backgroundColor = UIColor.clearColor
            view?.addSubview(createButton())
        }
        overlay.hidden = false

        window = overlay
        return true
    }

    private fun createButton(): UIView {
        val button = UIButton.buttonWithType(UIButtonTypeCustom)
        button.setFrame(CGRectMake(0.0, 0.0, BUBBLE_SIZE, BUBBLE_SIZE))
        button.setTitle("S", forState = UIControlStateNormal)
        button.setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
        button.titleLabel?.font = UIFont.boldSystemFontOfSize(20.0)
        button.backgroundColor = UIColor.colorWithRed(0.31, 0.42, 0.93, 0.92)
        button.layer.cornerRadius = BUBBLE_SIZE / 2
        button.clipsToBounds = true
        button.accessibilityLabel = "Open Scry"
        button.addTarget(
            target = handler,
            action = NSSelectorFromString("onTap"),
            forControlEvents = UIControlEventTouchUpInside,
        )
        button.addGestureRecognizer(
            UIPanGestureRecognizer(handler, NSSelectorFromString("onPan:")),
        )
        return button
    }

    private fun activeWindowScene(): UIWindowScene? =
        UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .firstOrNull { it.activationState == UISceneActivationStateForegroundActive }

    private fun observeSceneActivation() {
        if (sceneObserver != null) return
        sceneObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = SCENE_DID_ACTIVATE,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            if (window == null && present()) {
                sceneObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
                sceneObserver = null
            }
        }
    }

    /** Moves the window by a drag, then snaps it to the nearer screen edge. */
    internal fun moveBy(deltaX: Double, deltaY: Double, finished: Boolean) {
        val overlay = window ?: return
        val screen = UIScreen.mainScreen.bounds
        val screenWidth = screen.useContents { size.width }
        val screenHeight = screen.useContents { size.height }

        val currentX = overlay.frame.useContents { origin.x }
        val currentY = overlay.frame.useContents { origin.y }
        var newX = currentX + deltaX
        val newY = (currentY + deltaY)
            .coerceIn(BUBBLE_MARGIN + TOP_INSET, screenHeight - BUBBLE_SIZE - BUBBLE_MARGIN)

        if (finished) {
            // Same rule as Android: park on a side, never mid-screen.
            newX = if (newX + BUBBLE_SIZE / 2 < screenWidth / 2) {
                BUBBLE_MARGIN
            } else {
                screenWidth - BUBBLE_SIZE - BUBBLE_MARGIN
            }
        } else {
            newX = newX.coerceIn(BUBBLE_MARGIN, screenWidth - BUBBLE_SIZE - BUBBLE_MARGIN)
        }

        overlay.setFrame(CGRectMake(newX, newY, BUBBLE_SIZE, BUBBLE_SIZE))
    }

    // Rough stand-ins for the safe areas: the window is created before any view
    // has a layout, so real insets are not available yet, and being 44pt clear of
    // the notch and the home indicator is all this needs.
    private const val TOP_INSET = 44.0
    private const val BOTTOM_INSET = 34.0
}

/**
 * Target for the button's tap and pan.
 *
 * UIKit's target-action needs an Objective-C object with real selectors, which a
 * Kotlin object declaration is not — hence this small [NSObject] shim.
 */
private class BubbleHandler : NSObject() {

    private var lastX = 0.0
    private var lastY = 0.0

    @ObjCAction
    fun onTap() {
        Scry.show()
    }

    @ObjCAction
    fun onPan(recognizer: UIPanGestureRecognizer) {
        val translation = recognizer.translationInView(recognizer.view)
        val x = translation.useContents { x }
        val y = translation.useContents { y }

        when (recognizer.state) {
            UIGestureRecognizerStateBegan -> {
                lastX = 0.0
                lastY = 0.0
            }

            UIGestureRecognizerStateChanged -> {
                ScryBubble.moveBy(x - lastX, y - lastY, finished = false)
                lastX = x
                lastY = y
            }

            else -> ScryBubble.moveBy(x - lastX, y - lastY, finished = true)
        }
    }
}
