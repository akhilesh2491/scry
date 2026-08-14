package io.github.akhilesh2491.scry.sample.android;

import android.app.Activity;
import android.os.Bundle;

import io.github.akhilesh2491.scry.core.Retention;
import io.github.akhilesh2491.scry.core.Scry;
import io.github.akhilesh2491.scry.core.ScryAndroid;
import io.github.akhilesh2491.scry.core.ShakeToOpen;
import io.github.akhilesh2491.scry.network.NetworkPlugin;
import io.github.akhilesh2491.scry.network.okhttp.ScryInterceptor;

import okhttp3.OkHttpClient;

/**
 * The Android setup path, written in Java.
 *
 * Requirement #1 is that Kotlin, Java and KMP callers can all use Scry. This
 * activity is compiled on every build, so a Kotlin default argument or a suspend
 * function leaking into the public API breaks the build here rather than in
 * someone's Java codebase after release.
 */
public final class JavaSetupActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Builder facade — no Kotlin DSL, no lambdas with receivers.
        ScryAndroid.installer(this)
                .retention(Retention.ofHours(12))
                .redactHeaders("X-Internal-Trace")
                .addPlugin(new NetworkPlugin())
                .install();

        new ShakeToOpen(this).start();

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new ScryInterceptor())
                .build();

        // Static entry points, callable without Scry.INSTANCE.
        //
        // Note this uses Scry.show() rather than referencing ScryActivity: app
        // code must never touch the UI module, or the release build would need a
        // no-op that carries Compose.
        if (Scry.isInstalled()) {
            Scry.show();
        }
        finish();
    }
}
