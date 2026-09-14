package io.github.akhilesh2491.scry.analytics

/** The type a parameter is expected to carry. [ANY] checks presence only. */
public enum class AnalyticsParamType { STRING, NUMBER, BOOLEAN, ANY }

/** One parameter an event is expected to carry. */
public class ParamExpectation internal constructor(
    public val name: String,
    public val type: AnalyticsParamType,
    public val required: Boolean,
    /** Allowed values, compared against [AnalyticsValue.display]. Null means any. */
    public val oneOf: Set<String>?,
)

/** One event a screen is expected to fire. */
public class EventExpectation internal constructor(
    public val name: String,
    public val params: List<ParamExpectation>,
    public val required: Boolean,
    public val atMostOnce: Boolean,
)

/** What one screen is expected to emit. */
public class ScreenExpectation internal constructor(
    public val screen: String,
    public val events: List<EventExpectation>,
    /** Events that must *not* fire here — a debug ping, a leftover from a copy-paste. */
    public val forbidden: Set<String>,
)

/**
 * What each screen is supposed to emit, declared once at install time.
 *
 * The analytics counterpart of `PerfBudget`: it is what turns a stream of events
 * you have to read into a pass or a fail you can act on. A screen with no
 * expectation is never judged — see [AnalyticsVerdict.NO_SPEC].
 *
 * ```kotlin
 * plugin(AnalyticsPlugin {
 *     expect("Checkout") {
 *         event("begin_checkout") {
 *             param("cart_value", AnalyticsParamType.NUMBER)
 *             param("currency", AnalyticsParamType.STRING, oneOf = setOf("USD", "EUR"))
 *             param("coupon", required = false)
 *         }
 *         forbid("debug_ping")
 *     }
 * })
 * ```
 */
public class AnalyticsSpec internal constructor(
    private val byScreen: Map<String, ScreenExpectation>,
) {
    /** Screens with a declared expectation. */
    public val screens: Set<String> get() = byScreen.keys

    /** True when nothing at all has been declared. */
    public val isEmpty: Boolean get() = byScreen.isEmpty()

    public fun forScreen(screen: String): ScreenExpectation? = byScreen[screen]

    /**
     * Checks one event as it arrives.
     *
     * Only the checks that can be answered immediately — everything about the
     * event in hand. "Did it fire at all" is [checkVisit]'s job, because at this
     * point the answer is always yes.
     */
    public fun check(event: AnalyticsEvent, flagUnexpected: Boolean = false): List<AnalyticsIssue> {
        val expectation = byScreen[event.screen] ?: return emptyList()

        fun issue(
            kind: AnalyticsIssueKind,
            param: String? = null,
            expected: String? = null,
            actual: String? = null,
        ) = AnalyticsIssue(
            kind = kind,
            screen = event.screen,
            eventName = event.name,
            paramName = param,
            expected = expected,
            actual = actual,
            timestampMillis = event.timestampMillis,
        )

        if (event.name in expectation.forbidden) {
            return listOf(issue(AnalyticsIssueKind.FORBIDDEN_EVENT))
        }

        val declared = expectation.events.firstOrNull { it.name == event.name }
            ?: return if (flagUnexpected) listOf(issue(AnalyticsIssueKind.UNEXPECTED_EVENT)) else emptyList()

        val issues = mutableListOf<AnalyticsIssue>()
        for (param in declared.params) {
            val value = event.params[param.name]

            // An explicit null satisfies nothing: for a required parameter it is
            // the same failure as omitting it, and reporting it as a type error
            // would send someone looking at the wrong line.
            if (value == null || value is AnalyticsValue.Null) {
                if (param.required) issues += issue(AnalyticsIssueKind.MISSING_PARAM, param.name)
                continue
            }

            val typeOk = when (param.type) {
                AnalyticsParamType.ANY -> true
                AnalyticsParamType.STRING -> value is AnalyticsValue.Text
                AnalyticsParamType.NUMBER -> value is AnalyticsValue.Number
                AnalyticsParamType.BOOLEAN -> value is AnalyticsValue.Bool
            }
            if (!typeOk) {
                issues += issue(
                    AnalyticsIssueKind.WRONG_TYPE,
                    param = param.name,
                    expected = param.type.name,
                    actual = "${value.typeName()} (${value.display()})",
                )
                continue
            }

            val allowed = param.oneOf
            if (allowed != null && value.display() !in allowed) {
                issues += issue(
                    AnalyticsIssueKind.DISALLOWED_VALUE,
                    param = param.name,
                    expected = allowed.joinToString(", ", "[", "]"),
                    actual = value.display(),
                )
            }
        }
        return issues
    }

    /**
     * Checks what the visit as a whole did or did not contain.
     *
     * Called when the screen settles, never while it is still open: an event that
     * has not fired yet is not a missing event.
     */
    public fun checkVisit(visit: ScreenVisit, settledAtMillis: Long): List<AnalyticsIssue> {
        val expectation = byScreen[visit.screen] ?: return emptyList()
        val issues = mutableListOf<AnalyticsIssue>()

        for (declared in expectation.events) {
            val count = visit.events.count { it.name == declared.name }
            if (count == 0) {
                if (declared.required) {
                    issues += AnalyticsIssue(
                        kind = AnalyticsIssueKind.MISSING_EVENT,
                        screen = visit.screen,
                        eventName = declared.name,
                        timestampMillis = settledAtMillis,
                    )
                }
                continue
            }
            if (declared.atMostOnce && count > 1) {
                issues += AnalyticsIssue(
                    kind = AnalyticsIssueKind.DUPLICATE_EVENT,
                    screen = visit.screen,
                    eventName = declared.name,
                    actual = count.toString(),
                    timestampMillis = settledAtMillis,
                )
            }
        }
        return issues
    }

    public companion object {
        /** Records everything, judges nothing. */
        public val NONE: AnalyticsSpec = AnalyticsSpec(emptyMap())
    }
}

