package com.rippleit.coast

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Advances a feed by one video, always via a synthetic swipe.
 *
 * ACTION_SCROLL_FORWARD is deliberately NOT used. Both Instagram and TikTok
 * expose their *navigation* pager as the nearest scrollable node --
 * `swipeable_nav_view_pager_inner_recycler_view` on Instagram, the outer
 * `id/viewpager` on TikTok -- so scrolling it switches bottom-nav tabs instead
 * of changing video. A swipe inside the video area is unambiguous, and it is
 * what already works on YouTube Shorts.
 */
class ScrollEngine(private val service: AccessibilityService) {

    companion object {
        private const val TAG = "Coast"

        /** Long enough to read as a deliberate swipe, short enough not to drag. */
        private const val SWIPE_MS = 220L

        /** Fraction of the usable height the swipe travels. */
        private const val TRAVEL = 0.45f

        /**
         * Android reserves the screen edges for system navigation: swiping up
         * from the bottom is Home, from the sides is Back. Staying well clear
         * stops our swipe being stolen by the system.
         */
        private const val BOTTOM_SAFE_PX = 260
        private const val TOP_SAFE_PX = 260

        /** Two swipes closer together than this are almost certainly a glitch. */
        private const val MIN_GAP_MS = 450L
    }

    private var lastSwipeAt = 0L

    /** @return true if the advance was dispatched. */
    fun advance(feed: Feed, root: AccessibilityNodeInfo?, forward: Boolean = true): Boolean {
        if (root == null || !feed.isFeed) return false

        val now = SystemClock.uptimeMillis()
        if (now - lastSwipeAt < MIN_GAP_MS) {
            Log.d(TAG, "advance suppressed, too soon")
            return false
        }

        val targetId = when (feed) {
            Feed.YOUTUBE_SHORTS -> FeedDetector.ID_YT_PLAYER
            Feed.INSTAGRAM_REELS -> FeedDetector.ID_IG_CLIPS
            Feed.TIKTOK_FOR_YOU, Feed.TIKTOK_FRIENDS -> FeedDetector.ID_TT_PAGER_CONTAINER
            Feed.NONE -> return false
        }

        val area = Rect()
        FeedDetector.firstById(root, targetId)?.getBoundsInScreen(area)
        if (area.width() <= 0 || area.height() <= 0) root.getBoundsInScreen(area)
        if (area.width() <= 0 || area.height() <= 0) return false

        val screen = Rect()
        root.getBoundsInScreen(screen)

        lastSwipeAt = now
        return swipe(area, screen, forward)
    }

    private fun swipe(area: Rect, screen: Rect, forward: Boolean): Boolean {
        val x = area.exactCenterX()

        // Confine the stroke to the middle band, away from the system gesture
        // zones at the top and bottom of the display.
        val lowest = minOf(area.bottom, screen.bottom - BOTTOM_SAFE_PX).toFloat()
        val highest = maxOf(area.top, screen.top + TOP_SAFE_PX).toFloat()
        if (lowest - highest < 200f) {
            Log.w(TAG, "swipe band too small: $highest..$lowest area=$area")
            return false
        }

        val mid = (highest + lowest) / 2f
        val half = (lowest - highest) * TRAVEL / 2f

        // Swiping up advances to the next video.
        val startY = if (forward) mid + half else mid - half
        val endY = if (forward) mid - half else mid + half

        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, SWIPE_MS))
            .build()

        val dispatched = service.dispatchGesture(gesture, null, null)
        Log.d(
            TAG,
            "swipe x=${x.toInt()} ${startY.toInt()}->${endY.toInt()} area=$area ok=$dispatched"
        )
        return dispatched
    }
}
