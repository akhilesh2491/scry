package io.github.akhilesh2491.scry.logs

import androidx.compose.runtime.Composable
import io.github.akhilesh2491.scry.core.PlatformContext
import io.github.akhilesh2491.scry.core.ScryContextSection
import io.github.akhilesh2491.scry.core.ScryScope
import io.github.akhilesh2491.scry.core.ScryStore
import io.github.akhilesh2491.scry.ui.ScryUiPlugin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlin.jvm.JvmStatic

/** Builder for [LogPlugin]. */
public class LogPluginBuilder internal constructor() {

    /** Entries below this level are dropped at the source. */
    public var minimumLevel: LogLevel = LogLevel.VERBOSE

    /**
     * How many entries to keep in memory.
     *
     * Separate from Scry's storage retention: logs are far higher-volume than
     * anything else Scry captures, and an unbounded in-memory list is the fast
     * way to make a debug build OOM.
     */
    public var maxEntries: Int = 2_000

    /**
     * Also read the platform log (Android logcat, this process only).
     *
     * Off by default: it spawns a reader process, and most teams already route
     * their logging through a facade Scry can hook more cheaply.
     */
    public var captureSystemLog: Boolean = false

    /**
     * Persist entries so they survive process death.
     *
     * Off by default. Logs are the highest-volume thing Scry captures, and
     * writing every line to SQLite would dominate the store — but a crash you
     * cannot see the lead-up to is exactly what persistence is for, so it is one
     * flag away.
     */
    public var persist: Boolean = false
}

/**
 * Collects log output and shows it in Scry.
 *
 * ```kotlin
 * plugin(LogPlugin { captureSystemLog = true })
 *
 * ScryLog.i("Checkout", "Placing order $id")
 * ScryLog.e("Checkout", "Payment failed", throwable)
 * ```
 *
 * Logs are also attached to crash reports, so a crash arrives with the lines
 * that led up to it.
 */
public class LogPlugin(
    configure: LogPluginBuilder.() -> Unit = {},
) : ScryUiPlugin {

    internal val config: LogPluginBuilder = LogPluginBuilder().apply(configure)

    override val id: String = PLUGIN_ID
    override val displayName: String = "Logs"

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())

    /** Captured entries, newest first. */
    public val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val _badge = MutableStateFlow<String?>(null)
    override val badge: StateFlow<String?> = _badge.asStateFlow()

    private var store: ScryStore? = null
    private var systemCapture: AutoCloseable? = null
    private var sequence: Long = 0

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun onInstall(scope: ScryScope) {
        store = scope.store.takeIf { config.persist }

        if (config.persist) {
            _entries.value = scope.store.read(PLUGIN_ID).mapNotNull { record ->
                runCatching { json.decodeFromString<LogEntry>(record.payload) }.getOrNull()
            }
        }

        ScryLog.attach(this)

        if (config.captureSystemLog) {
            systemCapture = startSystemLogCapture(scope.context, this)
        }

        scope.contextRegistry.register(PLUGIN_ID) {
            val recent = _entries.value.take(CONTEXT_LINE_COUNT)
            if (recent.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    ScryContextSection(
                        title = "Last ${recent.size} log lines",
                        // Oldest first here: a crash report reads top-to-bottom as
                        // a narrative, unlike the UI where newest-first is right.
                        body = recent.asReversed().joinToString("\n") { it.format() },
                    ),
                )
            }
        }
        updateBadge()
    }

    override fun onClear() {
        _entries.value = emptyList()
        store?.clear(PLUGIN_ID)
        updateBadge()
    }

    /** Records an entry. Safe to call from any thread. */
    public fun record(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        source: LogSource = LogSource.SCRY,
    ) {
        if (level < config.minimumLevel) return

        val entry = LogEntry(
            id = "${nowMillis()}-${sequence++}",
            timestampMillis = nowMillis(),
            level = level,
            tag = tag,
            message = message,
            stackTrace = throwable?.stackTraceToString(),
            source = source,
        )

        // Trim on write. Logs arrive far faster than anything else Scry captures,
        // so an unbounded list would grow without limit between UI visits.
        _entries.value = (listOf(entry) + _entries.value).take(config.maxEntries)

        store?.put(
            pluginId = PLUGIN_ID,
            id = entry.id,
            payload = json.encodeToString(entry),
            createdAtMillis = entry.timestampMillis,
        )
        updateBadge()
    }

    private fun updateBadge() {
        val errors = _entries.value.count { it.level >= LogLevel.ERROR }
        _badge.value = when {
            errors > 0 -> errors.toString()
            _entries.value.isNotEmpty() -> _entries.value.size.toString()
            else -> null
        }
    }

    @Composable
    override fun Content() {
        LogScreen(plugin = this)
    }

    public companion object {
        public const val PLUGIN_ID: String = "scry.logs"

        /** How many lines to attach to a crash report. */
        private const val CONTEXT_LINE_COUNT: Int = 100

        /** The installed plugin, if any. Used by [ScryLog]. */
        @JvmStatic
        public fun installed(): LogPlugin? = ScryLog.plugin
    }
}

/** Starts reading the platform log. Returns a handle that stops it. */
internal expect fun startSystemLogCapture(
    context: PlatformContext,
    plugin: LogPlugin,
): AutoCloseable?

internal expect fun nowMillis(): Long
