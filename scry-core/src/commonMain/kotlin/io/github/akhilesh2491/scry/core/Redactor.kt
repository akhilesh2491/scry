package io.github.akhilesh2491.scry.core

/** Replacement text substituted for any redacted value. */
public const val REDACTED: String = "██ redacted by Scry ██"

/**
 * Masks sensitive values before they are persisted or displayed.
 *
 * Redaction is **default-deny for known-sensitive names**: the common auth
 * headers and credential-ish body keys are masked unless explicitly allowed.
 * Scry writes captured traffic to disk and offers a share button, so an
 * un-redacted `Authorization` header is one tap away from a bug-report
 * attachment in a group chat.
 *
 * Matching is case-insensitive. Body-key matching is substring-based, so
 * `"token"` also catches `refresh_token` and `tokenExpiry`.
 */
public class Redactor internal constructor(
    private val headerNames: Set<String>,
    private val bodyKeys: Set<String>,
) {
    private val lowerHeaders: Set<String> = headerNames.mapTo(mutableSetOf()) { it.lowercase() }
    private val lowerBodyKeys: List<String> = bodyKeys.map { it.lowercase() }

    /** True if a header with this name should have its value masked. */
    public fun shouldRedactHeader(name: String): Boolean = name.lowercase() in lowerHeaders

    /** Returns [value] or [REDACTED], depending on [name]. */
    public fun redactHeaderValue(name: String, value: String): String =
        if (shouldRedactHeader(name)) REDACTED else value

    /**
     * Masks values of sensitive keys in a JSON document.
     *
     * Intentionally a targeted string rewrite rather than a parse-and-reserialise:
     * captured bodies are frequently malformed, truncated, or not really JSON, and
     * a redactor that throws on bad input is a redactor that leaks. Handles the
     * `"key": "value"` form, which is what credentials in practice look like.
     */
    public fun redactJsonBody(body: String): String {
        if (lowerBodyKeys.isEmpty() || body.isEmpty()) return body
        var result = body
        for (key in lowerBodyKeys) {
            result = redactJsonKey(result, key)
        }
        return result
    }

    private fun redactJsonKey(source: String, key: String): String {
        val builder = StringBuilder(source.length)
        var index = 0
        while (index < source.length) {
            val quote = source.indexOf('"', index)
            if (quote < 0) {
                builder.append(source, index, source.length)
                break
            }
            val closingQuote = source.indexOf('"', quote + 1)
            if (closingQuote < 0) {
                builder.append(source, index, source.length)
                break
            }
            val name = source.substring(quote + 1, closingQuote)
            builder.append(source, index, closingQuote + 1)
            index = closingQuote + 1

            if (!name.lowercase().contains(key)) continue

            // Expect `: "value"` — anything else (numbers, objects, arrays) is left
            // alone rather than guessed at.
            var cursor = index
            while (cursor < source.length && source[cursor].isWhitespace()) cursor++
            if (cursor >= source.length || source[cursor] != ':') continue
            cursor++
            while (cursor < source.length && source[cursor].isWhitespace()) cursor++
            if (cursor >= source.length || source[cursor] != '"') continue

            val valueEnd = findClosingQuote(source, cursor + 1) ?: continue
            builder.append(source, index, cursor)
            builder.append('"').append(REDACTED).append('"')
            index = valueEnd + 1
        }
        return builder.toString()
    }

    /** Finds the closing quote of a JSON string, respecting backslash escapes. */
    private fun findClosingQuote(source: String, from: Int): Int? {
        var i = from
        while (i < source.length) {
            when (source[i]) {
                '\\' -> i++
                '"' -> return i
            }
            i++
        }
        return null
    }

    public companion object {
        /** Headers masked unless explicitly allowed. */
        public val DEFAULT_HEADERS: Set<String> = setOf(
            "Authorization",
            "Proxy-Authorization",
            "Cookie",
            "Set-Cookie",
            "X-Api-Key",
            "X-Auth-Token",
            "X-Csrf-Token",
        )

        /** Body keys masked unless explicitly allowed. Substring matched. */
        public val DEFAULT_BODY_KEYS: Set<String> = setOf(
            "password",
            "passwd",
            "secret",
            "token",
            "apikey",
            "api_key",
            "otp",
            "pin",
            "credit_card",
            "cvv",
        )

        /** A redactor with the defaults above. */
        public val DEFAULT: Redactor = Redactor(DEFAULT_HEADERS, DEFAULT_BODY_KEYS)
    }
}

/** Builder for [Redactor], reached via `redaction { }` in the install DSL. */
public class RedactorBuilder internal constructor() {
    private val headers = Redactor.DEFAULT_HEADERS.toMutableSet()
    private val bodyKeys = Redactor.DEFAULT_BODY_KEYS.toMutableSet()

    /** Adds header names to mask, on top of [Redactor.DEFAULT_HEADERS]. */
    public fun redactHeaders(vararg names: String) {
        headers += names
    }

    /** Adds body keys to mask, on top of [Redactor.DEFAULT_BODY_KEYS]. */
    public fun redactBodyKeys(vararg keys: String) {
        bodyKeys += keys
    }

    /**
     * Stops masking a header Scry redacts by default.
     *
     * Rarely correct. Present because "my API uses `Cookie` for a non-secret
     * session id and I need to see it" is a real situation, and the alternative
     * is that people disable redaction wholesale.
     */
    public fun allowHeader(name: String) {
        headers.removeAll { it.equals(name, ignoreCase = true) }
    }

    internal fun build(): Redactor = Redactor(headers.toSet(), bodyKeys.toSet())
}
