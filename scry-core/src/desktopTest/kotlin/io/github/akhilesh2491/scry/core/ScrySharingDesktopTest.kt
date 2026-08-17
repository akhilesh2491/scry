package io.github.akhilesh2491.scry.core

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Desktop sharing writes the export before it tries to show it to anyone.
 *
 * The bug this guards against: `Desktop.open` throws for file types with no
 * registered handler — `.har` and `.json` on most machines — and the previous
 * implementation let that failure escape into `runCatching`, so a share reported
 * failure and left nothing behind. The file landing on disk is the part that
 * must not depend on a desktop environment being present at all.
 */
class ScrySharingDesktopTest {

    private val context = PlatformContext(applicationId = "scry-sharing-test")

    private val reports: File
        get() = File(context.scryStorageDirectory(), "reports")

    @AfterTest
    fun tearDown() {
        reports.listFiles()?.forEach { it.delete() }
    }

    @Test
    fun `writes the export and reports success`() {
        val shared = shareScryFile(context, "scry-test.har", """{"log":{}}""")

        assertTrue(shared, "sharing should succeed once the file is written")
        assertEquals("""{"log":{}}""", File(reports, "scry-test.har").readText())
    }

    @Test
    fun `creates the reports directory when it does not exist`() {
        reports.deleteRecursively()

        assertTrue(shareScryFile(context, "scry-test.txt", "hello"))
        assertTrue(reports.isDirectory)
    }

    @Test
    fun `overwrites a previous export of the same name`() {
        shareScryFile(context, "scry-test.txt", "first")
        shareScryFile(context, "scry-test.txt", "second")

        assertEquals("second", File(reports, "scry-test.txt").readText())
    }

    @Test
    fun `an unwritable path fails without throwing`() {
        // A share that crashes the app is worse than one that does not happen.
        val shared = shareScryFile(context, "nested/dir/scry-test.txt", "x")

        assertTrue(!shared, "an unwritable target should report failure, not throw")
    }
}
