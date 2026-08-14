package io.github.akhilesh2491.scry.core

/** A titled block of diagnostic text contributed by a plugin. */
public class ScryContextSection(
    public val title: String,
    public val body: String,
)

/**
 * Supplies diagnostic context for a crash report or bug report.
 *
 * Implemented by plugins that hold information worth attaching to a failure —
 * recent network calls, recent logs, current feature flags.
 */
public fun interface ScryContextContributor {
    public fun contribute(): List<ScryContextSection>
}

/**
 * Where plugins register diagnostic context, and where the crash plugin collects it.
 *
 * This indirection is what lets the crash plugin attach the last N network calls
 * to a report **without depending on `scry-network`**. Neither module knows the
 * other exists; they meet here. That is the difference between a plugin system
 * and a set of features that happen to share a UI.
 */
public class ScryContextRegistry internal constructor() {

    private val lock = ScryLock()
    private val contributors = mutableListOf<Pair<String, ScryContextContributor>>()

    /** Registers [contributor] under [sourceId], replacing any previous entry for it. */
    public fun register(sourceId: String, contributor: ScryContextContributor) {
        lock.withLock {
            contributors.removeAll { it.first == sourceId }
            contributors += sourceId to contributor
        }
    }

    public fun unregister(sourceId: String) {
        lock.withLock { contributors.removeAll { it.first == sourceId } }
    }

    /**
     * Collects every contributor's sections.
     *
     * A throwing contributor is reported as a section rather than propagated:
     * this runs while the process is already crashing, and losing the whole
     * report because one contributor failed would defeat the purpose.
     */
    public fun collect(): List<ScryContextSection> {
        val snapshot = lock.withLock { contributors.toList() }
        return snapshot.flatMap { (sourceId, contributor) ->
            runCatching { contributor.contribute() }.getOrElse { failure ->
                listOf(
                    ScryContextSection(
                        title = "$sourceId (failed)",
                        body = failure.message ?: failure::class.simpleName ?: "unknown error",
                    ),
                )
            }
        }
    }
}
