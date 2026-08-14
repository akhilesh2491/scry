package io.github.akhilesh2491.scry.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.akhilesh2491.scry.core.PlatformContext
import io.github.akhilesh2491.scry.core.Scry
import io.github.akhilesh2491.scry.network.NetworkPlugin
import io.github.akhilesh2491.scry.network.ktor.ScryKtor
import io.github.akhilesh2491.scry.perf.PerfPlugin
import io.github.akhilesh2491.scry.ui.ScryDesktopWindow
import io.github.akhilesh2491.scry.ui.onScryHotkey
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.launch

private val scry = Scry.install(PlatformContext(applicationId = "scry-sample-desktop")) {
    plugin(NetworkPlugin { maxBodyBytes = 128 * 1024 })
    plugin(PerfPlugin())
}

private val httpClient = HttpClient(CIO) {
    install(ScryKtor)
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Scry sample",
        state = rememberWindowState(width = 520.dp, height = 420.dp),
        onKeyEvent = ::onScryHotkey,
    ) {
        MaterialTheme { SampleScreen() }
    }

    // Renders only when Scry is visible.
    scry?.let { ScryDesktopWindow(it) }
}

@Composable
private fun SampleScreen() {
    val scope = rememberCoroutineScope()
    var lastResult by remember { mutableStateOf("Fire a request, then press Ctrl/Cmd+Shift+S.") }

    fun fire(label: String, block: suspend () -> String) {
        scope.launch {
            lastResult = "$label …"
            lastResult = runCatching { block() }
                .fold({ "$label → ${it.take(120)}" }, { "$label → failed: ${it.message}" })
        }
    }

    // Seed one request on first composition so opening Scry shows something
    // immediately rather than an empty list.
    LaunchedEffect(Unit) {
        fire("GET json (startup)") { httpClient.get("https://httpbin.org/json").bodyAsText() }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Scry sample", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Ctrl+Shift+S (Cmd+Shift+S on macOS) opens Scry.",
                style = MaterialTheme.typography.bodySmall,
            )

            Button(
                onClick = {
                    fire("GET json") {
                        httpClient.get("https://httpbin.org/json").bodyAsText()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("GET  /json") }

            Button(
                onClick = {
                    // Carries an Authorization header and a password field, so the
                    // Scry UI should show both masked.
                    fire("POST with secrets") {
                        httpClient.post("https://httpbin.org/post") {
                            header("Authorization", "Bearer super-secret-token")
                            contentType(ContentType.Application.Json)
                            setBody("""{"user":"ada","password":"hunter2"}""")
                        }.bodyAsText()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("POST  /post  (redaction check)") }

            Button(
                onClick = {
                    fire("GET 500") {
                        httpClient.get("https://httpbin.org/status/500").bodyAsText()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("GET  /status/500") }

            Button(
                onClick = {
                    fire("GET unreachable") {
                        httpClient.get("https://this-host-does-not-exist.invalid/x").bodyAsText()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("GET  unreachable host  (error capture)") }

            Button(
                onClick = { Scry.show() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Open Scry") }

            Text(lastResult, style = MaterialTheme.typography.bodySmall)
        }
    }
}
