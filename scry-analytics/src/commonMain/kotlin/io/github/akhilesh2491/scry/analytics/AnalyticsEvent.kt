package io.github.akhilesh2491.scry.analytics

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One analytics parameter value.
 *
 * Typed rather than stringified because the whole point of this plugin is
 * catching the difference between `cart_value = 49.9` and `cart_value = "49.9"`
 * — a mismatch every analytics backend accepts silently and then sums to zero.
 */
@Serializable
public sealed class AnalyticsValue {

    @Serializable
    @SerialName("text")
    public data class Text(public val value: String) : AnalyticsValue()

    @Serializable
    @SerialName("number")
    public data class Number(public val value: Double) : AnalyticsValue()

    @Serializable
    @SerialName("bool")
    public data class Bool(public val value: Boolean) : AnalyticsValue()

    /** An explicitly supplied null. Distinct from the parameter being absent. */
    @Serializable
    @SerialName("null")
    public data object Null : AnalyticsValue()

    /** How the value reads in the UI and in exports. */
    public fun display(): String = when (this) {
        is Text -> value
        is Number -> if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
        is Bool -> value.toString()
        Null -> "null"
    }

    /** The type name used in issue messages. Matches [AnalyticsParamType]. */
    public fun typeName(): String = when (this) {
        is Text -> "STRING"
        is Number -> "NUMBER"
        is Bool -> "BOOLEAN"
        Null -> "NULL"
    }

    public companion object {
        /**
         * Boxes whatever the app passed.
         *
         * Anything that is not a string, number or boolean is rendered with
         * `toString()` rather than rejected: a debugging tool that throws on an
         * unexpected parameter type is worse than the bug it was installed to
         * find.
         */
        public fun of(value: Any?): AnalyticsValue = when (value) {
            null -> Null
            is String -> Text(value)
            is Boolean -> Bool(value)
            is kotlin.Number -> Number(value.toDouble())
            else -> Text(value.toString())
        }
    }
}

/**
 * One analytics event, as the app reported it.
 *
 * [screen] is stamped at capture time rather than derived later, because "which
 * screen fired this" stops being answerable the moment the user navigates.
 */
@Serializable
public data class AnalyticsEvent(
    public val id: String,
    public val name: String,
    public val params: Map<String, AnalyticsValue> = emptyMap(),
    public val screen: String,
    public val timestampMillis: Long,
    /**
     * Which sink the app routed this to — `"firebase"`, `"segment"`. Free-form
     * and optional; apps that fan one call out to several SDKs use it to tell
     * the copies apart.
     */
    public val destination: String? = null,
) {
    /** `name(key=value, key=value)` — one line, for exports and crash reports. */
    public fun format(): String =
        if (params.isEmpty()) {
            name
        } else {
            name + params.entries.joinToString(", ", "(", ")") { "${it.key}=${it.value.display()}" }
        }

    /**
     * True if this event matches a free-text query.
     *
     * Searches parameter names and values as well as the event name: the reason
     * to search an analytics trail is usually "where did this user id appear",
     * not "find me the event I can already see".
     */
    public fun matches(query: String): Boolean =
        query.isBlank() ||
            name.contains(query, ignoreCase = true) ||
            screen.contains(query, ignoreCase = true) ||
            params.any { (key, value) ->
                key.contains(query, ignoreCase = true) ||
                    value.display().contains(query, ignoreCase = true)
            }
}
