package com.rippleit.coast

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Debug-only diagnostic. Not compiled into release builds.
 *
 * Dumps the accessibility node tree of whatever app is in the foreground so the
 * signatures in docs/feed-signatures.md can be re-derived. YouTube, TikTok and
 * Instagram reshuffle and re-obfuscate their view IDs between releases, so
 * detection *will* break eventually and this is how it gets fixed.
 *
 * Usage:
 *   flutter build apk --debug && adb install -r <apk>
 *   adb shell settings put secure enabled_accessibility_services \
 *     com.rippleit.coast/com.rippleit.coast.FeedProbeService
 *   adb shell settings put secure accessibility_enabled 1
 *   adb logcat -v time FeedProbe:I '*:S' > captures/session.log
 *
 * Line formats (pipe-delimited, one node per line so logcat cannot truncate):
 *   PROBE|EVT|<eventName>|<pkg>|<className>
 *   PROBE|W|<seq>|<reason>|<pkg>|BEGIN
 *   PROBE|N|<seq>|<depth>|<viewId>|<class>|<desc>|<text>|<flags>|<l,t,r,b>
 *   PROBE|SCROLL|<seq>|<depth>|<viewId>|<class>|<l,t,r,b>
 *   PROBE|SIG|<seq>|<pkg>|<chunk>|<id,id,...>
 *   PROBE|W|<seq>|<reason>|<pkg>|END|<nodeCount>
 *
 * flags: S=scrollable C=clickable V=visible K=checkable E=editable
 *        X=selected H=checked F=focused
 *
 * The selected flag matters: TikTok's view IDs are minified and unusable, so
 * the bottom-nav item carrying isSelected is what separates For You from
 * Friends.
 */
class FeedProbeService : AccessibilityService() {

    companion object {
        private const val TAG = "FeedProbe"
        private const val PERIODIC_MS = 1_500L
        private const val MIN_DUMP_GAP_MS = 600L
        private const val MAX_DEPTH = 18
        private const val MAX_NODES = 600
        private const val TEXT_CAP = 48
        private const val SIG_CHUNK = 12

        /** Packages that are pure noise while probing. */
        private val DENY_PREFIXES = listOf(
            "com.android.systemui", "com.android.launcher",
            "com.google.android.apps.nexuslauncher", "com.miui.home",
            "com.sec.android.app.launcher", "com.oppo.launcher",
            "com.android.inputmethod", "com.google.android.inputmethod",
            "com.touchtype.swiftkey", "com.samsung.android.honeyboard",
            "com.android.settings", "com.rippleit.coast"
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private var seq = 0
    private var lastDumpAt = 0L
    private var nodesThisDump = 0

    private val periodic = object : Runnable {
        override fun run() {
            maybeDump("periodic")
            handler.postDelayed(this, PERIODIC_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "PROBE|SVC|connected")
        handler.removeCallbacks(periodic)
        handler.postDelayed(periodic, PERIODIC_MS)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Log every package, even denied ones -- this is how the actual TikTok
        // build gets identified (musically / trill / aweme all exist).
        Log.i(TAG, "PROBE|EVT|windowState|$pkg|${clean(event.className?.toString())}")
        maybeDump("windowState")
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(periodic)
        super.onDestroy()
    }

    private fun maybeDump(reason: String) {
        val now = SystemClock.uptimeMillis()
        if (now - lastDumpAt < MIN_DUMP_GAP_MS) return
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: "?"
        if (DENY_PREFIXES.any { pkg.startsWith(it) }) return

        lastDumpAt = now
        seq++
        nodesThisDump = 0
        val ids = LinkedHashSet<String>()

        Log.i(TAG, "PROBE|W|$seq|$reason|$pkg|BEGIN")
        walk(root, 0, ids)

        val list = ids.toList()
        var i = 0
        var chunk = 0
        while (i < list.size) {
            Log.i(
                TAG, "PROBE|SIG|$seq|$pkg|$chunk|" +
                    list.subList(i, minOf(i + SIG_CHUNK, list.size)).joinToString(",")
            )
            i += SIG_CHUNK
            chunk++
        }
        Log.i(TAG, "PROBE|W|$seq|$reason|$pkg|END|$nodesThisDump")
    }

    private fun walk(node: AccessibilityNodeInfo?, depth: Int, ids: MutableSet<String>) {
        if (node == null || nodesThisDump >= MAX_NODES || depth > MAX_DEPTH) return
        nodesThisDump++

        val vid = node.viewIdResourceName ?: ""
        if (vid.isNotEmpty()) ids.add(vid.substringAfterLast('/'))

        val flags = buildString {
            if (node.isScrollable) append('S')
            if (node.isClickable) append('C')
            if (node.isVisibleToUser) append('V')
            if (node.isCheckable) append('K')
            if (node.isEditable) append('E')
            if (node.isSelected) append('X')
            if (node.isChecked) append('H')
            if (node.isFocused) append('F')
        }

        val r = Rect()
        node.getBoundsInScreen(r)
        val cls = clean(node.className?.toString())
        val bounds = "${r.left},${r.top},${r.right},${r.bottom}"

        Log.i(
            TAG, "PROBE|N|$seq|$depth|${clean(vid)}|$cls|" +
                "${clean(node.contentDescription?.toString())}|${clean(node.text?.toString())}|" +
                "$flags|$bounds"
        )

        if (node.isScrollable) {
            Log.i(TAG, "PROBE|SCROLL|$seq|$depth|${clean(vid)}|$cls|$bounds")
        }

        for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1, ids)
    }

    /** Keeps the delimiter intact and stops personal text flooding the log. */
    private fun clean(s: String?): String {
        if (s.isNullOrEmpty()) return ""
        val t = s.replace('|', '/').replace('\n', ' ').replace('\r', ' ').trim()
        return if (t.length > TEXT_CAP) t.substring(0, TEXT_CAP) + "~" else t
    }
}
