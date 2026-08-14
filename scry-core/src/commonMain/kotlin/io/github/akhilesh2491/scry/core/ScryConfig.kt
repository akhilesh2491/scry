package io.github.akhilesh2491.scry.core

import kotlin.jvm.JvmStatic
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * How much captured data Scry keeps before evicting the oldest.
 *
 * All three limits apply simultaneously; whichever is hit first wins. Retention
 * is enforced **on write**, not by a background sweep — unbounded growth is the
 * most common complaint against on-device inspectors in large apps, and a
 * periodic cleaner still lets the store spike between sweeps.
 */
public class Retention private constructor(
    public val maxAge: Duration,
    public val maxEntries: Int,
    public val maxBytes: Long,
) {
    public companion object {
        /** 24 hours, 1000 entries, 50 MB. */
        @JvmStatic
        public val DEFAULT: Retention = Retention(24.hours, 1_000, 50L * 1024 * 1024)

        @JvmStatic
        public fun of(maxAge: Duration, maxEntries: Int, maxBytes: Long): Retention {
            require(maxEntries > 0) { "maxEntries must be positive, was $maxEntries" }
            require(maxBytes > 0) { "maxBytes must be positive, was $maxBytes" }
            require(maxAge.isPositive()) { "maxAge must be positive, was $maxAge" }
            return Retention(maxAge, maxEntries, maxBytes)
        }

        /** Java-friendly overload avoiding [Duration]. */
        @JvmStatic
        public fun ofHours(hours: Long): Retention =
            of(hours.hours, DEFAULT.maxEntries, DEFAULT.maxBytes)
    }

    override fun toString(): String =
        "Retention(maxAge=$maxAge, maxEntries=$maxEntries, maxBytes=$maxBytes)"
}

/** Resolved, immutable configuration for a Scry instance. */
public class ScryConfig internal constructor(
    public val retention: Retention,
    public val plugins: List<ScryPlugin>,
    public val redactor: Redactor,
    public val allowInReleaseBuilds: Boolean,
)

/**
 * Builder behind the `Scry.install(context) { ... }` DSL.
 *
 * Every setting is a plain `var` or a method taking non-Kotlin-specific types so
 * the Java facade ([ScryInstaller]) can drive the same builder without duplication.
 */
public class ScryConfigBuilder internal constructor() {

    /** See [Retention]. Defaults to [Retention.DEFAULT]. */
    public var retention: Retention = Retention.DEFAULT

    /**
     * Scry refuses to install in a non-debuggable build unless this is set.
     *
     * Deliberately verbose and greppable: shipping an on-device debugger that
     * exposes preferences and databases to anyone holding the phone is the single
     * worst failure mode this library has, so opting in should be visible in code
     * review and easy to find with a repo-wide search.
     */
    public var allowInReleaseBuilds: Boolean = false

    private val pluginList = mutableListOf<ScryPlugin>()
    private var redactorBuilder: RedactorBuilder = RedactorBuilder()

    /** Register a plugin. Order determines display order in the UI. */
    public fun plugin(plugin: ScryPlugin) {
        require(pluginList.none { it.id == plugin.id }) {
            "Duplicate plugin id '${plugin.id}' — ids must be unique"
        }
        pluginList += plugin
    }

    /** Customise redaction. Defaults already cover common auth headers and body keys. */
    public fun redaction(configure: RedactorBuilder.() -> Unit) {
        redactorBuilder.configure()
    }

    internal fun build(): ScryConfig = ScryConfig(
        retention = retention,
        plugins = pluginList.toList(),
        redactor = redactorBuilder.build(),
        allowInReleaseBuilds = allowInReleaseBuilds,
    )
}
