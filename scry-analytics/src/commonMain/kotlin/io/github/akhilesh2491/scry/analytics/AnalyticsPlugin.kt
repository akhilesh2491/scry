package io.github.akhilesh2491.scry.analytics

import androidx.compose.runtime.Composable
import io.github.akhilesh2491.scry.core.REDACTED
import io.github.akhilesh2491.scry.core.Redactor
import io.github.akhilesh2491.scry.core.ScreenChangedEvent
import io.github.akhilesh2491.scry.core.ScryContextSection
import io.github.akhilesh2491.scry.core.ScryEvent
import io.github.akhilesh2491.scry.core.ScryEventBus
import io.github.akhilesh2491.scry.core.ScryScope
import io.github.akhilesh2491.scry.core.ScryStore
import io.github.akhilesh2491.scry.ui.ScryUiPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.jvm.JvmStatic

/** Published when a screen's events do not match its spec. */
public data class AnalyticsIssueEvent(
    public val issue: AnalyticsIssue,
    override val timestampMillis: Long = issue.timestampMillis,
) : ScryEvent

/** Published for every captured analytics event. */
public data class AnalyticsTrackedEvent(
    public val event: AnalyticsEvent,
    override val timestampMillis: Long = event.timestampMillis,
) : ScryEvent

/** Builder for [AnalyticsPlugin]. */
public class AnalyticsPluginBuilder internal constructor() {

    /**
     * How many events to keep in memory.
     *
     * Analytics volume sits between logs and network: a chatty app fires a few
     * events per interaction, not a few hundred per second.
     */
    public var maxEvents: Int = 1_000

    /** How many screen visits to keep. Older visits drop off with their events. */
    public var maxVisits: Int = 100

    /**
     * Persist visits so they survive process death.
     *
     * Off by default, following the log plugin: this is a tool you watch while
     * you drive the app, and writing every event to SQLite would dominate the
     * shared store for a history nobody reads.
     */
    public var persist: Boolean = false

    /**
     * Flag events that fire on a screen with a spec but are not in it.
     *
     * Off by default. Most apps fire cross-cutting events — session pings,
     * lifecycle beacons — on every screen, and flagging them would mean
     * declaring them on every screen before the plugin became usable.
     */
    public var flagUnexpectedEvents: Boolean = false

    /**
     * How long after entering a screen its expectations are judged.
     *
     * A missing event is only missing once the screen has had a chance to fire
     * it, so a verdict needs a settle point. A visit is also judged the moment
     * you navigate away, whichever comes first. Set to 0 to judge on navigation
     * only — appropriate for screens that fire their events after a slow call.
     */
    public var settleMillis: Long = 2_000

    internal val spec: AnalyticsSpecBuilder = AnalyticsSpecBuilder()

    internal var listener: ((AnalyticsIssue) -> Unit)? = null

    /** Declares what a screen must emit. See [AnalyticsSpec]. */
    public fun expect(screen: String, configure: ScreenExpectationBuilder.() -> Unit) {
        spec.expect(screen, configure)
    }

    /**
     * Called for every issue.
     *
     * The hook exists so a team can fail a QA run or assert in an instrumented
     * test. Scry does not phone home, and adding a reporting backend here would
     * be the wrong thing to build.
     */
    public fun onIssue(listener: (AnalyticsIssue) -> Unit) {
        this.listener = listener
    }
}

/**
 * Shows which analytics events fired, on which screen, with what data — and
 * whether that matches what you said the screen should emit.
 *
 * ```kotlin
 * plugin(AnalyticsPlugin {
 *     expect("Checkout") {
 *         event("begin_checkout") {
 *             param("cart_value", AnalyticsParamType.NUMBER)
 *             param("currency", AnalyticsParamType.STRING, oneOf = setOf("USD", "EUR"))
 *         }
 *     }
 * })
 *
 * ScryAnalytics.track("begin_checkout", mapOf("cart_value" to 49.90, "currency" to "USD"))
 * ```
 *
 * Events reach Scry through [ScryAnalytics] only — one line inside the wrapper
 * the app already has around its analytics SDK. Scry does not hook Firebase or
 * Segment, and says so rather than appearing to work while capturing nothing.
 *
 * Screens are attributed automatically when the performance plugin is installed,
 * since it already tracks activity, fragment and composable changes and
 * publishes them on the event bus. Without it, call [ScryAnalytics.screenEntered].
 */
