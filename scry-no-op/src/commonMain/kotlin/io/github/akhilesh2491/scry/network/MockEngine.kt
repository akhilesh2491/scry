@file:Suppress("UNUSED_PARAMETER", "unused")

package io.github.akhilesh2491.scry.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

public sealed interface MockOutcome {
    public data class Proceed(val delayMillis: Long) : MockOutcome
    public data class Respond(val action: MockAction.Respond, val rule: MockRule) : MockOutcome
    public data class Fail(val message: String, val delayMillis: Long) : MockOutcome
}

public data class MockConfiguration(
    public val offline: Boolean = false,
    public val throttle: ThrottleProfile = ThrottleProfile.NONE,
    public val rules: List<MockRule> = emptyList(),
)

/**
 * Never intercepts anything.
 *
 * The most important stub here by far: a rule left enabled in a shipped build
 * would silently replace real API responses for users.
 */
public class MockEngine internal constructor() {
    public val configuration: StateFlow<MockConfiguration> = MutableStateFlow(MockConfiguration())
    public val isActive: Boolean get() = false

    public fun setOffline(offline: Boolean): Unit = Unit
    public fun setThrottle(profile: ThrottleProfile): Unit = Unit
    public fun addRule(rule: MockRule): Unit = Unit
    public fun removeRule(id: String): Unit = Unit
    public fun setRuleEnabled(id: String, enabled: Boolean): Unit = Unit
    public fun clearRules(): Unit = Unit
    public fun resolve(method: String, url: String): MockOutcome? = null
    public fun exportJson(): String = "{}"
    public fun importJson(text: String): Boolean = false
}
