package io.github.akhilesh2491.scry.core

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A draggable button that floats over the host app and opens Scry.
 *
 * The answer to "I installed Scry and nothing happened". It is drawn into the
 * app's **own** window — `android.R.id.content` of each resumed activity —
 * rather than a system overlay, for the same reason the Scry UI is an activity
 * and not a `TYPE_APPLICATION_OVERLAY`: asking a developer to grant "draw over
 * other apps" before they can use a debug library is a non-starter, and
 * `SYSTEM_ALERT_WINDOW` in a manifest is something security review notices.
 *
 * Started for you by [Scry.install] unless `launchers { bubble = false }`.
 */
public object ScryBubble {

    private const val PREFS_NAME = "scry_launcher"
    private const val KEY_X = "bubble_x_fraction"
    private const val KEY_Y = "bubble_y_fraction"
    private const val SIZE_DP = 48f
    private const val MARGIN_DP = 16f
    private const val LONG_PRESS_HIDE_MESSAGE =
        "Scry bubble hidden until restart — shake or call Scry.show()"

    private val attachedViews = WeakHashMap<Activity, View>()

    private var application: Application? = null
    private var callbacks: Application.ActivityLifecycleCallbacks? = null
    private var corner: BubbleCorner = BubbleCorner.BOTTOM_END
    private var visible: Boolean = true

    /**
     * Starts drawing the bubble over every activity of [application].
     *
     * Idempotent. Call [detach] to stop.
     */
    @JvmStatic
    @JvmOverloads
    public fun attach(
        application: Application,
        corner: BubbleCorner = BubbleCorner.BOTTOM_END,
    ) {
        if (callbacks != null) return
        this.application = application
        this.corner = corner
        this.visible = true

        val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity): Unit = addTo(activity)
            override fun onActivityPaused(activity: Activity): Unit = removeFrom(activity)
            override fun onActivityDestroyed(activity: Activity): Unit = removeFrom(activity)
            override fun onActivityCreated(activity: Activity, state: Bundle?): Unit = Unit
            override fun onActivityStarted(activity: Activity): Unit = Unit
            override fun onActivityStopped(activity: Activity): Unit = Unit
            override fun onActivitySaveInstanceState(activity: Activity, out: Bundle): Unit = Unit
        }
        application.registerActivityLifecycleCallbacks(lifecycleCallbacks)
        callbacks = lifecycleCallbacks
    }

    /** Removes the bubble and stops watching for new activities. */
    @JvmStatic
    public fun detach() {
        callbacks?.let { application?.unregisterActivityLifecycleCallbacks(it) }
        callbacks = null
        attachedViews.keys.toList().forEach(::removeFrom)
        application = null
    }

    /**
     * Hides or shows the bubble without giving up the lifecycle registration.
     *
     * What a long-press on the bubble does, so a developer can get an unobscured
     * screenshot without editing code.
     */
    @JvmStatic
    public fun setVisible(visible: Boolean) {
        this.visible = visible
        attachedViews.values.forEach { it.visibility = if (visible) View.VISIBLE else View.GONE }
    }

    /** Whether the bubble is currently being drawn. */
    @JvmStatic
    public fun isVisible(): Boolean = visible

    private fun addTo(activity: Activity) {
        // The Scry UI is an activity too, and a bubble on top of the thing it
        // opens is just something to mis-tap.
        if (activity.javaClass.name == SCRY_ACTIVITY_CLASS) return
        if (attachedViews.containsKey(activity)) return

        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val bubble = createBubbleView(activity)
        bubble.visibility = if (visible) View.VISIBLE else View.GONE

        val size = activity.dp(SIZE_DP)
        val margin = activity.dp(MARGIN_DP)
        bubble.layoutParams = FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.START)

        // Position needs the content view's measured size and its window insets,
        // and neither is known until layout. Placing it before that put every
        // bubble at 0,0 — and in an edge-to-edge app, under the navigation bar.
        content.post {
            val placement = resolveBubblePosition(
                requested = activity.savedPosition(),
                corner = corner,
                parentWidth = content.width,
                parentHeight = content.height,
                bubbleSize = size,
                margin = margin,
                insets = content.systemBarInsets(),
            )
            bubble.translationX = placement.x
            bubble.translationY = placement.y
        }

        attachDragBehaviour(activity, bubble, content, size, margin)
        content.addView(bubble)
        attachedViews[activity] = bubble
    }

    private fun removeFrom(activity: Activity) {
        val view = attachedViews.remove(activity) ?: return
        (view.parent as? ViewGroup)?.removeView(view)
    }

    private fun createBubbleView(context: Context): View = TextView(context).apply {
        text = "S"
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        textSize = 18f
        contentDescription = "Open Scry"
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(BUBBLE_COLOR)
            setStroke(context.dp(1f), Color.argb(80, 255, 255, 255))
        }
        elevation = context.dp(8f).toFloat()
        alpha = 0.92f
    }

    /**
     * Tap opens Scry; anything longer or further is a drag.
     *
     * Hand-rolled rather than `OnClickListener` + `OnTouchListener` together:
     * the two fight over the same stream and a drag ending on the bubble still
     * fired the click, so Scry opened every time the bubble was moved.
     */
    private fun attachDragBehaviour(
        activity: Activity,
        bubble: View,
        content: ViewGroup,
        size: Int,
        margin: Int,
    ) {
        val slop = ViewConfiguration.get(activity).scaledTouchSlop
        val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

        var downX = 0f
        var downY = 0f
        var startTranslationX = 0f
        var startTranslationY = 0f
        var downAt = 0L
        var dragging = false

        bubble.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startTranslationX = view.translationX
                    startTranslationY = view.translationY
                    downAt = System.currentTimeMillis()
                    dragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        view.translationX = startTranslationX + dx
                        view.translationY = startTranslationY + dy
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val heldMillis = System.currentTimeMillis() - downAt
                    when {
                        dragging -> {
                            val placement = resolveBubblePosition(
                                requested = BubblePosition(
                                    view.translationX / maxOf(content.width - size, 1).toFloat(),
                                    view.translationY / maxOf(content.height - size, 1).toFloat(),
                                ),
                                corner = corner,
                                parentWidth = content.width,
                                parentHeight = content.height,
                                bubbleSize = size,
                                margin = margin,
                                insets = content.systemBarInsets(),
                            )
                            view.animate()
                                .translationX(placement.x)
                                .translationY(placement.y)
                                .setDuration(SNAP_DURATION_MILLIS)
                                .start()
                            activity.savePosition(placement, content, size)
                        }

                        heldMillis >= longPressTimeout -> {
                            setVisible(false)
                            Toast.makeText(activity, LONG_PRESS_HIDE_MESSAGE, Toast.LENGTH_LONG)
                                .show()
                        }

                        else -> {
                            view.performClick()
                            Scry.show()
                        }
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun Activity.savedPosition(): BubblePosition? {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_X)) return null
        return BubblePosition(prefs.getFloat(KEY_X, 1f), prefs.getFloat(KEY_Y, 1f))
    }

    /**
     * Stores the position as a fraction of the content area, not as pixels.
     *
     * Pixels are meaningless across a rotation or a second device: a bubble
     * saved at the right edge in landscape came back hanging off the screen in
     * portrait.
     */
    private fun Activity.savePosition(placement: Placement, content: ViewGroup, size: Int) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_X, placement.x / maxOf(content.width - size, 1).toFloat())
            .putFloat(KEY_Y, placement.y / maxOf(content.height - size, 1).toFloat())
            .apply()
    }

    /** The window's system-bar insets, or none when the window has no insets yet. */
    private fun View.systemBarInsets(): SystemInsets {
        val windowInsets = rootWindowInsets ?: return SystemInsets.NONE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bars = windowInsets.getInsets(WindowInsets.Type.systemBars())
            SystemInsets(bars.left, bars.top, bars.right, bars.bottom)
        } else {
            @Suppress("DEPRECATION")
            SystemInsets(
                left = windowInsets.systemWindowInsetLeft,
                top = windowInsets.systemWindowInsetTop,
                right = windowInsets.systemWindowInsetRight,
                bottom = windowInsets.systemWindowInsetBottom,
            )
        }
    }

    private fun Context.dp(value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        resources.displayMetrics,
    ).roundToInt()

    private const val SNAP_DURATION_MILLIS = 150L
    private val BUBBLE_COLOR = Color.rgb(0x4F, 0x6B, 0xED)
}

