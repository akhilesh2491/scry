package io.github.akhilesh2491.scry.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedactorTest {

    private val redactor = Redactor.DEFAULT

    @Test
    fun `masks default auth headers regardless of case`() {
        assertTrue(redactor.shouldRedactHeader("Authorization"))
        assertTrue(redactor.shouldRedactHeader("authorization"))
        assertTrue(redactor.shouldRedactHeader("AUTHORIZATION"))
        assertTrue(redactor.shouldRedactHeader("Set-Cookie"))
    }

    @Test
    fun `leaves ordinary headers alone`() {
        assertFalse(redactor.shouldRedactHeader("Content-Type"))
        assertEquals("application/json", redactor.redactHeaderValue("Content-Type", "application/json"))
    }

    @Test
    fun `masks header values`() {
        assertEquals(REDACTED, redactor.redactHeaderValue("Authorization", "Bearer abc123"))
    }

    @Test
    fun `masks sensitive json string values`() {
        val body = """{"user":"ada","password":"hunter2"}"""
        val result = redactor.redactJsonBody(body)

        assertFalse(result.contains("hunter2"))
        assertTrue(result.contains("ada"), "non-sensitive values must survive")
        assertTrue(result.contains(REDACTED))
    }

    @Test
    fun `body key matching is substring based`() {
        val body = """{"refresh_token":"abc","tokenExpiry":"soon"}"""
        val result = redactor.redactJsonBody(body)

        assertFalse(result.contains("abc"))
        assertFalse(result.contains("soon"))
    }

    @Test
    fun `handles escaped quotes inside redacted values`() {
        val body = """{"secret":"a\"b","keep":"yes"}"""
        val result = redactor.redactJsonBody(body)

        assertFalse(result.contains("""a\"b"""))
        assertTrue(result.contains("yes"))
    }

    @Test
    fun `leaves non-string values alone rather than guessing`() {
        // A numeric or object value is not rewritten — the redactor prefers
        // under-reaching to corrupting the body.
        val body = """{"pin":1234,"password":{"nested":"x"}}"""
        val result = redactor.redactJsonBody(body)

        assertEquals(body, result)
    }

    @Test
    fun `malformed json does not throw`() {
        // Captured bodies are frequently truncated. A redactor that throws is a
        // redactor that leaks, so this must degrade rather than fail.
        val truncated = """{"password":"secr"""
        redactor.redactJsonBody(truncated)
        redactor.redactJsonBody("""{"unclosed: """)
        redactor.redactJsonBody("not json at all")
        redactor.redactJsonBody("")
    }

    @Test
    fun `custom redaction adds to defaults without replacing them`() {
        val custom = RedactorBuilder().apply { redactHeaders("X-Trace-Id") }.build()

        assertTrue(custom.shouldRedactHeader("X-Trace-Id"))
        assertTrue(custom.shouldRedactHeader("Authorization"), "defaults must be retained")
    }

    @Test
    fun `allowHeader opts a default header back out`() {
        val custom = RedactorBuilder().apply { allowHeader("Cookie") }.build()

        assertFalse(custom.shouldRedactHeader("Cookie"))
        assertTrue(custom.shouldRedactHeader("Authorization"))
    }
}
