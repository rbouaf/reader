# Reader for Android: build plan

A personal reader app for a Pixel 10 (Android 16) and a Galaxy Tab S9, installed over USB.
The design source is `../mockups/v8/`, which holds five looks that switch from Settings.

## What it does

- **Library:** you pick one folder (the Android system folder picker); everything in it and its subfolders shows up.
  The Settings button lives here.
- **Formats:** EPUB, PDF, TXT, MD.
- **Book view:** only the book. Tap the middle to show minimal controls:
  - back;
  - title and chapter;
  - a scrubber with page x / y and "back to where you were";
  - contents, search and read aloud.
- **Settings** open only from the library:
  - **Style:** the five looks.
  - **Theme:** System / Light / Dark / Black (AMOLED); for System, a choice of what to use at night: Dark or Black.
  - **Warmth:** 0 to 100.
  - **Text:** font, size, line spacing, margins, justify, hyphenation.
  - **Pages:** spread, tap side, volume keys.
  - **PDF:** trim margins.
  - **Haptics:** off / quiet / full.
- **Warmth** shifts every colour of the look from neutral to warm, the PDF page recolouring included. It is not a translucent overlay.
- **Sync:** each book's reading position and reading activity are saved as small files in the library folder, so whatever already syncs that folder carries them between devices.
- **Search** inside a book, and **read aloud** (Android text-to-speech, page following).

## Stack

| Concern | Choice |
|---|---|
| Language/UI | Kotlin, Jetpack Compose. Custom drawing; Material3 is used only for the basics |
| SDK | minSdk 35, target/compile 36 |
| EPUB, and TXT/MD | Readium Kotlin Toolkit 3.4 (`shared`, `streamer`, `navigator`, `navigator-media-tts`). TXT/MD are converted to a small EPUB in the cache, so all reflowable books share one engine, with the same settings, search and read-aloud |
| Markdown | commonmark-java (plus the GFM tables extension) → XHTML |
| PDF | Platform `PdfRenderer` (API 35 has text extraction and search) with our own Compose viewer: bitmap tiles, recoloured with a `ColorMatrix`, automatic trim from a low-res bounding-box scan, pinch/double-tap zoom, two-page spreads on landscape tablets |
| Folder access | Storage Access Framework tree URI (persisted), with a fast `DocumentsContract` child query instead of DocumentFile |
| Settings | DataStore (per device; settings don't sync) |
| Metadata cache | JSON in app files, keyed by uri + size + mtime |
| Screenshots in tests | Roborazzi + Robolectric (JVM, no emulator), used to verify the looks |
| DI | None; the `ReaderApp` singletons are enough |

## Architecture (`app/src/main/java/app/reader/`)

```
ReaderApp.kt            Application: lazily creates the repositories
MainActivity.kt         Library and Settings (Compose); switching looks recomposes in place
ReaderActivity.kt       Book view: immersive, keeps the screen on, volume keys
data/
  Settings.kt           data class AppSettings and SettingsRepository (DataStore)
  Library.kt            Book model, folder scan, metadata extraction, cache
  Sidecar.kt            positions and activity files in <folder>/.reader/
  TextToEpub.kt         TXT/MD → EPUB (cached)
theme/
  Palette.kt            Pal(neutral, warm) pairs, mixing, Light/Dark/Black resolution
  Fonts.kt              asset font families (UI and book faces)
  Haptics.kt            the haptic grammar → HapticFeedbackConstants or vibration primitives
  LocalTheme.kt         CompositionLocals: resolved colours, look, haptics
looks/
  Look.kt               Look interface and registry
  common/               shared state models and components (settings scaffold, scrubber logic)
  readout/  trace/  editorial/  radar/  plotter/
reader/
  ReaderViewModel.kt    shared state: position, chapters, search, TTS
  epub/                 Readium navigator host, preferences mapping, fonts
  pdf/                  PdfDocument wrapper, page cache, trim, recolour, viewer
```

### Look contract

Every look supplies the following:
- **Palette:** light, dark and black, each key a neutral/warm pair taken from the mockup's `READER_CONFIG`. Black is designed per look: pure #000 paper, with rules and grid retuned against black.
- **Book fonts:** four reading faces; the first is the default.
- **Composables:**
  - `Library(state, actions)`: the whole library screen, laid out for phone, tablet portrait and tablet landscape like the mockup;
  - `SettingsChrome`: the look's styling of the shared settings scaffold (headers, segmented control, slider, switch, Style picker card);
  - `ReaderChrome(state, actions)`: the overlay drawn over the book, scrubber included;
  - `BookSheet`: the long-press actions sheet.

The settings structure is shared (as `kit.js` was in the mockups), so all five looks have the same settings.

### Sidecar format

The files live at `<folder>/.reader/<bookKey>.<deviceId>.json`.
- `bookKey` is the sha1 of the book's path relative to the folder, so it is the same on both devices.
- Each device writes only its own file. That avoids sync conflicts.
- When reading, the newest `updatedAt` wins for the position, and activity is summed across devices.

```json
{ "v":1, "path":"Fiction/Middlemarch.epub", "device":"pixel-10-3f2a",
  "updatedAt": 1761100000000, "locator": { ...Readium Locator JSON or {"page":12} for PDF },
  "progress": 0.42, "finished": false, "openedAt": 1761090000000,
  "activity": { "2026-10-22": 1830 } }
```

Activity counts seconds read per day. It feeds the trace graphs in Readout + trace and Editorial, which were synthetic in the mockups.

### Haptic grammar (from `mockups/v8/core.js`)

`pageTurn` (full only), `chapter`, `edge`, `bookEnd`, `scrubGrab`, `scrubPage`, `scrubChapter`, `scrubReturn`, `scrubRelease`, `detent`, `detentHome`, `select`, `toggleOn`, `toggleOff`, `longPress`, `confirm`.

- These use `View.performHapticFeedback` with the API 34+ constants (`SEGMENT_TICK`, `SEGMENT_FREQUENT_TICK`, `GESTURE_START/END`, `TOGGLE_ON/OFF`, `CONFIRM`, `LONG_PRESS`, `CLOCK_TICK`, ...), which the system tunes for each device.
- Thud, low tick and slow rise use `VibrationEffect.Composition` when `arePrimitivesSupported`, otherwise the nearest constant. That covers the Tab S9.

## Phases

1. **Scaffold:** Gradle project, dependencies, fonts in assets, an empty app that builds and runs on the emulator, Roborazzi wired up.
2. **Core:**
   - settings, folder pick and scan, metadata, sidecars, theme engine, haptics;
   - the reader: EPUB/TXT/MD through Readium, PDF viewer, search, TTS;
   - the **Readout** look in full, as the reference implementation.
3. **Looks:** port Trace, Editorial, Radar and Plotter in parallel (verified with Roborazzi screenshots against the mockup).
4. **Integration:**
   - run on phone and tablet emulators with real books in each format;
   - test themes, warmth and look switching, sync between two emulators sharing a folder, and haptics;
   - then install on the user's devices over USB.
