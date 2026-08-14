package io.github.akhilesh2491.scry.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What an adapter should do with a request. */
public sealed interface MockOutcome {

    /** Wait, then let the request proceed normally. */
    public data class Proceed(val delayMillis: Long) : MockOutcome

    /** Wait, then return this canned response instead of calling the network. */
    public data class Respond(val action: MockAction.Respond, val rule: MockRule) : MockOutcome

    /** Wait, then throw. */
    public data class Fail(val message: String, val delayMillis: Long) : MockOutcome
}

/** Serialisable snapshot of the whole mock configuration. */
@Serializable
public data class MockConfiguration(
    public val offline: Boolean = false,
    public val throttle: ThrottleProfile = ThrottleProfile.NONE,
    public val rules: List<MockRule> = emptyList(),
)

/**
 * Decides what happens to each request.
 *
 * Owned by [NetworkPlugin] and consulted by every capture adapter, so a rule
 * written once applies whether the app uses Ktor or OkHttp.
 */
public class MockEngine internal constructor() {

    private val _configuration = MutableStateFlow(MockConfiguration())
    public val configuration: StateFlow<MockConfiguration> = _configuration.asStateFlow()

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    /** True when any rule, offline mode or throttling is active. */
    public val isActive: Boolean
        get() = _configuration.value.let { config ->
            config.offline ||
                config.throttle != ThrottleProfile.NONE ||
                config.rules.any { it.enabled }
        }

    public fun setOffline(offline: Boolean) {
        _configuration.value = _configuration.value.copy(offline = offline)
    }

    public fun setThrottle(profile: ThrottleProfile) {
        _configuration.value = _configuration.value.copy(throttle = profile)
    }

    public fun addRule(rule: MockRule) {
        _configuration.value = _configuration.value.copy(
            rules = _configuration.value.rules.filterNot { it.id == rule.id } + rule,
        )
    }

    public fun removeRule(id: String) {
        _configuration.value = _configuration.value.copy(
            rules = _configuration.value.rules.filterNot { it.id == id },
        )
    }

    public fun setRuleEnabled(id: String, enabled: Boolean) {
        _configuration.value = _configuration.value.copy(
            rules = _configuration.value.rules.map {
                if (it.id == id) it.copy(enabled = enabled) else it
            },
        )
    }

    public fun clearRules() {
        _configuration.value = _configuration.value.copy(rules = emptyList())
    }

    /**
     * Decides the fate of one request.
     *
     * Returns null when nothing applies, so the common path — no mocking
     * configured — costs one flag check and allocates nothing.
     *
     * Order matters: offline wins over everything (it is the coarse "what does
     * my app do with no network" switch), then the first matching rule, then
     * throttling alone.
     */
    public fun resolve(method: String, url: String): MockOutcome? {
        val config = _configuration.value

        if (config.offline) {
            return MockOutcome.Fail("Offline mode enabled in Scry", config.throttle.delayMillis)
        }

        val rule = config.rules.firstOrNull { it.matches(method, url) }
        if (rule != null) {
            val delay = rule.action.delayMillis + config.throttle.delayMillis
            return when (val action = rule.action) {
                is MockAction.Respond -> MockOutcome.Respond(action.copy(delayMillis = delay), rule)
                is MockAction.Fail -> MockOutcome.Fail(action.message, delay)
                is MockAction.Delay -> MockOutcome.Proceed(delay)
            }
        }

        return config.throttle.takeIf { it != ThrottleProfile.NONE }
            ?.let { MockOutcome.Proceed(it.delayMillis) }
    }

    /** The configuration as JSON, for committing into a repo. */
    public fun exportJson(): String =
        json.encodeToString(MockConfiguration.serializer(), _configuration.value)

    /**
     * Replaces the configuration from JSON.
     *
     * Returns false on malformed input rather than throwing — this is fed by a
     * text field on a phone, and a typo must not take the app down.
     */
    public fun importJson(text: String): Boolean = runCatching {
        _configuration.value = json.decodeFromString(MockConfiguration.serializer(), text)
        true
    }.getOrDefault(false)
}
