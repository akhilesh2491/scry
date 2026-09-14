package io.github.akhilesh2491.scry.analytics

import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * The analytics capture facade.
 *
 * ```kotlin
 * class Analytics(private val firebase: FirebaseAnalytics) {
 *     fun track(name: String, params: Map<String, Any?>) {
 *         firebase.logEvent(name, params.toBundle())
 *         ScryAnalytics.track(name, params)   // <- the whole integration
 *     }
 * }
 * ```
 *
 * Calls cost a null check until an [AnalyticsPlugin] is installed, so leaving
 * them in shared code that also compiles against `scry-no-op` in release is safe.
 *
 * Scry captures what the app hands it and nothing else. It does not hook the
 * Firebase, Segment or Amplitude SDKs — an event the app sends without telling
 * Scry is invisible here, which is a limitation worth knowing rather than a bug
 * to report.
 */
public object ScryAnalytics {

    internal var plugin: AnalyticsPlugin? = null
        private set

    internal fun attach(plugin: AnalyticsPlugin) {
        this.plugin = plugin
    }

    /** Detaches the sink. Used when Scry is uninstalled, and by tests. */
    @JvmStatic
    public fun detach() {
        plugin = null
    }

    /**
     * Records an event against the screen the app is currently on.
     *
     * [params] takes `Any?` because that is the shape every analytics SDK
     * accepts; values are boxed into [AnalyticsValue] so the spec can tell a
     * number from a string that looks like one.
     */
    @JvmStatic
    @JvmOverloads
    public fun track(
        name: String,
        params: Map<String, Any?> = emptyMap(),
        destination: String? = null,
    ) {
        plugin?.track(name, params, destination)
    }

    /**
     * Announces that the app moved to [screen].
     *
     * Only needed when the performance plugin is not installed — it publishes
     * screen changes on the event bus already, and this plugin listens. Calling
     * both is harmless: a repeat of the current screen is ignored.
     *
     * Deliberately not a `@Composable`, for the same reason as
     * `ScryTrace.screenEntered`: everything an app calls in shared code needs an
     * inert mirror in `scry-no-op`, and a `@Composable` mirror would mean
     * shipping Compose in release builds.
     */
    @JvmStatic
    public fun screenEntered(screen: String) {
        plugin?.screenEntered(screen)
    }

    /**
     * Judges the current screen's expectations now.
     *
     * For instrumented tests: drive the screen, call this, then assert on
     * [AnalyticsPlugin.issues] or collect them through `onIssue`.
     */
    @JvmStatic
    public fun checkNow() {
        plugin?.settleCurrentVisit()
    }
}
