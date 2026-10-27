# Wide camera split layout (iPhone Duo / foldables) — design

## Problem

On an opened foldable (iPhone Duo, Pixel Fold, Galaxy Z Fold) held in
landscape, the Camera tab stretches today's single-pane live view across the
whole wide screen. Every other tab already behaves well on the Duo; the camera
is the one screen that wastes the extra width and reads badly.

The user tested the current library on a real Duo and reported this.

Context the library cannot see: the shipping apps (BiangBiang Hanzi, Harakat
Lens, sibling repos under `../`) target device family iPhone + iPad and allow
portrait + both landscapes on iPhone, so landscape is reachable on every
device and the Camera tab already runs in landscape on normal phones today.

## Goal

When the device is wide and landscape, split the Camera tab in two halves:

- **Right half** — the live camera exactly as it is today (preview, OCR pill
  overlays, zoom presets, control bar, captured-photo mode, plugin sheet).
- **Left half** — a *session log* of the text the camera has recognised since
  the tab was opened, each row showing original and transliteration, with
  tap-to-copy and long-press-to-save-to-history.

Everywhere else (portrait, folded, phone landscape, external screen) the
layout stays exactly as it is now.

Both platforms get the change (parity rule). Android foldables opened in
landscape get the same split.

## Non-goals

- No change to the OCR pipeline, throttle, debounce, `TextProcessingEngine`,
  `OCRService` seam, plugin hooks, History store, or Settings.
- No hinge-aware layout (aligning the split to the physical fold). Plain
  50/50 split.
- No split layout on external displays, in portrait, or when folded.
- No persistence of the session log across tab switches or app launches.
  History remains the only persistent store.
- No change to the Text / History / Settings tabs.

## Anti-goals

- Breaking today's single-pane camera on normal phones. Any regression there
  is a failure even if the split works.
- Making the left list flicker, reorder, or blank every OCR tick. A list that
  mirrors the live frame raw is worse than no list.
- Adding a dependency (e.g. `material3-adaptive`, `androidx.window`) just to
  detect the layout. Not needed for the chosen rule.

## Decisions (locked during brainstorming)

### Layout gate

Split is enabled when **all** hold, evaluated from the Camera tab's own
content size (not the whole window, so a future side-by-side host still
works):

1. landscape: `width > height`;
2. wide enough to be a tablet-class surface:
   `min(width, height) >= 500` pt/dp;
3. the window is on the device's built-in display, not an external one.

Rationale for a pure geometric rule instead of size classes: iPhone Plus/Max
in landscape already report a *regular* horizontal size class, so size class
alone would split a normal phone. The short-edge threshold excludes every
phone (short edge in landscape ≤ 440 pt/dp today) and includes every opened
foldable and iPad-class surface. The threshold is a single named constant in
one pure, unit-tested policy type per platform (see Architecture) so it can
be tuned after testing on the real Duo.

"Built-in display" check: iOS `UIWindowScene.screen` is the main screen (the
scene hosting the view); Android `displayId == Display.DEFAULT_DISPLAY`,
read from `Context.getDisplay()` on API 30+ and from
`WindowManager.defaultDisplay` below (module is `minSdk 26`). iOS apps that
do not add an external-display scene only ever mirror, so this check is a
safety guard, not the primary gate.

The gate is re-evaluated live: folding, rotating, or moving the window
switches layout in place without restarting the camera session or losing the
session log.

### Pane sides are physical, not reading-direction

The camera pane sits on the right because, with the Duo open and held the
usual way, the rear camera module is on the right half: the viewfinder is
beside the lens. This is a physical fact, so:

- the container forces left-to-right layout (`environment(\.layoutDirection,
  .leftToRight)` / `CompositionLocalProvider(LocalLayoutDirection provides
  Ltr)`) so an RTL app (Harakat Lens) does not mirror the panes. Row content
  (text, glyphs) inside the log pane keeps the app's own direction.
