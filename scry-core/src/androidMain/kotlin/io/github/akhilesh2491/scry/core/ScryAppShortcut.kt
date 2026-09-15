package io.github.akhilesh2491.scry.core

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * A "Scry" entry under a long-press of the app icon.
 *
 * The only launcher surface that is reachable *without* the app running, and it
 * costs nothing: a dynamic shortcut may target a non-exported activity, because
 * the launcher starts it on the app's behalf. That is what keeps the Scry UI
 * `exported="false"` while still being one long-press away — unlike a drawer
 * icon, which cannot ([ScryLauncherIcon]).
 *
 * Registered for you by [Scry.install] unless `launchers { appShortcut = false }`.
 */
public object ScryAppShortcut {

    private const val SHORTCUT_ID = "scry_open"

    /** Adds the shortcut. No-op below API 25, which has no dynamic shortcuts. */
    @JvmStatic
    public fun add(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        addShortcut(context.applicationContext)
    }

    /** Removes the shortcut. */
    @JvmStatic
    public fun remove(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        removeShortcut(context.applicationContext)
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun addShortcut(context: Context) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val shortcut = ShortcutInfo.Builder(context, SHORTCUT_ID)
            .setShortLabel("Scry")
            .setLongLabel("Open Scry")
            .setIcon(Icon.createWithResource(context, R.drawable.scry_ic_launcher))
            .setIntent(scryActivityIntent(context).setAction(Intent.ACTION_VIEW))
            .build()
        runCatching { manager.addDynamicShortcuts(listOf(shortcut)) }
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun removeShortcut(context: Context) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        runCatching { manager.removeDynamicShortcuts(listOf(SHORTCUT_ID)) }
    }
}
