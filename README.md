# Coast

Hands-free scrolling for short-form video feeds on Android. Open YouTube Shorts,
TikTok or Instagram Reels, press Start, and each video advances on its own.

Flutter shell, Kotlin `AccessibilityService` underneath. Android only —
iOS has no equivalent of gesture injection, so it is not portable.

## How it works

| App | Detection signature | Timing |
|---|---|---|
| YouTube Shorts | `reel_watch_fragment_root` | reads the real clip duration and advances when it ends |
| Instagram Reels | `clips_viewer_container` | fixed interval |
| TikTok | `viewpager_container` + selected nav tab | fixed interval |

Package name is never enough — Shorts, Reels and the TikTok feed all share an
activity with the rest of their app, so detection runs on the accessibility node
tree. Signatures were derived empirically; see `docs/feed-signatures.md`.

Scrolling is always a synthetic swipe via `dispatchGesture`. `ACTION_SCROLL_FORWARD`
is deliberately unused: on Instagram and TikTok the nearest scrollable node is the
*navigation* pager, so scrolling it switches bottom-nav tabs instead of changing
video.

Only YouTube publishes a clip duration (through its seek bar's content
description). TikTok and Instagram expose no duration through any channel —
not MediaSession, not node text, not node geometry — which is why they run on an
interval.

## Build

```bash
flutter pub get
flutter build apk --debug       # development
flutter build appbundle --release
```

Release signing needs `android/key.properties`; see `docs/release-checklist.md`.

## Development

Enable the accessibility service:

```bash
adb shell settings put secure enabled_accessibility_services com.rippleit.coast/com.rippleit.coast.ScrollService
adb shell settings put secure accessibility_enabled 1
```

Reinstalling **revokes** this every time — re-run after each install.

### Re-deriving feed signatures

The target apps reshuffle and obfuscate their view IDs between releases, so
detection will eventually break. Debug builds include `FeedProbeService`, which
dumps the foreground view tree to logcat. It is excluded from release builds.

```bash
adb shell settings put secure enabled_accessibility_services com.rippleit.coast/com.rippleit.coast.FeedProbeService
adb shell settings put secure accessibility_enabled 1
./tools/capture.sh shorts
```

Then drive the phone by hand and diff the signatures between the feed and
non-feed screens of the same app. `docs/feed-signatures.md` documents the format
and what was found last time.

## Docs

- `docs/feed-signatures.md` — how each feed is detected, with the evidence
- `docs/release-checklist.md` — Play Store requirements and the accessibility-policy risk
- `docs/logo-brief.md` — logo specification for the designer
