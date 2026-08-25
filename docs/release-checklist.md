# Coast — Play Store release checklist

Publisher: **Rippleit LLC**
Package: `com.rippleit.coast` — **permanent once published, cannot be changed**
Version: `1.0.0+1` (set in `pubspec.yaml`)

---

## 1. Signing key — do this first

The release bundle currently falls back to **debug signing**, which Play will
reject. You need an upload key, and it must be created by you: I will not
generate or hold your signing credentials.

```bash
keytool -genkey -v -keystore upload-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

Store the `.jks` somewhere backed up and outside the repo. Then create
`android/key.properties`:

```
storePassword=<the password you chose>
keyPassword=<the key password you chose>
keyAlias=upload
storeFile=<absolute path to upload-keystore.jks>
```

`key.properties`, `*.jks` and `*.keystore` are already git-ignored.

> **Losing this key means losing the ability to update the app**, unless Play App
> Signing is enrolled — enrol during the first upload, it lets Google recover an
> upload key. Do it.

Then:

```bash
flutter build appbundle --release
```

Upload `build/app/outputs/bundle/release/app-release.aab`.

## 2. The accessibility declaration — the real risk

Coast depends on `AccessibilityService`. Google restricts that API
tightly and this is by far the most likely reason for rejection. Be deliberate
about it.

**What Play requires:**

- A **Permissions Declaration Form** in the Play Console explaining why the app
  needs accessibility access and why no other API achieves it. (True here:
  injecting a swipe into another app has no alternative API.)
- **Prominent in-app disclosure** before the permission is requested. The setup
  card already does this — it states what the access is used for and that
  nothing leaves the device. Do not remove it.
- A **privacy policy** covering the accessibility usage specifically.

**Positioning matters more than wording.** Two framings are available and they
carry very different odds:

| Framing | Store listing leads with | Risk |
|---|---|---|
| **Assistive** (recommended) | hands-free control for users who find repeated swiping difficult or painful | Defensible under policy; accessibility is the stated purpose |
| **Convenience** | "watch TikTok without touching your phone" | Reads as non-accessibility use of an accessibility API — the exact pattern Play removes apps for |

Pick one and be consistent across the listing, the declaration form, and the
in-app copy. Inconsistency between them is what gets flagged.

**On `android:isAccessibilityTool="true"`:** only set it if you genuinely commit
to the assistive framing throughout. Declaring it while marketing the app as a
doomscrolling convenience is a misrepresentation and a worse outcome than not
declaring it. It is deliberately **not** set in the manifest right now — that is
a decision for you to make, not a default.

Budget for at least one rejection and appeal. Apps in this category commonly get
one.

## 3. Data safety form

Answers, all verifiable against the code:

- **Data collected:** none
- **Data shared:** none
- **Data encrypted in transit:** N/A — no network calls are made
- **Deletion request mechanism:** N/A — nothing is stored

The app has no INTERNET permission in release builds, no analytics SDK, and no
backend. Screen content is read into memory only to answer "which feed is this"
and "how long is this clip", and is never persisted or transmitted.

## 4. Privacy policy

Required, and must be a public URL. Host it on the Rippleit site. It has to
state plainly:

- accessibility access is used to detect the active feed and to perform scroll
  gestures
- screen content is processed on-device, in memory, and is never stored,
  logged, or transmitted
- no personal data is collected, and no third parties receive anything
- contact details for Rippleit LLC

## 5. Store listing assets

| Asset | Spec | Status |
|---|---|---|
| App icon | 512 × 512 PNG, no alpha | see `docs/logo-brief.md` |
| Feature graphic | 1024 × 500 PNG/JPG | needed |
| Phone screenshots | 2–8, min 1080px on the short edge | needed |
| Short description | ≤ 80 characters | draft below |
| Full description | ≤ 4000 characters | draft below |

**Short description (assistive framing):**

> Hands-free scrolling for short-video feeds. Set it once, watch without
> touching your phone.

**Full description opening:**

> Coast advances short-form video feeds for you, so you never have to
> reach for your phone. Open YouTube Shorts, TikTok or Instagram Reels, press
> Start, and each video moves on by itself.
>
> Built for anyone who finds repeated swiping awkward, uncomfortable or simply
> unnecessary — whether your hands are busy, your phone is propped across the
> room, or repetitive gestures are painful.
>
> On YouTube Shorts it reads the actual clip length and advances exactly when
> the video ends. On TikTok and Instagram it advances on an interval you choose,
> from 3 to 60 seconds.
>
> Everything runs on your device. No account, no internet connection, no data
> collected, no screen content stored or sent anywhere.

Screenshots to capture: the setup screen, the idle state, an active state with
a feed detected and a countdown, and the interval control.

## 6. Console configuration

- **Category:** Tools
- **Content rating:** complete the questionnaire — expect Everyone
- **Target audience:** 18+ (avoids the child-safety obligations, and matches the
  fact that the app drives social feeds)
- **Ads:** none
- **In-app purchases:** none
- **Government app:** no

Organisation developer accounts are exempt from the 12-tester / 14-day closed
testing requirement that applies to new personal accounts. Confirm the Rippleit
LLC account is registered as an organisation.

## 7. Pre-upload verification

```bash
flutter analyze
flutter build appbundle --release
```

Then confirm on the built bundle:

- release manifest contains `ScrollService` and **not** `FeedProbeService`
- no `CAMERA`, `INTERNET`, or foreground-service permissions in the release
  manifest
- the AAB is signed with the upload key, not the debug key

## 8. Known gaps before shipping

- The redesigned UI has **not been visually verified on a device** — the phone
  was disconnected when it was finished. Install a debug build and check every
  state (setup, starting, idle, active) before uploading.
- Feed detection signatures are version-specific. They were derived against
  YouTube 21.32.4, Instagram 443.0.0.48.82 and TikTok 46.4.3, and **will break**
  when those apps update. `FeedProbeService` in the debug build exists to
  re-derive them; see `docs/feed-signatures.md`.
- No crash reporting. Consider whether you want any before a wide release —
  note that adding one changes the Data safety answers above.
