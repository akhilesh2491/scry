@file:Suppress("UNUSED_PARAMETER", "unused")

package io.github.akhilesh2491.scry.logs

import io.github.akhilesh2491.scry.core.ScryPlugin
import io.github.akhilesh2491.scry.core.ScryScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.jvm.JvmStatic

/** Inert mirrors of `scry-logs`. File name matches for the JVM facade. */

public enum class LogLevel(public val label: String) {
    VERBOSE("V"), DEBUG("D"), INFO("I"), WARN("W"), ERROR("E"), ASSERT("A")
}

public enum class LogSource { SCRY, PLATFORM }

public data class LogEntry(
    public val id: String,
    public val timestampMillis: Long,
    public val level: LogLevel,
    public val tag: String,
    public val message: String,
    public val stackTrace: String? = null,
    public val source: LogSource = LogSource.SCRY,
) {
    public fun format(): String = ""
    public fun matches(query: String): Boolean = false
}

public class LogPluginBuilder internal constructor() {
    public var minimumLevel: LogLevel = LogLevel.VERBOSE
    public var maxEntries: Int = 0
    public var captureSystemLog: Boolean = false
    public var persist: Boolean = false
}

/**
 * Records nothing and spawns no logcat reader.
 *
 * The stub that matters most for release size and battery: the real plugin can
 * hold thousands of entries in memory and run a log-reader process.
 */
public class LogPlugin(
    configure: LogPluginBuilder.() -> Unit = {},
) : ScryPlugin {
    override val id: String = PLUGIN_ID
    override val displayName: String = "Logs"
    override val badge: StateFlow<String?> = MutableStateFlow(null)

    public val entries: StateFlow<List<LogEntry>> = MutableStateFlow(emptyList())

    override fun onInstall(scope: ScryScope): Unit = Unit
    override fun onClear(): Unit = Unit
    public fun record(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        source: LogSource = LogSource.SCRY,
    ): Unit = Unit

    public companion object {
        public const val PLUGIN_ID: String = "scry.logs"

        @JvmStatic
        public fun installed(): LogPlugin? = null
    }
}