/** A bubble position as a fraction (0..1) of the available content area. */
internal data class BubblePosition(val xFraction: Float, val yFraction: Float)

/** A bubble position in pixels, relative to the content view's top-left. */
internal data class Placement(val x: Float, val y: Float)

/** System-bar insets of the window the bubble is drawn into. */
internal data class SystemInsets(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
) {
    companion object {
        val NONE: SystemInsets = SystemInsets()
    }
}

/**
 * Resolves where the bubble goes: snapped to the nearer side, clear of the bars.
 *
 * Insets matter more here than they look. Modern apps draw edge to edge, so the
 * content view extends under the status and navigation bars — place the bubble
 * by content height alone and it lands on the gesture handle, where every tap
 * goes to the system instead.
 *
 * Pure, and separated from the view code so the maths that decides whether the
 * bubble is reachable at all can be tested on the host JVM.
 */
internal fun resolveBubblePosition(
    requested: BubblePosition?,
    corner: BubbleCorner,
    parentWidth: Int,
    parentHeight: Int,
    bubbleSize: Int,
    margin: Int,
    insets: SystemInsets = SystemInsets.NONE,
): Placement {
    val maxX = (parentWidth - bubbleSize).coerceAtLeast(0)
    val maxY = (parentHeight - bubbleSize).coerceAtLeast(0)

    val fallback = BubblePosition(
        xFraction = if (corner == BubbleCorner.TOP_START || corner == BubbleCorner.BOTTOM_START) {
            0f
        } else {
            1f
        },
        yFraction = if (corner == BubbleCorner.TOP_START || corner == BubbleCorner.TOP_END) 0f else 1f,
    )
    val position = requested ?: fallback

    // Bounds are collapsed to the midpoint when the bars leave less room than
    // the bubble needs — a window that small has no good answer, and an
    // on-screen compromise beats a crash or a bubble parked off the edge.
    val (minX, maxAllowedX) = bounds(insets.left + margin, maxX - insets.right - margin, maxX)
    val (minY, maxAllowedY) = bounds(insets.top + margin, maxY - insets.bottom - margin, maxY)

    // Snap horizontally: a bubble parked mid-screen covers content wherever it
    // is, and every half-finished drag would leave one there.
    val x = if (position.xFraction <= 0.5f) minX else maxAllowedX
    val y = (position.yFraction * maxY).coerceIn(minY.toFloat(), maxAllowedY.toFloat())
    return Placement(x.toFloat(), y)
}

private fun bounds(low: Int, high: Int, axisMax: Int): Pair<Int, Int> {
    val clampedLow = low.coerceIn(0, axisMax)
    val clampedHigh = high.coerceIn(0, axisMax)
    if (clampedLow <= clampedHigh) return clampedLow to clampedHigh
    val midpoint = (clampedLow + clampedHigh) / 2
    return midpoint to midpoint
}
