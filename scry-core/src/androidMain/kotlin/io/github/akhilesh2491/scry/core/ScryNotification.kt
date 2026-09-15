package io.github.akhilesh2491.scry.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * An ongoing notification that opens Scry.
 *
 * The one launcher that still works when the app is in the background, and the
 * only one visible while you are reading a crash in another app. Posted on a
 * `LOW` importance channel so it never makes a sound, vibrates, or takes over
 * the screen — it is a bookmark, not an alert.
 *
 * Posted for you by [Scry.install] unless `launchers { notification = false }`.
 */
public object ScryNotification {

    private const val CHANNEL_ID = "scry_launcher"
    private const val CHANNEL_NAME = "Scry"
    private const val NOTIFICATION_ID = 0x5C27

    internal const val ACTION_CLEAR = "io.github.akhilesh2491.scry.CLEAR"

    /**
     * Posts the notification. Idempotent — reposting replaces the existing one.
     *
     * Returns false when the host has no notification permission (API 33+ with
     * `POST_NOTIFICATIONS` denied). Deliberately silent in that case rather than
     * prompting: a debug library that throws a permission dialog at an app's
     * first launch is one that gets removed, and the bubble already covers it.
     */
    @JvmStatic
    public fun show(context: Context): Boolean {
        val appContext = context.applicationContext
        val manager = NotificationManagerCompat.from(appContext)
        if (!manager.areNotificationsEnabled()) return false

        createChannel(appContext)

        val open = PendingIntent.getActivity(
            appContext,
            0,
            scryActivityIntent(appContext),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val clear = PendingIntent.getBroadcast(
            appContext,
            1,
            Intent(appContext, ScryLauncherReceiver::class.java).setAction(ACTION_CLEAR),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.scry_ic_launcher)
            .setContentTitle("Scry")
            .setContentText("Tap to inspect network, storage, logs and crashes")
            .setContentIntent(open)
            .addAction(0, "Clear data", clear)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()

        return runCatching {
            @Suppress("MissingPermission")
            manager.notify(NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }

    /** Removes the notification. */
    @JvmStatic
    public fun hide(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Opens the Scry debugging suite"
            setShowBadge(false)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }
}

/**
 * Backs the notification's actions.
 *
 * `exported="false"` in the manifest: it clears captured data, and no other app
 * has any business doing that.
 */
public class ScryLauncherReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ScryNotification.ACTION_CLEAR) return
        Scry.clear()
        // Reposted because tapping an action dismisses nothing but still needs
        // the notification to survive — an ongoing launcher that vanishes when
        // you use it is worse than no launcher.
        ScryNotification.show(context)
    }
}