public class AnalyticsPlugin(
    configure: AnalyticsPluginBuilder.() -> Unit = {},
) : ScryUiPlugin {

    internal val config: AnalyticsPluginBuilder = AnalyticsPluginBuilder().apply(configure)

    override val id: String = PLUGIN_ID
    override val displayName: String = "Analytics"

    /** What each screen is expected to emit. */
    public val spec: AnalyticsSpec = config.spec.build()

    private var store: ScryStore? = null
    private var bus: ScryEventBus? = null
    private var redactor: Redactor = Redactor.DEFAULT
    private var coroutineScope: CoroutineScope? = null
    private var settleJob: Job? = null

    // Declared before the flows below: the initial visit is built during
    // construction and takes an id from it.
    private var sequence: Long = 0

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _events = MutableStateFlow<List<AnalyticsEvent>>(emptyList())

    /** Captured events, newest first. */
    public val events: StateFlow<List<AnalyticsEvent>> = _events.asStateFlow()

    private val _visits = MutableStateFlow(listOf(newVisit(ROOT_SCREEN)))

    /** Screen visits, newest first. The first entry is the visit in progress. */
    public val visits: StateFlow<List<ScreenVisit>> = _visits.asStateFlow()

    private val _issues = MutableStateFlow<List<AnalyticsIssue>>(emptyList())

    /** Every issue raised this session, newest first. */
    public val issues: StateFlow<List<AnalyticsIssue>> = _issues.asStateFlow()

    private val _currentScreen = MutableStateFlow(ROOT_SCREEN)

    /** The screen events are currently attributed to. */
    public val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    private val _badge = MutableStateFlow<String?>(null)
    override val badge: StateFlow<String?> = _badge.asStateFlow()

    override fun onInstall(scope: ScryScope) {
        bus = scope.events
        redactor = scope.config.redactor
        coroutineScope = scope.coroutineScope
        store = scope.store.takeIf { config.persist }

        if (config.persist) restore(scope.store)

        ScryAnalytics.attach(this)

        scope.coroutineScope.launch {
            scope.events.eventsOfType<ScreenChangedEvent>().collect { screenEntered(it.screen) }
        }

        scope.contextRegistry.register(PLUGIN_ID) { contextSections() }
        scheduleSettle()
        updateBadge()
    }

    override fun onClear() {
        settleJob?.cancel()
        _events.value = emptyList()
        _issues.value = emptyList()
        _visits.value = listOf(newVisit(_currentScreen.value))
        store?.clear(PLUGIN_ID)
        scheduleSettle()
        updateBadge()
    }

    // ---- recording -------------------------------------------------------

    /**
     * Records an event against the current screen. Safe to call from any thread.
     *
     * Parameter values are redacted with the configured [Redactor] on the way in,
     * so a `user_token` parameter never reaches the store or the screen.
     */
    public fun track(
        name: String,
        params: Map<String, Any?> = emptyMap(),
        destination: String? = null,
    ) {
        record(
            AnalyticsEvent(
                id = nextId(),
                name = name,
                params = params.mapValues { (key, value) ->
                    if (redactor.shouldRedactBodyKey(key)) {
                        AnalyticsValue.Text(REDACTED)
                    } else {
                        AnalyticsValue.of(value)
                    }
                },
                screen = _currentScreen.value,
                timestampMillis = nowMillis(),
                destination = destination,
            ),
        )
    }

    /**
     * Records a pre-built event.
     *
     * Prefer [track]. An event whose `screen` is not the current one is kept in
     * the event list but belongs to no visit, and so is never judged.
     */
    public fun record(event: AnalyticsEvent) {
        _events.value = (listOf(event) + _events.value).take(config.maxEvents)

        val issues = spec.check(event, config.flagUnexpectedEvents)
        val current = _visits.value.first()
        if (current.screen == event.screen) {
            replaceCurrentVisit(current.withEvent(event, issues))
        }
        issues.forEach(::raise)

        bus?.publish(AnalyticsTrackedEvent(event))
        persist()
        updateBadge()
    }

    /**
     * Starts a new visit to [screen], judging the one being left.
     *
     * Called automatically for the screen changes the performance plugin
     * publishes, and directly by [ScryAnalytics.screenEntered].
     */
    public fun screenEntered(screen: String) {
        if (screen == _currentScreen.value) return
        settleCurrentVisit()
        _currentScreen.value = screen
        // At least one: the head of this list is the visit in progress, and a
        // maxVisits of 0 would leave the plugin with nowhere to record.
        _visits.value = (listOf(newVisit(screen)) + _visits.value)
            .take(config.maxVisits.coerceAtLeast(1))
        scheduleSettle()
        updateBadge()
    }

    /**
     * Judges the visit in progress now, without waiting out the settle window.
     *
     * Public because the screen offers it as a button: when you are holding the
     * device and know the interaction has finished, waiting on a timer to find
     * out whether the event fired is the wrong interaction.
     */
    public fun settleCurrentVisit() {
        settleJob?.cancel()
        val current = _visits.value.first()
        if (current.isSettled) return

        val settledAt = nowMillis()
        val visitIssues = spec.checkVisit(current, settledAt)
        replaceCurrentVisit(
            current.settle(visitIssues, hasSpec = spec.forScreen(current.screen) != null),
        )
        visitIssues.forEach(::raise)
        persist()
        updateBadge()
    }

    // ---- internals -------------------------------------------------------

    /**
     * Arms the settle timer for the visit in progress.
     *
     * A single cancelled-and-replaced job rather than one timer per visit: only
     * the newest visit is ever open, so an older timer firing could at best
     * re-judge a visit that is already final.
     */
    private fun scheduleSettle() {
        settleJob?.cancel()
        val millis = config.settleMillis
        if (millis <= 0) return
        val scope = coroutineScope ?: return
        settleJob = scope.launch {
            delay(millis)
            settleCurrentVisit()
        }
    }

    private fun restore(store: ScryStore) {
        val restored = store.read(PLUGIN_ID)
            .mapNotNull { runCatching { json.decodeFromString<ScreenVisit>(it.payload) }.getOrNull() }
            .sortedByDescending { it.enteredAtMillis }
            .take(config.maxVisits.coerceAtLeast(1))
        if (restored.isEmpty()) return

        // The live visit stays at the head: a restored visit belongs to a process
        // that is gone and can never be added to again.
        _visits.value = listOf(_visits.value.first()) + restored
        _events.value = restored.flatMap { it.events }
            .sortedByDescending { it.timestampMillis }
            .take(config.maxEvents)
        _issues.value = restored.flatMap { it.issues }.sortedByDescending { it.timestampMillis }
    }

    private fun replaceCurrentVisit(visit: ScreenVisit) {
        _visits.value = listOf(visit) + _visits.value.drop(1)
    }

    private fun raise(issue: AnalyticsIssue) {
        _issues.value = listOf(issue) + _issues.value
        bus?.publish(AnalyticsIssueEvent(issue))
        config.listener?.invoke(issue)
    }

    private fun updateBadge() {
        val issueCount = _issues.value.size
        _badge.value = when {
            issueCount > 0 -> issueCount.toString()
            _events.value.isNotEmpty() -> _events.value.size.toString()
            else -> null
        }
    }

    private fun persist() {
        val store = store ?: return
        val visit = _visits.value.first()
        store.put(
            pluginId = PLUGIN_ID,
            id = visit.id,
            payload = json.encodeToString(visit),
            createdAtMillis = visit.enteredAtMillis,
        )
    }

    private fun newVisit(screen: String): ScreenVisit = ScreenVisit(
        id = nextId(),
        screen = screen,
        enteredAtMillis = nowMillis(),
    )

    private fun nextId(): String = "${nowMillis()}-${sequence++}"

    private fun contextSections(): List<ScryContextSection> = buildList {
        val recent = _events.value.take(CONTEXT_EVENT_COUNT)
        if (recent.isNotEmpty()) {
            add(
                ScryContextSection(
                    title = "Last ${recent.size} analytics events",
                    // Oldest first: a crash report reads top-to-bottom as a
                    // narrative, unlike the UI where newest-first is right.
                    body = recent.asReversed().joinToString("\n") { "${it.screen}: ${it.format()}" },
                ),
            )
        }
        val issues = _issues.value
        if (issues.isNotEmpty()) {
            add(
                ScryContextSection(
                    title = "Analytics issues",
                    body = issues.asReversed().joinToString("\n") { it.format() },
                ),
            )
        }
    }

    @Composable
    override fun Content() {
        AnalyticsScreen(plugin = this)
    }

    public companion object {
        public const val PLUGIN_ID: String = "scry.analytics"

        /**
         * Screen name used before any screen has announced itself.
         *
         * The same string the performance plugin uses, so an app with both
         * installed does not show two names for the same place.
         */
        public const val ROOT_SCREEN: String = "App"

        /** How many events to attach to a crash report. */
        private const val CONTEXT_EVENT_COUNT: Int = 20

        /** The installed plugin, if any. Used by [ScryAnalytics]. */
        @JvmStatic
        public fun installed(): AnalyticsPlugin? = ScryAnalytics.plugin
    }
}

internal expect fun nowMillis(): Long
