package io.github.akhilesh2491.scry.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LauncherConfigTest {

    @Test
    fun `defaults put something on screen without the host asking`() {
        val config = LauncherConfig.DEFAULT

        assertTrue(config.bubble)
        assertTrue(config.notification)
        assertTrue(config.appShortcut)
    }

    /**
     * The two opt-in surfaces, asserted because their defaults are the whole
     * argument: the drawer icon needs an exported component, and shake holds an
     * accelerometer listener. Neither may arrive by accident.
     */
    @Test
    fun `launcher icon and shake stay off until asked for`() {
        assertFalse(LauncherConfig.DEFAULT.launcherIcon)
        assertFalse(LauncherConfig.DEFAULT.shake)
    }

    @Test
    fun `NONE disables every surface`() {
        val config = LauncherConfig.NONE

        assertFalse(config.bubble)
        assertFalse(config.notification)
        assertFalse(config.appShortcut)
        assertFalse(config.launcherIcon)
        assertFalse(config.shake)
    }

    @Test
    fun `install DSL carries the launchers block into the config`() {
        val config = ScryConfigBuilder().apply {
            launchers {
                notification = false
                launcherIcon = true
            }
        }.build()

        assertTrue(config.launchers.bubble)
        assertFalse(config.launchers.notification)
        assertTrue(config.launchers.launcherIcon)
    }

    @Test
    fun `builder overrides survive into the built config`() {
        val config = LauncherConfigBuilder().apply {
            bubble = false
            shake = true
            bubbleCorner = BubbleCorner.TOP_START
        }.build()

        assertFalse(config.bubble)
        assertTrue(config.shake)
        assertTrue(config.notification)
        assertEquals(BubbleCorner.TOP_START, config.bubbleCorner)
    }
}
