package io.github.akhilesh2491.scry.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import io.github.akhilesh2491.scry.core.Scry
import io.github.akhilesh2491.scry.core.ScryInstance

/**
 * Scry's desktop window, plus its tray launcher.
 *
 * Place once inside `application { }`. The window renders nothing until Scry is
 * shown; the tray icon appears immediately, so a desktop host has something
 * visible to click without having to remember a hotkey.
 *
 * ```kotlin
 * application {
 *     Window(onCloseRequest = ::exitApplication, onKeyEvent = ::onScryHotkey) { App() }
 *     ScryDesktopWindow(instance)
 * }
 * ```
 *
 * An extension on `ApplicationScope` because a tray icon can only be declared
 * there. Existing call sites are already inside `application { }`, so nothing
 * changes for them.
 */
@Composable
public fun ApplicationScope.ScryDesktopWindow(instance: ScryInstance) {
    // Desktop's stand-in for the Android launcher notification: always present,
    // costs one menu, and does not steal focus.
    if (instance.config.launchers.notification) ScryTray()

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
 * A system-tray icon that opens Scry.
 *
 * Rendered by [ScryDesktopWindow] unless `launchers { notification = false }`;
 * public so a host that builds its own tray menu can place it deliberately.
 *
 * Desktop gets no floating bubble on purpose. On Android the bubble lives inside
 * the app's own window; the desktop equivalent would be a second always-on-top
 * OS window sitting over every application on the machine, which is a far larger
 * imposition than a tray icon plus the hotkey below.
 */
@Composable
public fun ApplicationScope.ScryTray() {
    Tray(
        icon = rememberVectorPainter(Icons.Default.Visibility),
        tooltip = "Scry",
        onAction = { Scry.show() },
    ) {
        Item("Open Scry", onClick = { Scry.show() })
        Separator()
        Item("Clear captured data", onClick = { Scry.clear() })
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
