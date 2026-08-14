package io.github.akhilesh2491.scry.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import io.github.akhilesh2491.scry.core.Scry
import io.github.akhilesh2491.scry.core.ScryInstance

/**
 * Scry's desktop window, shown whenever the instance is visible.
 *
 * Place once inside `application { }`; it renders nothing until Scry is shown.
 *
 * ```kotlin
 * application {
 *     Window(onCloseRequest = ::exitApplication, onKeyEvent = ::onScryHotkey) { App() }
 *     ScryDesktopWindow(instance)
 * }
 * ```
 */
@Composable
public fun ScryDesktopWindow(instance: ScryInstance) {
    val visible by instance.isVisible.collectAsState()
    if (!visible) return

    Window(
        onCloseRequest = { Scry.hide() },
        title = "Scry",
        state = rememberWindowState(position = WindowPosition(Alignment.Center)),
    ) {
        ScryShell(instance = instance, onDismiss = { Scry.hide() })
    }
}

/**
 * Toggles Scry on `Ctrl+Shift+S` (`Cmd+Shift+S` on macOS).
 *
 * Wire to a window's `onKeyEvent`. Returns true when it consumed the event.
 *
 * Desktop's answer to shake-to-open: there is no accelerometer, and a debug tool
 * you can only reach through a button you had to remember to add is a debug tool
 * nobody opens.
 */
public fun onScryHotkey(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (!event.isShiftPressed) return false
    if (!event.isCtrlPressed && !event.isMetaPressed) return false
    if (event.key != Key.S) return false

    if (Scry.instance?.isVisible?.value == true) Scry.hide() else Scry.show()
    return true
}