- when the device is rotated 180° in landscape the lens is on the left, so
  the panes swap: camera left, log right. `CameraLayoutPolicy` exposes
  `cameraSide(for interfaceOrientation) -> .left | .right` driven by
  `UIInterfaceOrientation.landscapeLeft/Right` on iOS and
  `Display.rotation` (`ROTATION_90` / `ROTATION_270`) on Android. Which
  orientation puts the lens on the right is one named constant per
  platform, set by the builder from a quick check on the Duo (or on any
  iPhone: the lens corner is the same one). Policy and tests use the
  constant, never a literal.

### Left pane — session log, not live mirror

- Rows: **original** (primary, larger) over **transliteration** (secondary,
  accent-tinted), trailing copy glyph.
- Source: every OCR result that reaches the view model — live frames, the
  captured photo, and gallery images — is *ingested* into the log.
- **Dedup by original text** (exact match after trimming whitespace). A text
  already in the log is not re-added and keeps its position. Both platforms
  trim only this explicit Unicode `White_Space` set at the original's boundaries:
  U+0009–U+000D, U+0020, U+0085, U+00A0, U+1680, U+2000–U+200A,
  U+2028–U+2029, U+202F, U+205F, U+3000. Whitespace-only originals are ignored.
  Interior characters and transliterations are preserved. U+001C–U+001F,
  U+180E, U+200B, and U+FEFF are not trimmed; platform default trim APIs
  disagree on some of these characters and must not define the policy.
- **Newest at top.** A newly ingested row inserts at index 0 with a short
  highlight that stays visible 1.5 s (same time a toast stays visible), then
  fades with `AppDesign.toastAnimation`. New token
  `AppDesign.logHighlightDuration = 1.5` on both platforms.
- **Cap**: 50 entries; oldest (bottom) dropped first.
- **Header**: "Found text · N" + **Clear** button that empties the log.
- **Footer hint**: existing `tapToCopyLongPressToSave` string.
- **Empty state**: centred text "Point the camera at text" until the first
  row.
- **Tap** copies what the camera toggle currently shows (transliteration when
  the toggle is on, original when off — same rule as the overlay pills) and
  fires the existing "Text copied" toast.
- **Long press** saves `(original, transliteration, activeVariant.id)` to
  History via the existing `settings.addHistory` and fires the existing
  "Saved to History" toast.
- The log is cleared when the Camera tab leaves the screen (same lifecycle as
  `recognizedTexts` today) and by the Clear button. Never persisted.
- The log is ingested regardless of layout (cheap, pure), but only rendered
  in the split layout. Portrait users never see it.

### Toasts and sheet

Copy / saved toasts and the plugin bottom sheet stay where they are today
(over the camera pane / modal). Left-pane actions reuse the same model flags,
so there is one toast implementation per platform.

### Strings

Three new `UIStrings` / `UiStrings` entries (English defaults, overridable
through the existing `strings` override map, mirrored on both platforms):

| Key              | Default                    |
| ---------------- | -------------------------- |
| `cameraLogTitle` | `Found text`               |
| `cameraLogClear` | `Clear`                    |
| `cameraLogEmpty` | `Point the camera at text` |

The count is rendered by the view as `"\(cameraLogTitle) · \(n)"`.

## Architecture

Two small pure types, mirrored 1:1, plus one new pane view per platform and
a layout switch in the existing camera screen. Nothing else moves.

### 1. `CameraLayoutPolicy` (pure, tested)

- iOS: `Sources/BiangBiangUI/UI/CameraLayoutPolicy.swift`
- Android: `.../ui/screens/camera/CameraLayoutPolicy.kt`

```swift
enum CameraLayoutPolicy {
    enum Side { case left, right }
    static let minShortEdge: CGFloat = 500
    /// The landscape orientation in which the lens is on the right half.
    static let lensOnRightOrientation: UIInterfaceOrientation = …
    static func isSplit(size: CGSize, isBuiltInDisplay: Bool) -> Bool
    static func cameraSide(for orientation: UIInterfaceOrientation) -> Side
}
```

Kotlin: `object CameraLayoutPolicy { fun isSplit(widthDp, heightDp,
isBuiltInDisplay): Boolean; fun cameraSide(displayRotation: Int): Side }`
with the matching `LENS_ON_RIGHT_ROTATION` constant. Only the orientation
enum is imported; same truth table on both.

