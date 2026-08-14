package io.github.akhilesh2491.scry.sample.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.akhilesh2491.scry.perf.ScryTrace
import kotlin.random.Random

/**
 * A screen that is slow on purpose.
 *
 * The sample needs something that actually misses a frame budget, because a
 * performance tool you cannot see working is a performance tool nobody trusts.
 * Everything here is deliberately bad code — do not copy any of it.
 */
class PerfDemoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Blocks before the first frame, so this screen's TTID blows past the
        // 300 ms budget the sample sets.
        ScryTrace.measure("PerfDemo.blockingSetup") {
            Thread.sleep(SETUP_BLOCK_MILLIS)
        }

        val rows = ScryTrace.measure("PerfDemo.buildRows") {
            List(ROW_COUNT) { "Row $it" }
        }

        setContent {
            Surface(
                Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                    Text(
                        "Scroll hard — every row does pointless work on the main " +
                            "thread, so frames drop and the Performance badge turns red.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                    JankyList(rows)
                }
            }
        }
    }

    private companion object {
        const val SETUP_BLOCK_MILLIS = 700L
        const val ROW_COUNT = 300
    }
}

@Composable
private fun JankyList(rows: List<String>) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(rows) { row ->
            // Sorting a fresh list per row, per composition. This is the jank.
            val checksum = List(4_000) { Random.nextInt() }.sorted().first()

            Text(
                text = "$row  ·  $checksum",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