// ---- DSL -----------------------------------------------------------------

/** Declares what one event must look like. See [AnalyticsSpec]. */
public class EventExpectationBuilder internal constructor(private val name: String) {

    /** False for an event that may fire here but does not have to. */
    public var required: Boolean = true

    /** True to flag a second occurrence on the same visit. */
    public var atMostOnce: Boolean = false

    private val params = mutableListOf<ParamExpectation>()

    /** Declares a parameter. Declaring none checks the event name only. */
    public fun param(
        name: String,
        type: AnalyticsParamType = AnalyticsParamType.ANY,
        required: Boolean = true,
        oneOf: Set<String>? = null,
    ) {
        params += ParamExpectation(name, type, required, oneOf)
    }

    internal fun build(): EventExpectation =
        EventExpectation(name, params.toList(), required, atMostOnce)
}

/** Declares what one screen must emit. See [AnalyticsSpec]. */
public class ScreenExpectationBuilder internal constructor(private val screen: String) {

    private val events = mutableListOf<EventExpectation>()
    private val forbidden = mutableSetOf<String>()

    public fun event(name: String, configure: EventExpectationBuilder.() -> Unit = {}) {
        events += EventExpectationBuilder(name).apply(configure).build()
    }

    /** Events that must not fire on this screen. */
    public fun forbid(vararg names: String) {
        forbidden += names
    }

    internal fun build(): ScreenExpectation =
        ScreenExpectation(screen, events.toList(), forbidden.toSet())
}

/** Collects the per-screen expectations. See [AnalyticsSpec]. */
public class AnalyticsSpecBuilder internal constructor() {

    private val screens = mutableMapOf<String, ScreenExpectation>()

    /**
     * Declares what [screen] must emit.
     *
     * The name has to match what the app announces — `ScryAnalytics.screenEntered`,
     * or the activity/fragment/composable name the performance plugin reports.
     */
    public fun expect(screen: String, configure: ScreenExpectationBuilder.() -> Unit) {
        screens[screen] = ScreenExpectationBuilder(screen).apply(configure).build()
    }

    internal fun build(): AnalyticsSpec = AnalyticsSpec(screens.toMap())
}
