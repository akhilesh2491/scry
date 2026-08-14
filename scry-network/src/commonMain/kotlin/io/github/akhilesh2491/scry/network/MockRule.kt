package io.github.akhilesh2491.scry.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What to do when a rule matches. */
@Serializable
public sealed interface MockAction {

    /** Milliseconds to wait before the action takes effect. */
    public val delayMillis: Long

    /** Return a canned response without touching the network. */
    @Serializable
    @SerialName("respond")
    public data class Respond(
        public val statusCode: Int = 200,
        public val body: String = "",
        public val contentType: String = "application/json",
        public val headers: List<HttpHeader> = emptyList(),
        override val delayMillis: Long = 0,
    ) : MockAction

    /** Fail the request as though the transport broke. */
    @Serializable
    @SerialName("fail")
    public data class Fail(
        public val message: String = "Failure injected by Scry",
        override val delayMillis: Long = 0,
    ) : MockAction

    /**
     * Let the request through, but slowly.
     *
     * Separate from [Respond] because reproducing a timeout usually means
     * exercising the *real* endpoint under bad conditions, not replacing it.
     */
    @Serializable
    @SerialName("delay")
    public data class Delay(
        override val delayMillis: Long,
    ) : MockAction
}

/**
 * A rule that intercepts matching requests.
 *
 * Serializable on purpose: the point is that a QA engineer configures a scenario
 * on a device, exports it, and commits the JSON so everyone reproduces the same
 * failure. A mocking feature you cannot share is a toy.
 */
@Serializable
public data class MockRule(
    public val id: String,
    /** Human label shown in the UI. Falls back to the pattern when blank. */
    public val name: String = "",
    /**
     * Glob against the full URL. `*` matches any run of characters, `?` any one.
     *
     * A glob rather than a regex because these are written on a phone keyboard
     * by someone debugging, and a pattern like `https://api.example.com/orders`
     * with a leading and trailing star is what they will reach for first.
     */
    public val urlPattern: String,
    /** HTTP method to match, or null for any. */
    public val method: String? = null,
    public val enabled: Boolean = true,
    public val action: MockAction,
) {
    public val displayName: String get() = name.ifBlank { urlPattern }

    /** True if this rule should intercept the given request. */
    public fun matches(method: String, url: String): Boolean {
        if (!enabled) return false
        if (this.method != null && !this.method.equals(method, ignoreCase = true)) return false
        return globToRegex(urlPattern).matches(url)
    }
}

/** Simulated network conditions applied to every request. */
@Serializable
public enum class ThrottleProfile(
    public val label: String,
    /** Latency added to each request. */
    public val delayMillis: Long,
) {
    NONE("Off", 0),
    WIFI("Wi-Fi", 30),
    FAST_4G("4G", 120),
    SLOW_3G("3G", 400),
    EDGE("EDGE", 1_200),
}

/**
 * Converts a glob to a regex.
 *
 * Everything except `*` and `?` is escaped, so a URL containing `.`, `+` or `(`
 * — which is to say every real URL — cannot accidentally behave as a regex
 * metacharacter.
 */
internal fun globToRegex(glob: String): Regex {
    val pattern = buildString {
        append('^')
        glob.forEach { char ->
            when (char) {
                '*' -> append(".*")
                '?' -> append('.')
                else -> if (char.isLetterOrDigit()) append(char) else append("\\").append(char)
            }
        }
        append('$')
    }
    return Regex(pattern, RegexOption.IGNORE_CASE)
}
