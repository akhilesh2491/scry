package io.github.akhilesh2491.scry.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Opens Scry when the device is shaken.
 *
 * ```kotlin
 * class MyApp : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         Scry.install(this) { plugin(NetworkPlugin()) }
 *         ShakeToOpen(this).start()
 *     }
 * }
 * ```
 *
 * Shake is the convention every on-device debugger uses because the alternative
 * — a button somewhere in the app — means shipping debug UI inside product
 * screens, and it is never where you need it.
 *
 * Deliberately not started automatically by [Scry.install]: registering a sensor
 * listener the caller did not ask for is the kind of hidden battery cost that
 * gets a library removed.
 */
public class ShakeToOpen @JvmOverloads constructor(
    context: Context,
    /**
     * Acceleration in g beyond which a shake is registered. 2.7g rejects normal
     * walking and pocket movement while staying comfortable to trigger on purpose.
     */
    private val thresholdG: Float = 2.7f,
    /** Shakes closer together than this are treated as one gesture. */
    private val debounceMillis: Long = 1_000L,
) : SensorEventListener {

    private val sensorManager = context.applicationContext
        .getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var lastShakeAt = 0L
    private var listening = false

    /** Begins listening. Safe to call when the device has no accelerometer. */
    public fun start() {
        if (listening || accelerometer == null) return
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        listening = true
    }

    /** Stops listening. Call from `onStop` if you only want it in the foreground. */
    public fun stop() {
        if (!listening) return
        sensorManager?.unregisterListener(this)
        listening = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
        val gForce = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        if (gForce < thresholdG) return

        val now = System.currentTimeMillis()
        if (now - lastShakeAt < debounceMillis) return
        lastShakeAt = now

        if (Scry.instance?.isVisible?.value == true) Scry.hide() else Scry.show()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int): Unit = Unit
}
