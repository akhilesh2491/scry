@file:Suppress("UNUSED_PARAMETER", "unused")

package io.github.akhilesh2491.scry.perf

import io.github.akhilesh2491.scry.core.ScryEvent
import io.github.akhilesh2491.scry.core.ScryPlugin
import io.github.akhilesh2491.scry.core.ScryScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.jvm.JvmStatic

public data class PerfViolationEvent(
    public val violation: PerfViolation,
    override val timestampMillis: Long = violation.timestampMillis,
) : ScryEvent

public class PerfPluginBuilder internal constructor() {
    public var trackStartup: Boolean = false
    public var trackScreens: Boolean = false
    public var trackFrames: Boolean = false
    public var trackFragments: Boolean = false
    public var maxFramesPerScreen: Int = 0
    public var maxSessions: Int = 0
    public var persist: Boolean = false

    public fun budget(configure: PerfBudgetBuilder.() -> Unit): Unit = Unit
    public fun onViolation(listener: (PerfViolation) -> Unit): Unit = Unit
}

/**
 * Measures nothing and registers no lifecycle or frame callbacks.
 *
 * The stub whose absence would be felt most on a user's device: the real plugin
 * holds a frame callback that runs up to 120 times a second for the lifetime of
 * the process.
 */
public class PerfPlugin(
    configure: PerfPluginBuilder.() -> Unit = {},
) : ScryPlugin {

    override val id: String = PLUGIN_ID
    override val displayName: String = "Performance"
    override val badge: StateFlow<String?> = MutableStateFlow(null)

    public val session: StateFlow<PerfSession> = MutableStateFlow(
        PerfSession(id = "", startedAtMillis = 0, platform = ""),
    )
    public val previousSessions: StateFlow<List<PerfSession>> = MutableStateFlow(emptyList())
    public val framesUnsupportedReason: StateFlow<String?> = MutableStateFlow(null)

    override fun onInstall(scope: ScryScope): Unit = Unit
    override fun onClear(): Unit = Unit

    public fun recordStartup(metric: StartupMetric): Unit = Unit
    public fun recordScreenLoad(
        screen: String,
        kind: ScreenKind,
        ttidMillis: Long,
        ttfdMillis: Long? = null,
    ): Unit = Unit
    public fun recordFullyDrawn(screen: String, ttfdMillis: Long): Unit = Unit
    public fun recordFrame(screen: String, durationMillis: Double, budgetMillis: Double): Unit = Unit
    public fun recordSpan(
        name: String,
        durationMillis: Long,
        startedAtMillis: Long,
        depth: Int,
    ): Unit = Unit
    public fun screenEntered(screen: String, kind: ScreenKind = ScreenKind.COMPOSABLE): Unit = Unit
    public fun attributeFramesTo(screen: String): Unit = Unit
    public fun frameStats(screen: String): FrameStats? = null
    public fun recentFrames(screen: String, count: Int): List<Double> = emptyList()

    public companion object {
        public const val PLUGIN_ID: String = "scry.perf"
        public const val ROOT_SCREEN: String = "App"

        @JvmStatic
        public fun installed(): PerfPlugin? = null
    }
}