### 2. `CameraSessionLog` (pure, tested)

- iOS: `Sources/BiangBiangUI/Services/Camera/CameraSessionLog.swift`
- Android: `.../services/camera/CameraSessionLog.kt`

Value-type model with:

- `entries: [Entry]` where `Entry { id, original, transliteration }`;
- `ingest(_ pairs: [(original, transliteration)]) -> [Entry.ID]` returning
  the ids actually inserted (used for the highlight);
- `clear()`;
- dedup, newest-first, cap-50 semantics as above.

Owned by the camera view model: iOS `OCRCameraModel.sessionLog` (published
via `@Observable`), fed from `applyRecognized` after `transliterationMap` is
rebuilt; Android a `remember`ed `mutableStateOf(CameraSessionLog())` in
`CameraLiveScreen`, fed from the analyzer `onResult` and from the still-image
OCR path with the same ingest call. Only boxes that have a transliteration
are ingested: on iOS filter through `transliterationMap` at the call site
(same filter the overlays use); on Android `LiveOcrAnalyzer` already drops
boxes without a transliteration, so every `OcrBox` qualifies.

### 3. `CameraSessionLogPane` (view)

- iOS: `Sources/BiangBiangUI/UI/CameraSessionLogPane.swift` — `List`-free
  `ScrollView` + `LazyVStack` so rows can be plain buttons with
  `simultaneousGesture(LongPressGesture)` exactly like `RecognizedTextOverlay`.
- Android: `.../ui/screens/camera/CameraSessionLogPane.kt` — `LazyColumn`
  with `combinedClickable(onClick, onLongClick)`.

Inputs: the log, `showTransliteration`, strings, accent colour, callbacks
`onCopy(entry)`, `onSave(entry)`, `onClear()`. No camera knowledge.

### 4. `CameraSplitLayout` (slot container) and the switch

A tiny slot-based container, mirrored, with no camera knowledge so it can be
hosted in tests with stand-in panes:

- iOS: `Sources/BiangBiangUI/UI/CameraSplitLayout.swift` —
  `CameraSplitLayout(isBuiltInDisplay:cameraSide:) { sidePane } camera:
  { cameraPane }`. It owns the outer `GeometryReader`, asks
  `CameraLayoutPolicy.isSplit`, forces `.leftToRight` layout direction, and
  renders `HStack(spacing: 0)` with the two panes ordered by `cameraSide`,
  each `frame(maxWidth: .infinity)`, when split; else `cameraPane` alone.
- Android: `.../ui/screens/camera/CameraSplitLayout.kt` —
  `CameraSplitLayout(isBuiltInDisplay, cameraSide, sidePane, cameraPane)`
  using `BoxWithConstraints`, `LocalLayoutDirection provides Ltr`, and
  `Row` with `weight(1f)` on each pane, ordered by `cameraSide`.

`CameraLiveScreen` (both platforms) wraps today's body in it: the existing
camera ZStack / Box becomes `cameraPane`, `CameraSessionLogPane` becomes
`sidePane`. The existing inner `GeometryReader` stays inside `cameraPane` so
`RecognizedTextOverlay.viewSize` and `CameraPreview` bounds are the *pane*
size, which keeps `pixelRectToViewRect` correct with no change. Android
`OcrOverlay` already scales against its own modifier size, so no change.

`CameraScreen` (permission router) and `BiangBiangRootView` / `BiangBiangRoot`
are untouched.

### 5. Landscape correctness of the camera pane (required, iOS)

`CameraPreview` hard-codes `connection.videoRotationAngle = 0` and the live
frame path passes `CGImage`s to Vision with no orientation. That is correct
only while the app is portrait. The split layout exists only in landscape, so
this work includes making the iOS preview and the OCR box mapping upright in
landscape on the built-in display. Builder:

- derive the rotation angle from the window scene's interface orientation
  (or `AVCaptureDevice.RotationCoordinator`), apply it to both the preview
  connection and the video-data-output connection so Vision receives upright
  frames and `lastImagePixelSize` matches, and update it on rotation;
