# Auto-Scroller — logo brief

For: graphics agent / designer
Client: Rippleit LLC
Product: **Auto-Scroller** — an Android app that scrolls short-form video feeds
(YouTube Shorts, TikTok, Instagram Reels) hands-free, so the phone can sit on a
desk and advance videos on its own.

## The idea in one line

Something moving downward, on its own.

The mark should read as *a feed advancing by itself*. Two ideas have to
coexist: **vertical feed** (stacked content) and **automatic motion** (downward,
continuous, effortless).

## Style

Flat, minimal, modern. Specifically:

- **No gradients, no shadows, no bevels, no 3D.** Solid fills only.
- **Geometric.** Built from rounded rectangles, circles and straight lines on a
  consistent grid. Nothing hand-drawn or organic.
- **Generous corner radii.** Soft, not sharp — friendly rather than technical.
- **One accent colour plus one neutral.** No more.
- **No literal phone outline.** Phone-in-a-logo is the obvious move and it looks
  dated and generic at small sizes.
- **No text in the icon.** The wordmark is separate.

Reference feel: Linear, Arc, Raycast, Things 3. Confident and quiet, not loud.

## Primary concept — "the descending stack"

Three horizontal rounded bars, stacked vertically, **decreasing in width from
top to bottom** so the silhouette forms an implied downward arrow without
drawing one.

- Top bar: full width, full opacity accent
- Middle bar: ~70% width, centred, full opacity accent
- Bottom bar: ~40% width, centred, full opacity accent

Reads simultaneously as a content feed (stacked rows) and as downward motion
(the taper points down). It's a single shape language, it scales to 24px, and
it is not a phone.

Optional refinement: give the bottom bar a slightly rounder, more pill-like
shape so it reads as the "leading edge" of the motion.

## Alternate A — "the loop arrow"

A near-complete circle with a gap at the top, terminating in a downward-pointing
triangular arrowhead at the bottom of the stroke. Suggests continuous automatic
cycling. Keep the stroke weight heavy and uniform; keep the arrowhead simple and
geometric, not a thin chevron.

Risk: circular-arrow marks read as "refresh/sync" and are very common. Only
pursue if the descending stack doesn't land.

## Alternate B — "the double chevron"

Two chevrons pointing down, stacked, the lower one smaller and lighter in
weight. Fast-forward rotated 90°. Very legible at small sizes, very simple.

Risk: reads as "download" or "scroll down" button. Needs distinctive proportions
and colour to avoid feeling like a UI control rather than a brand.

**Recommendation: build the descending stack first.** It's the most ownable, the
least likely to collide with an existing mark, and it survives shrinking better
than either alternate.

## Colour

| Role | Hex | Use |
|---|---|---|
| Accent | `#6C5CE7` | the mark itself |
| Accent light | `#A29BFE` | optional secondary bar, sparingly |
| Dark ground | `#0B0B10` | dark backgrounds |
| Light ground | `#F6F6F9` | light backgrounds |
| Pure white | `#FFFFFF` | mark on accent-filled backgrounds |

These match the app's in-product palette exactly, so keep them precise.

Required variants:
1. Accent mark on dark ground
2. Accent mark on light ground
3. White mark on accent ground ← **this is the launcher icon**
4. Single-colour black, and single-colour white (for stencils, press, watermarks)

## Android launcher icon requirements

This is the part most easily got wrong, so please follow it exactly.

- **Adaptive icon, two layers.** Foreground and background delivered as
  *separate* files. Do not deliver a single flattened square.
- **Canvas 108 × 108 dp.** The outer 18dp on every side is reserved for system
  masking and parallax — **nothing meaningful may enter it.**
- **Safe zone is the centre 66 dp circle.** The entire mark must sit inside it.
  OEMs mask to circles, squircles, rounded squares and teardrops; anything
  outside that circle can be cut off.
- **Background layer:** flat `#6C5CE7`, edge to edge, no shape of its own.
- **Foreground layer:** the mark in `#FFFFFF`, transparent elsewhere.
- Also supply a **512 × 512 px PNG**, 32-bit, no alpha, for the Play Store
  listing. This one is *not* masked, so it needs the mark on the accent ground
  with its own comfortable padding.

## Wordmark

- Set "Auto-Scroller" in a geometric sans — Inter, Satoshi, General Sans or
  similar. Weight 600–700.
- Tight tracking, around `-0.02em`.
- Keep the hyphen. The product name is hyphenated.
- Supply lockups: mark-above-text (vertical) and mark-left-of-text
  (horizontal), with the clear space rule defined as the height of one bar of
  the mark.

## Deliverables

- Source vector (Figma or `.ai`), with the grid and construction preserved
- `SVG` of the mark: all five colour variants
- Adaptive icon layers: `ic_launcher_foreground.svg` + background colour value
- `PNG` exports at 48, 72, 96, 144, 192, 512 px
- Play Store icon: 512 × 512 PNG, no alpha
- Horizontal and vertical lockups, SVG
- A one-page sheet showing the mark at 24px, 48px and 512px side by side

## Acceptance test

The mark passes if, at **24 pixels**, it still reads as *something moving
downward* and not as a smudge, a hamburger menu, or a download arrow. Anything
that only works large has failed.
