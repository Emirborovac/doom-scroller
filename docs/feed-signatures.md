# Feed detection signatures

Empirical, from a real capture. Do not trust these blindly on other builds —
re-run the probe when target apps update.

**Capture device:** Samsung SM-A566B (Galaxy A56), Android 16 / SDK 36, 1080x2340
**App versions:** YouTube 21.32.4 · Instagram 443.0.0.48.82 · TikTok 46.4.3 (`com.zhiliaoapp.musically`)
**Source:** `captures/session1.log` (141 dumps), `captures/tiktok.log` (49 dumps, TikTok logged in)

---

## YouTube Shorts — CONFIRMED

Package `com.google.android.youtube`. Shorts is a fragment inside the same
activity as Home, so the package tells you nothing. The `reel_*` view IDs do.

**Signature:** presence of `reel_watch_fragment_root`

Corroborating IDs, all appearing together: `reel_watch_player`,
`reel_watch_refresher`, `reel_player_underlay`, `reel_time_bar`,
`reel_scrim_shorts_while_top`.

Separation was total: present in 14/14 Shorts dumps, absent in 6/6 Home dumps.

**Scrolling: no scrollable node exists on the Shorts feed.** Zero `SCROLL`
lines across all 14 dumps. `ACTION_SCROLL_FORWARD` is not an option here —
`dispatchGesture()` is mandatory.

Swipe geometry — `reel_watch_player` occupies `0,101 → 1080,2070`:
- next video: x=540, y 1600 → 700
- previous:   x=540, y 700 → 1600

**Bonus: playback duration is readable.** An unlabelled `android.widget.SeekBar`
carries a contentDescription of the form
`"0 minutes 3 seconds of 0 minutes 59 seconds"`. Duration is reliable; position
updates lag by a second or two, so treat it as coarse. Enough to time the
auto-advance to the actual end of a video rather than a blind fixed interval.

## Instagram Reels — CONFIRMED

Package `com.instagram.android`. Reels is a surface inside the main activity.

**Signature:** presence of `clips_viewer_container`

Corroborating: `root_clips_layout`, `clips_swipe_refresh_container`,
`clips_linear_layout_container`, `clips_viewer_action_bar`.

Present in 48/48 Reels dumps, absent in all 51 other Instagram dumps.

**Scrolling:** a real scrollable exists —
`swipeable_nav_view_pager_inner_recycler_view`
(`androidx.recyclerview.widget.RecyclerView`). `ACTION_SCROLL_FORWARD` is worth
trying first, with a `dispatchGesture()` swipe as fallback.

**No metadata whatsoever.** Zero nodes carry text or contentDescription on the
Reels feed. No duration, no progress, no play state. Fixed-interval advance is
the only option.

## TikTok — CONFIRMED

Package `com.zhiliaoapp.musically`. Captured logged in, with Profile / Inbox /
Friends / Search visited for contrast (`captures/tiktok.log`, 49 dumps).

**Signature:** presence of `viewpager_container`

Separation was total across 49 dumps: present on every Home (For You) and
Friends dump, absent on every Profile, Inbox, profile-sub-tab and Search dump.
Both Home and Friends are vertical video feeds, so this ID means "a swipeable
video feed is on screen" — which is exactly the condition we want.

**Distinguishing For You from Friends:** read the bottom-nav item whose node has
`isSelected == true` and take its `contentDescription` — `Home`, `Friends`,
`Create`, `Inbox`, `Profile`. This was 100% reliable. Logged-out builds show
`Discover` instead of `Friends`/`Search`, so do not hard-code the full set.

**Do not use `video_seek_bar` for detection.** It is present in only ~70% of feed
dumps — the seek bar auto-hides during playback (absent in seq 16, 38, 40, 47,
48 while still on the feed). It is also an empty `LinearLayout`: no
contentDescription, no text, so it carries no progress information.

**Scrolling:** a scrollable exists at `com.zhiliaoapp.musically:id/viewpager`.
Two are nested — outer for tab switching, inner for the vertical feed. Its class
is `X.145N`, which is **obfuscated and will change every release — match on the
ID, never the class.**

**No metadata.** Like Instagram, TikTok exposes no duration or playback position.
Fixed-interval advance only.

**Warning on obfuscated IDs:** most TikTok view IDs are minified (`a2y`, `yp3`,
`xb2`, `o74`) and will break on every app update. The only IDs that survived
minification, and therefore the only ones safe to key on, are: `viewpager`,
`viewpager_container`, `tabcontent`, `title_shadow`, `video_seek_bar`.

---

## Implementation notes

- Detection must run on the node tree, not on package name, for all three apps.
- Match view IDs on the **suffix after `/`** — the probe already strips the
  package prefix when building signatures.
- Treat every signature as version-fragile. Ship a fallback: if no known
  signature matches but the foreground package is a target, degrade to
  package-level detection rather than doing nothing.
