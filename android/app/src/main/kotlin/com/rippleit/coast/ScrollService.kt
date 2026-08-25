package com.rippleit.coast

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * The engine. Watches the foreground app, decides whether a short-form video
 * feed is showing, and advances it while auto-scroll is armed.
 *
 * Screen content is only ever inspected in memory to answer two questions:
 * which feed is open, and how long the current clip is. Nothing is stored,
 * logged or transmitted.
 *
 * Accessibility services are bound by the system and are exempt from the normal
 * background-execution limits, so no foreground service is required.
 */
class ScrollService : AccessibilityService() {

    companion object {
        private const val TAG = "Coast"

        private const val TICK_MS = 400L

        /** Time for a newly-swiped video to load before we measure its length. */
        private const val SETTLE_MS = 900L

        /** Added to a known duration so we advance just after the video ends. */
        private const val TAIL_PAD_MS = 350L

        private const val MIN_DELAY_MS = 2_500L
        private const val MAX_DELAY_MS = 90_000L

        private const val MAX_SEARCH_DEPTH = 18

        /** "0 minutes 3 seconds of 0 minutes 59 seconds" -> position, duration. */
        private val PROGRESS = Regex(
            """(\d+)\s+minutes?\s+(\d+)\s+seconds?\s+of\s+(\d+)\s+minutes?\s+(\d+)\s+seconds?""",
            RegexOption.IGNORE_CASE
        )

        @Volatile
        var instance: ScrollService? = null

        // --- configuration, driven from Flutter ---
        @Volatile var autoScrollEnabled = false
        @Volatile var fixedIntervalMs = 8_000L
        @Volatile var smartTiming = true

        // --- observable state ---
        @Volatile var currentFeed: Feed = Feed.NONE
        @Volatile var advanceCount = 0
        @Volatile var lastDurationMs = 0L

        /** Diagnostic tracing. Off in shipped builds; flip to debug timing. */
        @Volatile var verbose = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var engine: ScrollEngine

    /** When the current video is due to be advanced. 0 = not yet scheduled. */
    private var nextAdvanceAt = 0L

    /** Nothing happens before this: gives a freshly swiped video time to load. */
    private var settleUntil = 0L

    /** Duration of the clip currently scheduled; a change means a new video. */
    private var trackedDurationMs = 0L

    /** Last position value seen; a change means the user seeked. */
    private var lastSeenPositionMs = -1L

    private var lastTraceAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            try {
                step()
            } catch (t: Throwable) {
                Log.w(TAG, "tick failed", t)
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        engine = ScrollEngine(this)
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, TICK_MS)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        autoScrollEnabled = false
        currentFeed = Feed.NONE
        instance = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------ engine

    private fun step() {
        val root = rootInActiveWindow
        val feed = FeedDetector.detect(root)
        currentFeed = feed

        if (!autoScrollEnabled || !feed.isFeed) {
            resetTracking()
            return
        }

        val now = SystemClock.uptimeMillis()

        if (verbose && now - lastTraceAt > 500L) {
            lastTraceAt = now
            val (tp, td) = if (root != null) readYouTubeProgress(root) else 0L to 0L
            Log.i(
                TAG,
                "trace feed=${feed.name} settle=${(settleUntil - now).coerceAtLeast(0)} " +
                    "pos=$tp dur=$td tracked=$trackedDurationMs " +
                    "next=${if (nextAdvanceAt == 0L) -1 else nextAdvanceAt - now}"
            )
        }

        if (now < settleUntil) return

        if (smartTiming && feed == Feed.YOUTUBE_SHORTS && root != null &&
            trackYouTube(now, feed, root)
        ) {
            return
        }

        // Fixed interval: TikTok and Instagram publish no position at all.
        if (nextAdvanceAt == 0L) {
            nextAdvanceAt = now + fixedIntervalMs.coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)
        } else if (now >= nextAdvanceAt) {
            fire(feed, root, now)
        }
    }

    /**
     * Schedules the advance from the clip duration, and re-schedules whenever
     * the reported position *changes*.
     *
     * YouTube does not refresh the seek bar's description as the video plays --
     * measured, it sat at 0 for ten seconds, then froze at 53s for thirteen.
     * So position cannot be polled as a clock. It does refresh when the user
     * seeks, which makes a *change* in the value a reliable seek signal even
     * though the value itself is stale.
     *
     * Duration, by contrast, is accurate and stable.
     *
     * @return true when this video is being handled here.
     */
    private fun trackYouTube(
        now: Long,
        feed: Feed,
        root: AccessibilityNodeInfo
    ): Boolean {
        val (position, duration) = readYouTubeProgress(root)
        if (duration <= 0) return false

        lastDurationMs = duration

        val newVideo = duration != trackedDurationMs
        val seeked = position != lastSeenPositionMs

        if (newVideo || seeked) {
            trackedDurationMs = duration
            lastSeenPositionMs = position
            val remaining = (duration - position).coerceAtLeast(0L) + TAIL_PAD_MS
            nextAdvanceAt = now + remaining.coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)
        }

        if (nextAdvanceAt != 0L && now >= nextAdvanceAt) fire(feed, root, now)
        return true
    }

    private fun fire(feed: Feed, root: AccessibilityNodeInfo?, now: Long) {
        if (engine.advance(feed, root)) advanceCount++
        settleUntil = now + SETTLE_MS
        nextAdvanceAt = 0L
        // Force a fresh anchor: the description still holds the old video's
        // numbers for a moment after the swipe.
        trackedDurationMs = 0L
        lastSeenPositionMs = -1L
    }

    private fun resetTracking() {
        nextAdvanceAt = 0L
        settleUntil = 0L
        trackedDurationMs = 0L
        lastSeenPositionMs = -1L
    }

    /** @return position to duration, in ms; 0 to 0 when unavailable. */
    private fun readYouTubeProgress(root: AccessibilityNodeInfo): Pair<Long, Long> {
        val desc = findProgressDescription(root, 0) ?: return 0L to 0L
        val m = PROGRESS.find(desc) ?: return 0L to 0L
        val (pm, ps, dm, ds) = m.destructured
        val position = (pm.toLong() * 60 + ps.toLong()) * 1000
        val duration = (dm.toLong() * 60 + ds.toLong()) * 1000
        return position to duration
    }

    private fun findProgressDescription(node: AccessibilityNodeInfo?, depth: Int): String? {
        if (node == null || depth > MAX_SEARCH_DEPTH) return null
        val d = node.contentDescription?.toString()
        if (d != null && PROGRESS.containsMatchIn(d)) return d
        for (i in 0 until node.childCount) {
            findProgressDescription(node.getChild(i), depth + 1)?.let { return it }
        }
        return null
    }

    fun manualAdvance(forward: Boolean): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { manualAdvance(forward) }
            return true
        }
        val root = rootInActiveWindow ?: return false
        val feed = FeedDetector.detect(root)
        if (!feed.isFeed) return false
        val ok = engine.advance(feed, root, forward)
        if (ok) {
            advanceCount++
            val now = SystemClock.uptimeMillis()
            settleUntil = now + SETTLE_MS
            nextAdvanceAt = 0L
            trackedDurationMs = 0L
            lastSeenPositionMs = -1L
        }
        return ok
    }

    fun secondsUntilNext(): Int {
        if (nextAdvanceAt == 0L) return -1
        val remaining = nextAdvanceAt - SystemClock.uptimeMillis()
        return if (remaining <= 0) 0 else ((remaining + 999) / 1000).toInt()
    }
}
