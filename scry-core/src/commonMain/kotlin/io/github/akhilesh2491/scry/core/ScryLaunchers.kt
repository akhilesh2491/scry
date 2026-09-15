package io.github.akhilesh2491.scry.core

/** Which corner the floating bubble starts in, before anyone drags it. */
public enum class BubbleCorner {
    TOP_START,
    TOP_END,
    BOTTOM_START,
    BOTTOM_END,
}

/**
 * How the host can reach the Scry UI.
 *
 * Shake alone is not discoverable: it needs the emulator's extended controls,
 * it is unreliable through a case, and it does not exist on iOS or desktop. A
 * developer who installs Scry and sees nothing on screen concludes it is broken.
 * So the surfaces below are on by default.
 *
 * That is not a contradiction of [ShakeToOpen]'s opt-in rule. What that rule
 * protects against is a *background cost the caller did not ask for* — a sensor
 * listener held for the life of the process. A view in the app's own window and
 * an ongoing notification cost nothing while nobody touches them, so [shake]
 * stays off and the rest stay on.
 */
public class LauncherConfig internal constructor(
    /** Draggable button floating over the host app. Android and iOS. */
    public val bubble: Boolean,
    /** Ongoing notification on Android; a system tray item on desktop. */
    public val notification: Boolean,
    /** Long-press-the-app-icon shortcut. Android 7.1+ and iOS. */
    public val appShortcut: Boolean,
    /**
     * A "Scry" icon in the Android launcher drawer.
     *
     * Off by default, and the only surface here that is. It needs an
     * `exported="true"` component, and Scry's UI is `exported="false"` on
     * purpose — it shows request bodies, preferences and database rows. Turning
     * this on lets any app on the device open that UI, so it is a decision the
     * host makes explicitly rather than one inherited from a default.
     */
    public val launcherIcon: Boolean,
    /** Starts [ShakeToOpen] for you. Off: it holds an accelerometer listener. */
    public val shake: Boolean,
    /** Where [bubble] sits until it is dragged somewhere else. */
    public val bubbleCorner: BubbleCorner,
) {
    public companion object {
        /** Bubble, notification and app shortcut on; drawer icon and shake off. */
        public val DEFAULT: LauncherConfig = LauncherConfigBuilder().build()

        /** Nothing but `Scry.show()`. */
        public val NONE: LauncherConfig = LauncherConfigBuilder()
            .apply {
                bubble = false
                notification = false
                appShortcut = false
            }
            .build()
    }

    override fun toString(): String =
        "LauncherConfig(bubble=$bubble, notification=$notification, " +
            "appShortcut=$appShortcut, launcherIcon=$launcherIcon, shake=$shake, " +
            "bubbleCorner=$bubbleCorner)"
}

/** Builder behind the `launchers { }` block of the install DSL. */
public class LauncherConfigBuilder internal constructor() {

    /** See [LauncherConfig.bubble]. */
    public var bubble: Boolean = true

    /** See [LauncherConfig.notification]. */
    public var notification: Boolean = true

    /** See [LauncherConfig.appShortcut]. */
    public var appShortcut: Boolean = true

    /** See [LauncherConfig.launcherIcon]. Read the KDoc before setting it. */
    public var launcherIcon: Boolean = false

    /** See [LauncherConfig.shake]. */
    public var shake: Boolean = false

    /** See [LauncherConfig.bubbleCorner]. */
    public var bubbleCorner: BubbleCorner = BubbleCorner.BOTTOM_END

    internal fun build(): LauncherConfig = LauncherConfig(
        bubble = bubble,
        notification = notification,
        appShortcut = appShortcut,
        launcherIcon = launcherIcon,
        shake = shake,
        bubbleCorner = bubbleCorner,
    )
}
