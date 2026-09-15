package io.github.akhilesh2491.scry.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The placement maths, which decides whether the bubble is reachable at all.
 *
 * Tested here rather than on a device because it is pure: everything that needs
 * a real window — adding the view, the touch stream — is separate from this.
 */
class BubblePositionTest {

    private val width = 1080
    private val height = 1920
    private val size = 144
    private val margin = 48

    @Test
    fun `a fresh install starts in the requested corner`() {
        val placement = resolve(requested = null, corner = BubbleCorner.BOTTOM_END)

        assertEquals((width - size - margin).toFloat(), placement.x)
        assertEquals((height - size - margin).toFloat(), placement.y)
    }

    @Test
    fun `top start corner lands at the margin on both axes`() {
        val placement = resolve(requested = null, corner = BubbleCorner.TOP_START)

        assertEquals(margin.toFloat(), placement.x)
        assertEquals(margin.toFloat(), placement.y)
    }

    @Test
    fun `a drag that ends past halfway snaps to the right edge`() {
        val placement = resolve(BubblePosition(0.55f, 0.4f), BubbleCorner.BOTTOM_END)

        assertEquals((width - size - margin).toFloat(), placement.x)
    }

    @Test
    fun `a drag that ends before halfway snaps to the left edge`() {
        val placement = resolve(BubblePosition(0.45f, 0.4f), BubbleCorner.BOTTOM_END)

        assertEquals(margin.toFloat(), placement.x)
    }

    /**
     * The rotation case. A position saved in landscape is replayed against a
     * portrait window, and the bubble has to stay on screen — fractions out of
     * range are what a mid-drag rotation actually produces.
     */
    @Test
    fun `out of range fractions are clamped inside the window`() {
        val placement = resolve(BubblePosition(2f, 3f), BubbleCorner.BOTTOM_END)

        assertTrue(placement.x in 0f..(width - size).toFloat())
        assertTrue(placement.y in 0f..(height - size).toFloat())
    }

    /**
     * The regression that shipped in the first build: an edge-to-edge app's
     * content view runs under the navigation bar, so a bubble placed by content
     * height alone sits on the gesture handle and every tap goes to the system.
     */
    @Test
    fun `system bars are kept clear of the bubble`() {
        val navBar = 132
        val statusBar = 96

        val placement = resolveBubblePosition(
            requested = null,
            corner = BubbleCorner.BOTTOM_END,
            parentWidth = width,
            parentHeight = height,
            bubbleSize = size,
            margin = margin,
            insets = SystemInsets(top = statusBar, bottom = navBar),
        )

        assertEquals((height - size - navBar - margin).toFloat(), placement.y)
    }

    @Test
    fun `a saved position under the navigation bar is pulled back above it`() {
        val navBar = 132

        val placement = resolveBubblePosition(
            requested = BubblePosition(1f, 1f),
            corner = BubbleCorner.BOTTOM_END,
            parentWidth = width,
            parentHeight = height,
            bubbleSize = size,
            margin = margin,
            insets = SystemInsets(bottom = navBar),
        )

        assertTrue(placement.y <= (height - size - navBar - margin).toFloat())
    }

    @Test
    fun `a window smaller than the bubble still produces a placement on screen`() {
        val placement = resolveBubblePosition(
            requested = BubblePosition(1f, 1f),
            corner = BubbleCorner.BOTTOM_END,
            parentWidth = 100,
            parentHeight = 100,
            bubbleSize = size,
            margin = margin,
        )

        assertEquals(0f, placement.x)
        assertEquals(0f, placement.y)
    }

    private fun resolve(requested: BubblePosition?, corner: BubbleCorner) =
        resolveBubblePosition(
            requested = requested,
            corner = corner,
            parentWidth = width,
            parentHeight = height,
            bubbleSize = size,
            margin = margin,
        )
}
