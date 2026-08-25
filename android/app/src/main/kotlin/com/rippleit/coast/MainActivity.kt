package com.rippleit.coast

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.text.TextUtils
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream

class MainActivity : FlutterActivity() {

    private val channelName = "coast/control"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "isServiceEnabled" -> result.success(isAccessibilityServiceEnabled())

                    "openAccessibilitySettings" -> {
                        startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        result.success(true)
                    }

                    "setAutoScroll" -> {
                        ScrollService.autoScrollEnabled =
                            call.argument<Boolean>("enabled") ?: false
                        result.success(ScrollService.autoScrollEnabled)
                    }

                    "setInterval" -> {
                        val seconds = call.argument<Int>("seconds") ?: 8
                        ScrollService.fixedIntervalMs = seconds * 1000L
                        result.success(seconds)
                    }

                    "setSmartTiming" -> {
                        ScrollService.smartTiming =
                            call.argument<Boolean>("enabled") ?: true
                        result.success(ScrollService.smartTiming)
                    }

                    "advance" -> {
                        val forward = call.argument<Boolean>("forward") ?: true
                        result.success(
                            ScrollService.instance?.manualAdvance(forward) ?: false
                        )
                    }

                    "getSupportedApps" -> result.success(supportedApps())

                    "openApp" -> {
                        val pkg = call.argument<String>("package")
                        val intent = pkg?.let {
                            packageManager.getLaunchIntentForPackage(it)
                        }
                        if (intent != null) {
                            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            result.success(true)
                        } else {
                            result.success(false)
                        }
                    }

                    "getStatus" -> result.success(status())

                    else -> result.notImplemented()
                }
            }
    }

    private fun status(): Map<String, Any?> {
        val svc = ScrollService.instance
        return mapOf(
            "serviceEnabled" to isAccessibilityServiceEnabled(),
            "serviceBound" to (svc != null),
            "feed" to ScrollService.currentFeed.name,
            "feedLabel" to ScrollService.currentFeed.label,
            "onFeed" to ScrollService.currentFeed.isFeed,
            "supportsSmartTiming" to
                (ScrollService.currentFeed == Feed.YOUTUBE_SHORTS),
            "autoScroll" to ScrollService.autoScrollEnabled,
            "smartTiming" to ScrollService.smartTiming,
            "intervalSeconds" to (ScrollService.fixedIntervalMs / 1000).toInt(),
            "advances" to ScrollService.advanceCount,
            "lastDurationSeconds" to (ScrollService.lastDurationMs / 1000).toInt(),
            "secondsUntilNext" to (svc?.secondsUntilNext() ?: -1)
        )
    }

    /**
     * The apps Coast can drive, with their real launcher icons read from the
     * system. Reading them at runtime avoids shipping third-party trademarks
     * inside the APK, and lets uninstalled apps be hidden rather than shown as
     * dead entries.
     */
    private fun supportedApps(): List<Map<String, Any?>> {
        val wanted = listOf(
            FeedDetector.PKG_YOUTUBE to "YouTube",
            FeedDetector.PKG_TIKTOK to "TikTok",
            FeedDetector.PKG_INSTAGRAM to "Instagram"
        )
        return wanted.mapNotNull { (pkg, fallbackLabel) ->
            try {
                val info = packageManager.getApplicationInfo(pkg, 0)
                mapOf(
                    "package" to pkg,
                    "label" to (packageManager.getApplicationLabel(info)?.toString()
                        ?: fallbackLabel),
                    "icon" to packageManager.getApplicationIcon(info).toPng(96)
                )
            } catch (_: Exception) {
                null // not installed
            }
        }
    }

    private fun Drawable.toPng(size: Int): ByteArray {
        val bmp = if (this is BitmapDrawable && bitmap != null) {
            Bitmap.createScaledBitmap(bitmap, size, size, true)
        } else {
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
                val c = Canvas(it)
                setBounds(0, 0, size, size)
                draw(c)
            }
        }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    /**
     * Settings.Secure stores enabled services as a colon-separated list of
     * flattened ComponentNames. OEMs are inconsistent about long vs short form,
     * so check both.
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val component = ComponentName(this, ScrollService::class.java)
        val long = component.flattenToString()
        val short = component.flattenToShortString()

        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            val entry = splitter.next()
            if (entry.equals(long, true) || entry.equals(short, true)) return true
        }
        return false
    }
}
