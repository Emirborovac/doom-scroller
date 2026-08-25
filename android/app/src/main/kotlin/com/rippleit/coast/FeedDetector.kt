package com.rippleit.coast

import android.view.accessibility.AccessibilityNodeInfo

/** A short-form video surface we know how to drive. */
enum class Feed(val label: String) {
    YOUTUBE_SHORTS("YouTube Shorts"),
    INSTAGRAM_REELS("Instagram Reels"),
    TIKTOK_FOR_YOU("TikTok · For You"),
    TIKTOK_FRIENDS("TikTok · Friends"),
    NONE("Not on a feed");

    val isFeed: Boolean get() = this != NONE
}

/**
 * Maps the foreground node tree to a Feed.
 *
 * Signatures are empirical -- see docs/feed-signatures.md. Package name alone is
 * never sufficient: Shorts, Reels and the TikTok feed all share an activity with
 * the rest of their app.
 */
object FeedDetector {

    const val PKG_YOUTUBE = "com.google.android.youtube"
    const val PKG_INSTAGRAM = "com.instagram.android"
    const val PKG_TIKTOK = "com.zhiliaoapp.musically"

    val SUPPORTED_PACKAGES = setOf(PKG_YOUTUBE, PKG_INSTAGRAM, PKG_TIKTOK)

    // Detection signatures.
    const val ID_YT_SHORTS = "$PKG_YOUTUBE:id/reel_watch_fragment_root"
    const val ID_IG_CLIPS = "$PKG_INSTAGRAM:id/clips_viewer_container"
    const val ID_TT_PAGER_CONTAINER = "$PKG_TIKTOK:id/viewpager_container"

    // Scroll targets.
    const val ID_YT_PLAYER = "$PKG_YOUTUBE:id/reel_watch_player"
    const val ID_IG_RECYCLER = "$PKG_INSTAGRAM:id/swipeable_nav_view_pager_inner_recycler_view"
    const val ID_TT_PAGER = "$PKG_TIKTOK:id/viewpager"

    private const val MAX_SELECTED_SEARCH_DEPTH = 20

    fun detect(root: AccessibilityNodeInfo?): Feed {
        if (root == null) return Feed.NONE
        return when (root.packageName?.toString()) {
            PKG_YOUTUBE -> if (has(root, ID_YT_SHORTS)) Feed.YOUTUBE_SHORTS else Feed.NONE

            PKG_INSTAGRAM -> if (has(root, ID_IG_CLIPS)) Feed.INSTAGRAM_REELS else Feed.NONE

            // viewpager_container is present on both video feeds (For You and
            // Friends) and on nothing else -- Profile, Inbox and Search lack it.
            PKG_TIKTOK -> if (has(root, ID_TT_PAGER_CONTAINER)) {
                if (selectedTabLabel(root) == "Friends") Feed.TIKTOK_FRIENDS
                else Feed.TIKTOK_FOR_YOU
            } else Feed.NONE

            else -> Feed.NONE
        }
    }

    fun has(root: AccessibilityNodeInfo, viewId: String): Boolean =
        try {
            root.findAccessibilityNodeInfosByViewId(viewId).isNotEmpty()
        } catch (_: Exception) {
            false
        }

    fun firstById(root: AccessibilityNodeInfo, viewId: String): AccessibilityNodeInfo? =
        try {
            root.findAccessibilityNodeInfosByViewId(viewId).firstOrNull()
        } catch (_: Exception) {
            null
        }

    /** Deepest match wins -- TikTok nests two views sharing id/viewpager, and the
     *  inner one is the vertical video pager. */
    fun lastById(root: AccessibilityNodeInfo, viewId: String): AccessibilityNodeInfo? =
        try {
            root.findAccessibilityNodeInfosByViewId(viewId).lastOrNull()
        } catch (_: Exception) {
            null
        }

    /**
     * contentDescription of the bottom-nav item currently marked selected.
     * TikTok's view IDs are minified and change every release; the nav labels are
     * not, so this is the durable way to tell For You from Friends.
     */
    fun selectedTabLabel(root: AccessibilityNodeInfo): String? =
        findSelected(root, 0)?.contentDescription?.toString()

    private fun findSelected(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > MAX_SELECTED_SEARCH_DEPTH) return null
        if (node.isSelected && !node.contentDescription.isNullOrEmpty()) return node
        for (i in 0 until node.childCount) {
            findSelected(node.getChild(i), depth + 1)?.let { return it }
        }
        return null
    }
}
