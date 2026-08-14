package io.github.akhilesh2491.scry.sample.android

import android.app.Application
import kotlin.time.Duration.Companion.seconds
import io.github.akhilesh2491.scry.core.ShakeToOpen
import io.github.akhilesh2491.scry.core.install
import android.content.Context
import io.github.akhilesh2491.scry.network.MockAction
import io.github.akhilesh2491.scry.network.MockRule
import io.github.akhilesh2491.scry.network.NetworkPlugin
import io.github.akhilesh2491.scry.crash.CrashPlugin
import io.github.akhilesh2491.scry.database.DatabasePlugin
import io.github.akhilesh2491.scry.logs.LogPlugin
import io.github.akhilesh2491.scry.logs.ScryLog
import io.github.akhilesh2491.scry.prefs.PreferencesPlugin
import io.github.akhilesh2491.scry.core.Scry
import io.github.akhilesh2491.scry.core.enableScryUi

/**
 * The whole Android integration, in one place.
 *
 * Three lines: install, connect the UI, start a launcher. Anything more than
 * that and teams will not adopt it.
 */
class SampleApplication : Application() {

    lateinit var shakeToOpen: ShakeToOpen
        private set

    override fun onCreate() {
        super.onCreate()

        seedPreferences()
        seedDatabase()

        Scry.install(this) {
            plugin(NetworkPlugin { maxBodyBytes = 128 * 1024 })
            plugin(PreferencesPlugin())
            // File mode with writes enabled: this sample database is not under
            // contention. A production app should prefer instance mode.
            plugin(DatabasePlugin { allowFileModeWrites(true) })
            plugin(CrashPlugin { anrThreshold = 3.seconds })
            plugin(LogPlugin { captureSystemLog = true })
        }

        // Bridges core (no UI dependency) to the Compose shell.
        enableScryUi(this)

        shakeToOpen = ShakeToOpen(this).also { it.start() }

        seedMockRules()

        ScryLog.i("SampleApp", "Scry installed with 5 plugins")
        ScryLog.w("SampleApp", "This is a warning, for the level filter")
    }

    /**
     * Registers example rules, all disabled.
     *
     * Disabled on purpose: the sample should show what the Mocks screen looks
     * like populated without silently changing the traffic the other buttons
     * produce.
     */
    private fun seedMockRules() {
        val network = Scry.instance?.plugins?.filterIsInstance<NetworkPlugin>()?.firstOrNull()
            ?: return
        network.mocks.addRule(
            MockRule(
                id = "inventory-down",
                name = "Inventory 503",
                urlPattern = "*/inventory*",
                method = "GET",
                enabled = false,
                action = MockAction.Respond(statusCode = 503, body = """{"down":true}"""),
            ),
        )
        network.mocks.addRule(
            MockRule(
                id = "payments-fail",
                name = "Payments outage",
                urlPattern = "*/payments*",
                enabled = false,
                action = MockAction.Fail("Simulated payment gateway outage"),
            ),
        )
        network.mocks.addRule(
            MockRule(
                id = "search-slow",
                name = "Slow search",
                urlPattern = "*/search*",
                enabled = false,
                action = MockAction.Delay(delayMillis = 800),
            ),
        )
    }

    /** Gives the database inspector a table to browse and edit. */
    private fun seedDatabase() {
        openOrCreateDatabase("app.db", MODE_PRIVATE, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS users (" +
                    "id INTEGER PRIMARY KEY, name TEXT NOT NULL, email TEXT, active INTEGER)",
            )
            db.execSQL("DELETE FROM users")
            db.execSQL("INSERT INTO users VALUES (1, 'ada', 'ada@example.com', 1)")
            db.execSQL("INSERT INTO users VALUES (2, 'grace', NULL, 1)")
            db.execSQL("INSERT INTO users VALUES (3, 'alan', 'alan@example.com', 0)")
            db.execSQL("CREATE TABLE IF NOT EXISTS sessions (token TEXT, created_at INTEGER)")
        }
    }

    /** Gives the preferences inspector something of every type to edit. */
    private fun seedPreferences() {
        getSharedPreferences("user_settings", Context.MODE_PRIVATE).edit()
            .putString("username", "ada")
            .putBoolean("onboarding_complete", true)
            .putInt("launch_count", 7)
            .putLong("last_sync_millis", 1_786_000_000_000L)
            .putFloat("playback_speed", 1.25f)
            .putStringSet("enabled_features", setOf("dark_mode", "beta_search"))
            .apply()

        getSharedPreferences("feature_flags", Context.MODE_PRIVATE).edit()
            .putBoolean("new_checkout", false)
            .putBoolean("offline_mode", false)
            .apply()
    }
}
