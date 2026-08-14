package io.github.akhilesh2491.scry.perf

import androidx.compose.runtime.Composable
import io.github.akhilesh2491.scry.core.PlatformContext
import io.github.akhilesh2491.scry.core.ScryContextSection
import io.github.akhilesh2491.scry.core.ScryEvent
import io.github.akhilesh2491.scry.core.ScryEventBus
import io.github.akhilesh2491.scry.core.ScryScope
import io.github.akhilesh2491.scry.core.ScryStore
import io.github.akhilesh2491.scry.ui.ScryUiPlugin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlin.jvm.JvmStatic

/** Published when a measurement breaks its budget. */
public data class PerfViolationEvent(
    public val violation: PerfViolation,
    override val timestampMillis: Long = violation.timestampMillis,
) : ScryEvent

/** Builder for [PerfPlugin]. */
public class PerfPluginBuilder internal constructor() {

    /** Measure process start through to the first frame. */
    public var trackStartup: Boolean = true

    /** Measure how long each screen takes to appear. */
    public var trackScreens: Boolean = true

    /**
     * Measure per-frame durations.
     *
     * Android and iOS only — see the module docs. On desktop this is accepted and
     * ignored rather than rejected, so the same shared setup code compiles and
     * runs on every target.
     */
    public var trackFrames: Boolean = true

    /**
     * Also hook `FragmentManager`, when the app has androidx.fragment on its
     * classpath. Ignored everywhere else.
     */
    public var trackFragments: Boolean = true

    /**
     * Frame samples retained per screen for percentile calculation.
     *
     * Counts are kept for the whole session regardless; this only bounds the
     * window the percentiles describe. 5 000 is roughly 40 seconds at 120 Hz.
     */
    public var maxFramesPerScreen: Int = 5_000

    /** How many previous launches to keep for comparison. */
    public var maxSessions: Int = 20

    /**
     * Persist sessions so they survive process death.
     *
     * On by default, unlike the log plugin: a session is one small record per
     * launch, and a performance history that resets every launch cannot answer
     * the question this plugin exists for.
     */
    public var persist: Boolean = true

    internal var budget: PerfBudget = PerfBudget.DEFAULT

    internal var listener: ((PerfViolation) -> Unit)? = null

    /** Overrides the thresholds. See [PerfBudget]. */
    public fun budget(configure: PerfBudgetBuilder.() -> Unit) {
        budget = PerfBudgetBuilder(budget).apply(configure).build()
    }

    /**
     * Called for every budget violation.
     *
     * The hook exists so a team can fail a QA run or forward to their own
     * telemetry. Scry does not phone home, and adding a reporting backend here
     * would be the wrong thing to build.
     */
    public fun onViolation(listener: (PerfViolation) -> Unit) {
        this.listener = listener
    }
}

/**
 * Measures how fast the app is, on the device, while you use it.
 *
 * ```kotlin
 * plugin(PerfPlugin {
 *     budget { coldStartupMillis = 800 }
 * })
 *
 * ScryTrace.measure("checkout") { placeOrder() }
 * ```
 *
 * Startup, screen loads and frame timing are captured automatically where the
 * platform allows it. What each target supports differs, and the plugin's screen
 * says so rather than showing an empty chart.
 */
