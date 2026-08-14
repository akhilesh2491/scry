package io.github.akhilesh2491.scry.ui

import androidx.compose.ui.window.ComposeUIViewController
import io.github.akhilesh2491.scry.core.Scry
import io.github.akhilesh2491.scry.core.ScryInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import platform.UIKit.UIApplication
import platform.UIKit.UIModalPresentationPageSheet
import platform.UIKit.UIViewController

/**
 * The Scry UI as a `UIViewController`.
 *
 * Present it yourself if you have a controller to present from — from SwiftUI,
 * from a debug menu, from wherever. [enableScryUi] is the wire-once alternative.
 */
public fun ScryUIViewController(instance: ScryInstance): UIViewController =
    ComposeUIViewController {
        ScryShell(instance = instance, onDismiss = { Scry.hide() })
    }

/**
 * Makes [Scry.show] present the Scry UI.
 *
 * Call once after `Scry.install`. Presents from the key window's root view
 * controller, which is the only way to reach UIKit without the app handing Scry
 * a controller — it works from both UIKit and SwiftUI hosts, but an app that
 * manages its own presentation stack should call [ScryUIViewController] instead.
 */
public fun enableScryUi(): Job? {
    val instance = Scry.instance ?: return null
    var presented: UIViewController? = null

    return CoroutineScope(Dispatchers.Main).launch {
        instance.isVisible.collect { visible ->
            val root = UIApplication.sharedApplication.keyWindow?.rootViewController
            if (visible) {
                if (presented != null || root == null) return@collect
                val controller = ScryUIViewController(instance).apply {
                    modalPresentationStyle = UIModalPresentationPageSheet
                }
                presented = controller
                root.presentViewController(controller, animated = true, completion = null)
            } else {
                presented?.dismissViewControllerAnimated(true, completion = null)
                presented = null
            }
        }
    }
}
