@file:Suppress("UNUSED_PARAMETER", "unused")

package io.github.akhilesh2491.scry.analytics

/** Inert mirrors of `scry-analytics`' model and spec. */

public sealed class AnalyticsValue {

    public data class Text(public val value: String) : AnalyticsValue()

    public data class Number(public val value: Double) : AnalyticsValue()

    public data class Bool(public val value: Boolean) : AnalyticsValue()

    public data object Null : AnalyticsValue()

    public fun display(): String = ""

    public fun typeName(): String = ""

    public companion object {
        public fun of(value: Any?): AnalyticsValue = Null
    }
}

public data class AnalyticsEvent(
    public val id: String,
    public val name: String,
    public val params: Map<String, AnalyticsValue> = emptyMap(),
    public val screen: String,
    public val timestampMillis: Long,
    public val destination: String? = null,
) {
    public fun format(): String = ""
    public fun matches(query: String): Boolean = false
}

public enum class AnalyticsIssueKind {
    MISSING_EVENT,
    MISSING_PARAM,
    WRONG_TYPE,
    DISALLOWED_VALUE,
    DUPLICATE_EVENT,
    FORBIDDEN_EVENT,
    UNEXPECTED_EVENT,
}

public data class AnalyticsIssue(
    public val kind: AnalyticsIssueKind,
    public val screen: String,
    public val eventName: String,
    public val paramName: String? = null,
    public val expected: String? = null,
    public val actual: String? = null,
    public val timestampMillis: Long,
) {
    public fun format(): String = ""
}

public enum class AnalyticsVerdict { PENDING, PASS, FAIL, NO_SPEC }

public data class ScreenVisit(
    public val id: String,
    public val screen: String,
    public val enteredAtMillis: Long,
    public val events: List<AnalyticsEvent> = emptyList(),
    public val issues: List<AnalyticsIssue> = emptyList(),
    public val verdict: AnalyticsVerdict = AnalyticsVerdict.PENDING,
) {
    public val isSettled: Boolean get() = false
}

public enum class AnalyticsParamType { STRING, NUMBER, BOOLEAN, ANY }

public class ParamExpectation internal constructor(
    public val name: String,
    public val type: AnalyticsParamType,
    public val required: Boolean,
    public val oneOf: Set<String>?,
)

public class EventExpectation internal constructor(
    public val name: String,
    public val params: List<ParamExpectation>,
    public val required: Boolean,
    public val atMostOnce: Boolean,
)

public class ScreenExpectation internal constructor(
    public val screen: String,
    public val events: List<EventExpectation>,
    public val forbidden: Set<String>,
)

/** Judges nothing, because nothing is captured to judge. */
public class AnalyticsSpec internal constructor() {
    public val screens: Set<String> get() = emptySet()
    public val isEmpty: Boolean get() = true
    public fun forScreen(screen: String): ScreenExpectation? = null
    public fun check(event: AnalyticsEvent, flagUnexpected: Boolean = false): List<AnalyticsIssue> =
        emptyList()
    public fun checkVisit(visit: ScreenVisit, settledAtMillis: Long): List<AnalyticsIssue> =
        emptyList()

    public companion object {
        public val NONE: AnalyticsSpec = AnalyticsSpec()
    }
}

public class EventExpectationBuilder internal constructor() {
    public var required: Boolean = true
    public var atMostOnce: Boolean = false

    public fun param(
        name: String,
        type: AnalyticsParamType = AnalyticsParamType.ANY,
        required: Boolean = true,
        oneOf: Set<String>? = null,
    ): Unit = Unit
}

public class ScreenExpectationBuilder internal constructor() {
    public fun event(name: String, configure: EventExpectationBuilder.() -> Unit = {}): Unit = Unit
    public fun forbid(vararg names: String): Unit = Unit
}

public class AnalyticsSpecBuilder internal constructor() {
    public fun expect(screen: String, configure: ScreenExpectationBuilder.() -> Unit): Unit = Unit
}
