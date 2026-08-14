package io.github.akhilesh2491.scry.perf

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager

/**
 * Fragment load times, when the app has androidx.fragment.
 *
 * The dependency is `compileOnly`, so this class must never be *loaded* in an app
 * that does not have Fragments — every reference to a fragment type is confined
 * to [AndroidXFragmentWatcher], which is only constructed after the classpath
 * check passes. That split is the whole design; collapsing it into one class
 * would throw `NoClassDefFoundError` on verification in a Fragment-less app.
 */
internal class FragmentTracking(plugin: PerfPlugin) {

    private val watcher: AndroidXFragmentWatcher? =
        if (fragmentsOnClasspath()) {
            runCatching { AndroidXFragmentWatcher(plugin) }.getOrNull()
        } else {
            null
        }

    fun attach(activity: Activity) {
        watcher?.attach(activity)
    }

    fun detach(activity: Activity) {
        watcher?.detach(activity)
    }
}

private fun fragmentsOnClasspath(): Boolean = runCatching {
    Class.forName("androidx.fragment.app.FragmentActivity", false, FragmentTracking::class.java.classLoader)
}.isSuccess

private class AndroidXFragmentWatcher(private val plugin: PerfPlugin) {

    private val registered = mutableMapOf<Activity, FragmentManager.FragmentLifecycleCallbacks>()

    fun attach(activity: Activity) {
        val manager = (activity as? FragmentActivity)?.supportFragmentManager ?: return
        if (registered.containsKey(activity)) return

        val callbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(
                fm: FragmentManager,
                fragment: Fragment,
                view: View,
                savedInstanceState: Bundle?,
            ) {
                val createdAt = monotonicMillis()
                val name = fragment::class.simpleName ?: "Fragment"

                plugin.attributeFramesTo(name)
                view.onNextDraw { drawnAt ->
                    plugin.recordScreenLoad(
                        screen = name,
                        kind = ScreenKind.FRAGMENT,
                        ttidMillis = drawnAt - createdAt,
                    )
                }
            }

            override fun onFragmentResumed(fm: FragmentManager, fragment: Fragment) {
                plugin.attributeFramesTo(fragment::class.simpleName ?: "Fragment")
            }
        }

        // recursive = true: child fragments inside a ViewPager or a nested host
        // are exactly the ones whose load cost is hardest to see otherwise.
        manager.registerFragmentLifecycleCallbacks(callbacks, true)
        registered[activity] = callbacks
    }

    fun detach(activity: Activity) {
        val callbacks = registered.remove(activity) ?: return
        val manager = (activity as? FragmentActivity)?.supportFragmentManager ?: return
        runCatching { manager.unregisterFragmentLifecycleCallbacks(callbacks) }
    }
}
