package io.github.akhilesh2491.scry.perf

/**
 * Decides what kind of startup just happened, and how long it took.
 *
 * Platform-free on purpose. Classifying a start is a state machine over activity
 * lifecycle transitions and nothing else, and every interesting case — a process
 * restored from saved state, a return to the foreground, a launch where the first
 * frame never arrives — is a sequence of calls, not a device condition. Keeping
 * it here means those cases are unit tests rather than things you hope work.
 *
 * All times are on the [monotonicMillis] scale and supplied by the caller.
 */
internal class StartupTracker(
    /** True process start, or null when the platform cannot report it. */
    private val processStartMillis: Long?,
    /** When Scry was installed — inside `Application.onCreate`. */
    private val installMillis: Long,
) {

    private var launchReported = false
    private var startedActivities = 0

    /** Set when a start is in flight and cleared when its first frame lands. */
    private var pendingKind: StartupKind? = null
    private var pendingT0: Long = 0
    private var pendingActivityCreatedAt: Long? = null

    /** Whether an activity was created since the app was last fully backgrounded. */
    private var createdSinceBackground = false

    fun onActivityCreated(hasSavedState: Boolean, nowMillis: Long) {
        createdSinceBackground = true
        if (pendingKind != null) return

        // A create while something is already on screen is navigation within the
        // app, or a rotation — not the app starting. Once the launch has been
        // reported, only a create arriving with nothing in the foreground counts,
        // which is exactly the warm start case: process alive, activity gone.
        if (launchReported && startedActivities > 0) return

        pendingActivityCreatedAt = nowMillis
        // Saved state means the process outlived the activity: the expensive
        // parts of a cold start — dex loading, Application.onCreate — did not
        // happen this time, so calling it cold would understate what cold costs.
        pendingKind = if (hasSavedState) StartupKind.WARM else StartupKind.COLD
        pendingT0 = if (hasSavedState) nowMillis else processStartMillis ?: installMillis
    }

    /**
     * Returns true when this transition began a hot start.
     *
     * The caller needs to know, because a hot start creates no activity and so
     * has nothing to hang a first-draw listener on. Without that signal the
     * measurement stays open until some unrelated activity is created minutes
     * later, and reports the gap as the hot start time.
     */
    fun onActivityStarted(nowMillis: Long): Boolean {
        startedActivities++
        if (startedActivities != 1) return false

        // Coming back to the foreground with nothing created in between: both the
        // process and the activity survived, so this is a hot start.
        if (launchReported && !createdSinceBackground && pendingKind == null) {
            pendingKind = StartupKind.HOT
            pendingT0 = nowMillis
            pendingActivityCreatedAt = null
            return true
        }
        return false
    }

    fun onActivityStopped() {
        startedActivities--
        if (startedActivities <= 0) {
            startedActivities = 0
            createdSinceBackground = false
        }
    }

    /**
     * Closes the measurement. Returns null when no start is in flight.
     *
     * Called on the first draw after an activity appears, which is the closest
     * observable point to "the user can see the app".
     */
    fun onFirstFrame(nowMillis: Long): StartupMetric? {
        val kind = pendingKind ?: return null
        pendingKind = null

        val total = (nowMillis - pendingT0).coerceAtLeast(0)
        val metric = StartupMetric(
            kind = kind,
            totalMillis = total,
            timestampMillis = nowMillis(),
            phases = phases(nowMillis, kind),
            // Without a real process start the cold measurement begins at Scry's
            // own install, which is already inside Application.onCreate — so it
            // misses process creation and everything before Scry was installed.
            truncated = kind == StartupKind.COLD && processStartMillis == null,
        )

        if (kind != StartupKind.HOT) launchReported = true
        createdSinceBackground = false
        return metric
    }

    private fun phases(firstFrameMillis: Long, kind: StartupKind): List<PerfPhase> {
        if (kind != StartupKind.COLD) return emptyList()
        val activityCreatedAt = pendingActivityCreatedAt ?: return emptyList()

        return buildList {
            if (processStartMillis != null) {
                add(
                    PerfPhase(
                        name = "Process start → Scry install",
                        startOffsetMillis = 0,
                        durationMillis = (installMillis - processStartMillis).coerceAtLeast(0),
                    ),
                )
            }
            add(
                PerfPhase(
                    name = "Scry install → first Activity.onCreate",
                    startOffsetMillis = (installMillis - pendingT0).coerceAtLeast(0),
                    durationMillis = (activityCreatedAt - installMillis).coerceAtLeast(0),
                ),
            )
            add(
                PerfPhase(
                    name = "Activity.onCreate → first frame",
                    startOffsetMillis = (activityCreatedAt - pendingT0).coerceAtLeast(0),
                    durationMillis = (firstFrameMillis - activityCreatedAt).coerceAtLeast(0),
                ),
            )
        }
    }
}
