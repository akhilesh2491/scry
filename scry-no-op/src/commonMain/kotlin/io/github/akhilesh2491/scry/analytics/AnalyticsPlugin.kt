@file:Suppress("UNUSED_PARAMETER", "unused")

package io.github.akhilesh2491.scry.analytics

import io.github.akhilesh2491.scry.core.ScryEvent
import io.github.akhilesh2491.scry.core.ScryPlugin
import io.github.akhilesh2491.scry.core.ScryScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.jvm.JvmStatic

public data class AnalyticsIssueEvent(
    public val issue: AnalyticsIssue,
    override val timestampMillis: Long = issue.timestampMillis,
) : ScryEvent

public data class AnalyticsTrackedEvent(
    public val event: AnalyticsEvent,
    override val timestampMillis: Long = event.timestampMillis,
) : ScryEvent

public class AnalyticsPluginBuilder internal constructor() {
    public var maxEvents: Int = 0
    public var maxVisits: Int = 0
    public var persist: Boolean = false
    public var flagUnexpectedEvents: Boolean = false
    public var settleMillis: Long = 0

    public fun expect(screen: String, configure: ScreenExpectationBuilder.() -> Unit): Unit = Unit
    public fun onIssue(listener: (AnalyticsIssue) -> Unit): Unit = Unit
}

/**
 * Captures nothing and subscribes to no screen changes.
 *
 * The stub that matters most for privacy rather than performance: the real
 * plugin holds every analytics parameter the app has sent this session, which is
 * exactly the data that must not be sitting in a user's build.
 */
public class AnalyticsPlugin(
    configure: AnalyticsPluginBuilder.() -> Unit = {},
) : ScryPlugin {

    override val id: String = PLUGIN_ID
    override val displayName: String = "Analytics"
    override val badge: StateFlow<String?> = MutableStateFlow(null)

    public val spec: AnalyticsSpec = AnalyticsSpec.NONE
    public val events: StateFlow<List<AnalyticsEvent>> = MutableStateFlow(emptyList())
    public val visits: StateFlow<List<ScreenVisit>> = MutableStateFlow(emptyList())
    public val issues: StateFlow<List<AnalyticsIssue>> = MutableStateFlow(emptyList())
    public val currentScreen: StateFlow<String> = MutableStateFlow(ROOT_SCREEN)

    override fun onInstall(scope: ScryScope): Unit = Unit
    override fun onClear(): Unit = Unit

    public fun track(
        name: String,
        params: Map<String, Any?> = emptyMap(),
        destination: String? = null,
    ): Unit = Unit

    public fun record(event: AnalyticsEvent): Unit = Unit
    public fun screenEntered(screen: String): Unit = Unit
    public fun settleCurrentVisit(): Unit = Unit

    public companion object {
        public const val PLUGIN_ID: String = "scry.analytics"
        public const val ROOT_SCREEN: String = "App"

        @JvmStatic
        public fun installed(): AnalyticsPlugin? = null
    }
}
