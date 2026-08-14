package io.github.akhilesh2491.scry.logs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The logcat `threadtime` parser.
 *
 * Worth its own tests because a wrong regex fails silently: entries simply never
 * appear, which looks like "capture is broken" rather than "parsing is wrong".
 */
class LogcatParserTest {

    @Test
    fun `parses a standard threadtime line`() {
        val parsed = parseLogcatLine(
            "08-10 10:54:01.234  1234  1240 I MyTag: Something happened",
        )
        assertEquals(LogLevel.INFO, parsed?.first)
        assertEquals("MyTag", parsed?.second)
        assertEquals("Something happened", parsed?.third)
    }

    @Test
    fun `maps every level character`() {
        fun level(c: String) =
            parseLogcatLine("08-10 10:54:01.234  1  2 $c T: m")?.first

        assertEquals(LogLevel.VERBOSE, level("V"))
        assertEquals(LogLevel.DEBUG, level("D"))
        assertEquals(LogLevel.INFO, level("I"))
        assertEquals(LogLevel.WARN, level("W"))
        assertEquals(LogLevel.ERROR, level("E"))
        assertEquals(LogLevel.ASSERT, level("F"))
        assertEquals(LogLevel.ASSERT, level("A"))
    }

    @Test
    fun `keeps colons and spaces inside the message`() {
        val parsed = parseLogcatLine(
            "08-10 10:54:01.234  1  2 D Net: GET https://x.test/a?b=1 -> 200",
        )
        assertEquals("Net", parsed?.second)
        assertEquals("GET https://x.test/a?b=1 -> 200", parsed?.third)
    }

    @Test
    fun `handles tags containing spaces`() {
        val parsed = parseLogcatLine("08-10 10:54:01.234  1  2 W My Tag: careful")
        assertEquals("My Tag", parsed?.second)
        assertEquals("careful", parsed?.third)
    }

    @Test
    fun `allows an empty message`() {
        assertEquals("", parseLogcatLine("08-10 10:54:01.234  1  2 I T: ")?.third)
    }

    @Test
    fun `rejects continuation and banner lines rather than inventing a tag`() {
        assertNull(parseLogcatLine("--------- beginning of main"))
        assertNull(parseLogcatLine("\tat com.example.Foo.bar(Foo.kt:1)"))
        assertNull(parseLogcatLine(""))
        assertNull(parseLogcatLine("08-10 10:54:01.234  1  2 X T: unknown level"))
    }
}
