package io.github.akhilesh2491.scry.sample.ios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import io.github.akhilesh2491.scry.core.PlatformContext
import io.github.akhilesh2491.scry.core.Scry
import io.github.akhilesh2491.scry.crash.CrashPlugin
import io.github.akhilesh2491.scry.database.DatabasePlugin
import io.github.akhilesh2491.scry.logs.LogPlugin
import io.github.akhilesh2491.scry.logs.ScryLog
import io.github.akhilesh2491.scry.network.NetworkPlugin
import io.github.akhilesh2491.scry.network.ktor.ScryKtor
import io.github.akhilesh2491.scry.prefs.PreferencesPlugin
import io.github.akhilesh2491.scry.ui.enableScryUi
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIViewController

private val httpClient = HttpClient(Darwin) { install(ScryKtor) }

/**
 * Installs Scry and returns the sample's root view controller.
 *
 * Called once from Swift. Everything Scry needs on iOS is here: install,
 * connect the UI, and let `Scry.show()` do the rest.
 */
public fun MainViewController(): UIViewController {
    seedPreferences()

    Scry.install(PlatformContext()) {
        plugin(NetworkPlugin { maxBodyBytes = 128 * 1024 })
        plugin(PreferencesPlugin())
        plugin(DatabasePlugin { allowFileModeWrites(true) })
        plugin(CrashPlugin())
        plugin(LogPlugin())
    }
    enableScryUi()
    ScryLog.i("SampleApp", "Scry installed on iOS")

    if (NSProcessInfo.processInfo.arguments.contains(DEMO_ARGUMENT)) runDemo()

    return ComposeUIViewController { MaterialTheme { SampleScreen() } }
}

/** Launch argument that drives the sample without touching the screen. */
private const val DEMO_ARGUMENT: String = "--scry-demo"

/**
 * Fires the sample's requests and opens Scry, for automated verification.
 *
 * `xcrun simctl launch <sim> <bundle> --scry-demo`
 *
 * Deliberately goes through the same path a tap would — the buttons call the
 * same code — so this verifies the real presentation flow rather than a
 * shortcut around it.
 */
private fun runDemo() {
    CoroutineScope(Dispatchers.Main).launch {
        runCatching { httpClient.get("https://example.com/").bodyAsText() }
        runCatching {
            httpClient.post("https://example.com/login") {
                header("Authorization", "Bearer super-secret-token")
                contentType(ContentType.Application.Json)
                setBody("""{"user":"ada","password":"hunter2"}""")
            }.bodyAsText()
        }
        runCatching {
            httpClient.get("https://this-host-does-not-exist.invalid/x").bodyAsText()
        }
        Scry.show()
    }
}

/** Gives the preferences inspector something of every type to look at. */
private fun seedPreferences() {
    NSUserDefaults.standardUserDefaults.apply {
        setObject("ada", forKey = "username")
        setBool(true, forKey = "onboarding_complete")
        setInteger(7, forKey = "launch_count")
        setDouble(1.25, forKey = "playback_speed")
        synchronize()
    }
}

@Composable
private fun SampleScreen() {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Fire a request, then tap Open Scry.") }

    fun fire(label: String, block: suspend () -> String) {
        scope.launch {
            status = "$label …"
            status = runCatching { block() }
                .fold({ "$label → ok (${it.length} chars)" }, { "$label → failed: ${it.message}" })
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Scry iOS sample", style = MaterialTheme.typography.headlineSmall)

            Button(
                onClick = { fire("GET") { httpClient.get("https://example.com/").bodyAsText() } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Ktor  GET example.com") }

            Button(
                onClick = {
                    // Authorization header + password field: both must render masked.
                    fire("POST secrets") {
                        httpClient.post("https://example.com/login") {
                            header("Authorization", "Bearer super-secret-token")
                            contentType(ContentType.Application.Json)
                            setBody("""{"user":"ada","password":"hunter2"}""")
                        }.bodyAsText()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Ktor  POST  (redaction check)") }

            Button(
                onClick = {
                    fire("Unreachable") {
                        httpClient.get("https://this-host-does-not-exist.invalid/x").bodyAsText()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("GET  unreachable host  (error capture)") }

            Button(
                onClick = { Scry.show() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Open Scry") }

            Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}
