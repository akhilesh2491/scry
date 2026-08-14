package io.github.akhilesh2491.scry.sample.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.akhilesh2491.scry.core.Scry
import io.github.akhilesh2491.scry.network.ktor.ScryKtor
import io.github.akhilesh2491.scry.network.okhttp.ScryInterceptor
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class MainActivity : ComponentActivity() {

    // Ktor and OkHttp side by side, so one run exercises both capture adapters
    // and proves they land in the same list.
    private val ktorClient = HttpClient(OkHttp) { install(ScryKtor) }
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(ScryInterceptor())
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SampleTheme { SampleScreen(ktorClient, okHttpClient) }
        }
    }
}

/**
 * The sample's own theme.
 *
 * Close to Scry's palette on purpose: the sample exists to be screenshotted
 * next to Scry, and a stock-purple Material demo next to a tuned debugger looks
 * like an accident. It is still a *separate* theme — Scry never inherits it.
 */
@Composable
private fun SampleTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) {
            darkColorScheme(
                primary = Color(0xFF2DD4BF), onPrimary = Color(0xFF06231F),
                background = Color(0xFF0B0E14), onBackground = Color(0xFFE6E9EF),
                surface = Color(0xFF0F131A), onSurface = Color(0xFFE6E9EF),
                surfaceVariant = Color(0xFF1A1F29), onSurfaceVariant = Color(0xFF8B93A7),
                outline = Color(0xFF2A3140),
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF0F766E), onPrimary = Color.White,
                background = Color(0xFFF7F8FA), onBackground = Color(0xFF111827),
                surface = Color(0xFFFFFFFF), onSurface = Color(0xFF111827),
                surfaceVariant = Color(0xFFF0F2F5), onSurfaceVariant = Color(0xFF6B7280),
                outline = Color(0xFFD1D5DB),
            )
        },
        content = content,
    )
}

@Composable
private fun SampleScreen(ktorClient: HttpClient, okHttpClient: OkHttpClient) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun fire(label: String, block: suspend () -> String) {
        scope.launch {
            busy = true
            status = "$label …"
            status = runCatching { block() }
                .fold(
                    { "$label → ok, ${it.length} chars" },
                    { "$label → failed: ${it.message?.take(90)}" },
                )
            busy = false
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                "Scry sample",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Generate traffic and failures, then shake the device to inspect them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
            )

            SectionLabel("Traffic")
            ActionCard(
                "Ktor · GET",
                "example.com — the multiplatform capture path",
                enabled = !busy,
            ) { fire("Ktor GET") { ktorClient.get("https://example.com/").bodyAsText() } }
            ActionCard(
                "Ktor · POST with secrets",
                "Authorization header and a password field, both should render masked",
                enabled = !busy,
            ) {
                fire("Ktor POST") {
                    ktorClient.post("https://example.com/login") {
                        header("Authorization", "Bearer super-secret-token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"user":"ada","password":"hunter2"}""")
                    }.bodyAsText()
                }
            }
            ActionCard(
                "OkHttp · GET",
                "The second adapter — lands in the same transaction list",
                enabled = !busy,
            ) {
                fire("OkHttp GET") {
                    withContext(Dispatchers.IO) {
                        okHttpClient
                            .newCall(Request.Builder().url("https://example.com/").build())
                            .execute().use { it.body?.string().orEmpty() }
                    }
                }
            }

            SectionLabel("Failures")
            ActionCard(
                "Unreachable host",
                "Transport failure, captured with its error message",
                enabled = !busy,
                tone = Tone.WARNING,
            ) {
                fire("Unreachable") {
                    ktorClient.get("https://this-host-does-not-exist.invalid/x").bodyAsText()
                }
            }
            ActionCard(
                "Crash the app",
                "Uncaught on a background thread — Scry records it, then the app still dies",
                tone = Tone.DANGER,
            ) {
                Thread { error("Deliberate sample crash from a background thread") }.start()
            }
            ActionCard(
                "Freeze the main thread",
                "Blocks for 6s, past the 3s ANR threshold",
                tone = Tone.DANGER,
            ) { Thread.sleep(6_000) }

            Button(
                onClick = { Scry.show() },
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                shape = RoundedCornerShape(10.dp),
            ) { Text("Open Scry", fontWeight = FontWeight.SemiBold) }

            status?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                )
            }
        }
    }
}

private enum class Tone { NEUTRAL, WARNING, DANGER }

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

/**
 * A tappable card with a title and an explanation of what it demonstrates.
 *
 * Buttons labelled only "GET /json" tell you nothing about why the sample has
 * them; the subtitle is what makes this a demo rather than a test harness.
 */
@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    tone: Tone = Tone.NEUTRAL,
    onClick: () -> Unit,
) {
    val accent = when (tone) {
        Tone.NEUTRAL -> MaterialTheme.colorScheme.primary
        Tone.WARNING -> Color(0xFFFBBF24)
        Tone.DANGER -> Color(0xFFF87171)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(accent.copy(alpha = if (enabled) 1f else 0.4f), RoundedCornerShape(4.dp)),
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
