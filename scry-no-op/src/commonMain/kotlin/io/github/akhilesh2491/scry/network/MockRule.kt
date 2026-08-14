@file:Suppress("UNUSED_PARAMETER", "unused")

package io.github.akhilesh2491.scry.network

/** Inert mirrors of `scry-network`'s mocking model. File names match the real module. */

public sealed interface MockAction {
    public val delayMillis: Long

    public data class Respond(
        public val statusCode: Int = 200,
        public val body: String = "",
        public val contentType: String = "application/json",
        public val headers: List<HttpHeader> = emptyList(),
        override val delayMillis: Long = 0,
    ) : MockAction

    public data class Fail(
        public val message: String = "Failure injected by Scry",
        override val delayMillis: Long = 0,
    ) : MockAction

    public data class Delay(override val delayMillis: Long) : MockAction
}

public data class MockRule(
    public val id: String,
    public val name: String = "",
    public val urlPattern: String,
    public val method: String? = null,
    public val enabled: Boolean = true,
    public val action: MockAction,
) {
    public val displayName: String get() = ""
    public fun matches(method: String, url: String): Boolean = false
}

public enum class ThrottleProfile(public val label: String, public val delayMillis: Long) {
    NONE("Off", 0),
    WIFI("Wi-Fi", 0),
    FAST_4G("4G", 0),
    SLOW_3G("3G", 0),
    EDGE("EDGE", 0),
}