public class PerfPlugin(
    configure: PerfPluginBuilder.() -> Unit = {},
) : ScryUiPlugin {

    internal val config: PerfPluginBuilder = PerfPluginBuilder().apply(configure)

    override val id: String = PLUGIN_ID
    override val displayName: String = "Performance"

    private val _session = MutableStateFlow(
        PerfSession(id = PerfSession.newId(), startedAtMillis = nowMillis(), platform = "unknown"),
    )

    /** This launch. */
    public val session: StateFlow<PerfSession> = _session.asStateFlow()

    private val _previousSessions = MutableStateFlow<List<PerfSession>>(emptyList())

    /** Earlier launches, newest first. */
    public val previousSessions: StateFlow<List<PerfSession>> = _previousSessions.asStateFlow()

    private val _badge = MutableStateFlow<String?>(null)
    override val badge: StateFlow<String?> = _badge.asStateFlow()

    private val _framesUnsupportedReason = MutableStateFlow<String?>(null)

    /**
     * Why frames are not being captured, or null when they are.
     *
     * A reason rather than a boolean: "no frame data" and "this device is too old
     * for `FrameMetrics`" send someone looking in very different places, and the
     * screen should not make them guess which one they are looking at.
     */
    public val framesUnsupportedReason: StateFlow<String?> = _framesUnsupportedReason.asStateFlow()

    private val rings = mutableMapOf<String, FrameRing>()
    private var store: ScryStore? = null
    private var events: ScryEventBus? = null
    private var monitor: AutoCloseable? = null
    private var sequence: Long = 0

    /** The screen frames are currently being attributed to. */
    internal var currentScreen: String = ROOT_SCREEN
        private set

    /** A screen that has announced itself but not yet been drawn. */
    private var pendingScreen: String? = null
    private var pendingScreenStart: Long = 0
    private var pendingScreenKind: ScreenKind = ScreenKind.COMPOSABLE

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun onInstall(scope: ScryScope) {
        events = scope.events
        store = scope.store.takeIf { config.persist }

        val platform = perfPlatform(scope.context)
        _session.value = _session.value.copy(
            platform = platform.name,
            deviceModel = platform.deviceModel,
            appVersion = platform.appVersion,
        )

        if (config.persist) {
            _previousSessions.value = scope.store.read(PLUGIN_ID)
                .mapNotNull { runCatching { json.decodeFromString<PerfSession>(it.payload) }.getOrNull() }
                .filter { it.id != _session.value.id }
                .sortedByDescending { it.startedAtMillis }
                .take(config.maxSessions)
        }

        ScryTrace.attach(this)

        monitor = startPerfMonitor(scope.context, this)
        if (monitor == null && config.trackFrames && _framesUnsupportedReason.value == null) {
            _framesUnsupportedReason.value = "Frame timing is not captured on this target."
        }

        scope.contextRegistry.register(PLUGIN_ID) { contextSections() }
        persist()
        updateBadge()
    }

    override fun onClear() {
        rings.clear()
        _previousSessions.value = emptyList()
        _session.value = PerfSession(
            id = PerfSession.newId(),
            startedAtMillis = nowMillis(),
            platform = _session.value.platform,
            deviceModel = _session.value.deviceModel,
            appVersion = _session.value.appVersion,
        )
        store?.clear(PLUGIN_ID)
        updateBadge()
    }

    // ---- recording -------------------------------------------------------

    /**
     * Records a startup.
     *
     * Cold and warm starts describe the launch itself, so only the first is kept.
     * Hot starts are returns to the foreground and can happen any number of
     * times, so they accumulate in [PerfSession.hotStarts].
     */
    public fun recordStartup(metric: StartupMetric) {
        if (!config.trackStartup) return

        if (metric.kind == StartupKind.HOT) {
            _session.value = _session.value.copy(
                hotStarts = (_session.value.hotStarts + metric).takeLast(MAX_HOT_STARTS),
            )
        } else {
            if (_session.value.startup != null) return
            _session.value = _session.value.copy(startup = metric)
            config.budget.check(metric)?.let(::raise)
        }

        persist()
        updateBadge()
    }

    /** Records how long a screen took to appear. */
    public fun recordScreenLoad(
        screen: String,
        kind: ScreenKind,
        ttidMillis: Long,
        ttfdMillis: Long? = null,
    ) {
        if (!config.trackScreens) return
        val metric = ScreenLoadMetric(
            id = nextId(),
            screen = screen,
            kind = kind,
            ttidMillis = ttidMillis,
            timestampMillis = nowMillis(),
            ttfdMillis = ttfdMillis,
        )
        _session.value = _session.value.copy(screens = _session.value.screens + metric)
        config.budget.check(metric)?.let(::raise)
        persist()
        updateBadge()
    }

    /**
     * Attaches a time-to-full-display to the most recent load of [screen].
     *
     * Separate from [recordScreenLoad] because TTFD arrives later than TTID by
     * definition — the screen is already on-screen when its data lands.
     */
    public fun recordFullyDrawn(screen: String, ttfdMillis: Long) {
        if (!config.trackScreens) return
        val screens = _session.value.screens
        val index = screens.indexOfLast { it.screen == screen && it.ttfdMillis == null }
        if (index < 0) return
        val updated = screens.toMutableList()
        updated[index] = screens[index].copy(ttfdMillis = ttfdMillis)
        _session.value = _session.value.copy(screens = updated)
        config.budget.check(updated[index])?.let(::raise)
        persist()
        updateBadge()
    }

    /**
     * Records one frame. Called from the platform frame callback.
     *
     * Hot path: this runs up to 120 times a second on the main thread, so it does
     * no allocation beyond the ring's own and only recomputes aggregates every
     * [FRAME_AGGREGATION_INTERVAL] frames.
     */
    public fun recordFrame(screen: String, durationMillis: Double, budgetMillis: Double) {
        if (!config.trackFrames) return
        val ring = rings.getOrPut(screen) { FrameRing(config.maxFramesPerScreen) }
        ring.add(durationMillis, budgetMillis)

        // The first frame after a screen announced itself *is* its initial
        // display. Deriving TTID from the frame stream rather than a Compose
        // side-effect is what lets this work identically for an Activity, a
        // Fragment, a composable and a UIKit view controller.
        pendingScreen?.let { pending ->
            pendingScreen = null
            recordScreenLoad(pending, pendingScreenKind, monotonicMillis() - pendingScreenStart)
        }

        val frozen = durationMillis > FrameStats.FROZEN_FRAME_MILLIS
        if (ring.totalFrames % FRAME_AGGREGATION_INTERVAL != 0 && !frozen) return

        val stats = ring.snapshot(screen)
        val frames = _session.value.frames.toMutableList()
        val index = frames.indexOfFirst { it.screen == screen }
        if (index >= 0) frames[index] = stats else frames += stats
        _session.value = _session.value.copy(frames = frames)

        config.budget.check(stats, nowMillis()).forEach(::raise)
        updateBadge()
    }

    /** Records a manually timed block. See [ScryTrace]. */
    public fun recordSpan(name: String, durationMillis: Long, startedAtMillis: Long, depth: Int) {
        val span = SpanMetric(
            id = nextId(),
            name = name,
            durationMillis = durationMillis,
            startedAtMillis = startedAtMillis,
            depth = depth,
        )
        _session.value = _session.value.copy(spans = (_session.value.spans + span).takeLast(MAX_SPANS))
    }

    /**
     * Marks which screen subsequent frames belong to, and starts its load clock.
     *
     * The measurement completes on the next frame Scry observes. On a target with
     * no frame monitor (desktop) nothing completes it, and the caller should use
     * [recordScreenLoad] directly instead.
     */
    public fun screenEntered(screen: String, kind: ScreenKind = ScreenKind.COMPOSABLE) {
        currentScreen = screen
        pendingScreen = screen
        pendingScreenKind = kind
        pendingScreenStart = monotonicMillis()
    }

    /**
     * Attributes subsequent frames to [screen] without starting a load clock.
     *
     * For hosts that measure the load themselves — an Android `Activity`, whose
     * first draw is observable directly and more precisely than "the next frame
     * after something happened".
     */
    public fun attributeFramesTo(screen: String) {
        currentScreen = screen
    }

    /** Frame stats for one screen, aggregated up to this instant. */
    public fun frameStats(screen: String): FrameStats? = rings[screen]?.snapshot(screen)

    /** The most recent frame durations for one screen, oldest first. */
    public fun recentFrames(screen: String, count: Int): List<Double> =
        rings[screen]?.recent(count).orEmpty()

    /** Called by the platform monitor when it cannot observe frames here. */
    internal fun markFramesUnsupported(reason: String) {
        _framesUnsupportedReason.value = reason
    }

    // ---- internals -------------------------------------------------------

    private fun raise(violation: PerfViolation) {
        // Deduplicated by kind+label: a screen that stays janky produces a
        // violation on every aggregation tick, and a badge counting into the
        // hundreds tells you less than one that says "two things are wrong".
        val existing = _session.value.violations
        if (existing.any { it.kind == violation.kind && it.label == violation.label }) return

        _session.value = _session.value.copy(violations = existing + violation)
        events?.publish(PerfViolationEvent(violation))
        config.listener?.invoke(violation)
        persist()
    }

    private fun updateBadge() {
        val violations = _session.value.violations.size
        val session = _session.value
        val measurements = session.screens.size +
            session.frames.size +
            (if (session.startup != null) 1 else 0)
        _badge.value = when {
            violations > 0 -> violations.toString()
            measurements > 0 -> measurements.toString()
            else -> null
        }
    }

    private fun persist() {
        val store = store ?: return
        val session = _session.value
        store.put(
            pluginId = PLUGIN_ID,
            id = session.id,
            payload = json.encodeToString(session),
            createdAtMillis = session.startedAtMillis,
        )
    }

    private fun nextId(): String = "${nowMillis()}-${sequence++}"

    private fun contextSections(): List<ScryContextSection> {
        val session = _session.value
        val lines = buildList {
            session.startup?.let {
                add("Startup: ${it.kind} ${it.totalMillis}ms${if (it.truncated) " (truncated)" else ""}")
            }
            session.screens.takeLast(CONTEXT_SCREEN_COUNT).forEach {
                add("Screen ${it.screen}: TTID ${it.ttidMillis}ms" + (it.ttfdMillis?.let { f -> ", TTFD ${f}ms" } ?: ""))
            }
            session.frames.forEach {
                add(
                    "Frames ${it.screen}: ${it.frameCount} drawn, ${it.slowFrames} slow, " +
                        "${it.frozenFrames} frozen, p99 ${it.p99Millis.roundTo(1)}ms",
                )
            }
            session.violations.forEach { add("Budget: ${it.format()}") }
        }
        if (lines.isEmpty()) return emptyList()
        return listOf(ScryContextSection(title = "Performance", body = lines.joinToString("\n")))
    }

    @Composable
    override fun Content() {
        PerfScreen(plugin = this)
    }

    public companion object {
        public const val PLUGIN_ID: String = "scry.perf"

        /** Screen name used before any screen has announced itself. */
        public const val ROOT_SCREEN: String = "App"

        /** Recompute frame aggregates every N frames. */
        private const val FRAME_AGGREGATION_INTERVAL: Int = 20

        /** Spans are unbounded in principle; this keeps a runaway loop survivable. */
        private const val MAX_SPANS: Int = 500

        /** A long session can foreground hundreds of times; only the recent ones matter. */
        private const val MAX_HOT_STARTS: Int = 50

        /** How many screen loads to attach to a crash report. */
        private const val CONTEXT_SCREEN_COUNT: Int = 10

        /** The installed plugin, if any. */
        @JvmStatic
        public fun installed(): PerfPlugin? = ScryTrace.plugin
    }
}

/**
 * Starts platform instrumentation. Returns a handle that stops it, or null when
 * the target cannot observe frames at all.
 */
internal expect fun startPerfMonitor(context: PlatformContext, plugin: PerfPlugin): AutoCloseable?