- keep portrait behaviour byte-for-byte identical (angle 0 path);
- this fix applies to every landscape device, not only when the split is
  on. That is intended: the apps already allow landscape on iPhone.
- verify: the iOS simulator has no camera feed, so only the gallery /
  captured-image path (aspect-fit mapping) can be checked there, on an iPad
  simulator in landscape. The live path is verified on hardware: any
  landscape-capable iPhone or iPad first, then the user's Duo.

Android uses CameraX `PreviewView` + `imageInfo.rotationDegrees` via
`OcrRotation`, which is already orientation-aware; no change expected, but
verify on a foldable/tablet emulator in landscape (the Android emulator does
provide a virtual camera feed).

### Verifying in a real app

Neither platform in this repo ships a runnable app (iOS package is library +
tests; `android/examples` has no Activity). Verification host: BiangBiang
Hanzi (`../BiangBiang-Hanzi`, iOS + Android), pointed at a local checkout of
this library (SwiftPM local package override / Gradle `includeBuild` or a
local Maven publish). Nothing in that repo is committed as part of this
work.

### Parity

Every new file has a mirrored counterpart and a mirrored test; add rows to
`docs/parity.md`.

## Testing

Mirrored unit tests (Swift Testing / JUnit):

- `CameraLayoutPolicyTests`: portrait wide → false; phone landscape
  (932×430) → false; Duo/iPad-class landscape (e.g. 1024×744) → true; same
  size with `isBuiltInDisplay == false` → false; exact threshold edges;
  `cameraSide` returns `.right` for the lens-on-right constant and `.left`
  for the opposite landscape orientation.
- `CameraSessionLogTests`: ingest inserts newest first; dedup keeps original
  position and returns no ids; whitespace-trimmed dedup; cap at 50 drops
  oldest; clear empties; empty transliteration pairs are rejected by the
  caller (test the filter at the call site or document it).

Layout tests, through the slot container only (no camera, no CameraX, no
view introspection — the existing `RootViewUITests` rules stand):

- iOS `CameraSplitLayoutTests`: host `CameraSplitLayout` with two stand-in
  panes that record their appearance (e.g. set a flag in `onAppear`) at
  1024×744 and at 744×1024; assert the side pane appeared only in the first.
  One more run with `cameraSide: .left` under an RTL environment asserting
  pane order follows `cameraSide`, not layout direction.
  Keep the existing crash-free `RootViewUITests` hosting and add one run at
  1024×744.
- Android `CameraSplitLayoutTest`: Robolectric-hosted Compose test (the
  version catalog already has `ui-test-junit4` / `ui-test-manifest`; wire
  them as `testImplementation` / `debugImplementation` only — test-only
  dependencies do not violate the no-new-runtime-dependency anti-goal) with
  a 1024×744 dp and a 744×1024 dp window, asserting the side pane's test tag
  exists only in the first. No `androidTest` source set and no emulator job
  in CI.

The iOS pane also has a native XCUITest fixture in `Tests/CameraPaneUI`.
Its standard-library Python generator creates an app and test project in a
system temporary directory; CI runs it on the same simulator as unit tests.
The fixture imports the library with `@testable` to host the internal pane,
then tests rendered localized rows/count/empty state and actual tap,
long-press, and Clear controls. It adds no library or test framework dependency.
The existing layout tests continue to avoid view introspection.

Manual on the Duo (user): open + landscape → split with camera on the lens
side; rotate 180° → panes swap; fold or rotate to portrait → single pane,
log kept; external display → single pane; pills upright in landscape; same
pane order in Harakat Lens (RTL).

## Left to the builder

- Exact row typography and spacing (follow `AppDesign` tokens and the
  History screen rows for consistency).
- How the highlight on a new row is animated.
- Whether the left pane background is black (matches camera) or the system
  grouped background — pick black to keep the tab visually one surface.
- Precise way to read the built-in-display flag in SwiftUI (environment
  value via a tiny `UIViewRepresentable`, or `UIApplication.connectedScenes`).

## Open questions

None blocking. The threshold constant (500) and the Duo's actual reported
size are to be confirmed on hardware by the user after the first build.
