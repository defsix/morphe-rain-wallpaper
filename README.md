# Morphe Rain — Android live wallpaper

A standalone Android live wallpaper derived from the GPL-3.0 **Matrix Easter egg / animated background in [Morphe Manager](https://github.com/MorpheApp/morphe-manager)**.

Morphe Rain started as a faithful standalone port of Morphe's Matrix renderer and has grown into a configurable live wallpaper with editable hidden phrases, custom gradients, image-derived colour palettes, configurable backgrounds and manual image framing.

> **Unofficial project.** Morphe Rain is not affiliated with, maintained by, or endorsed by MorpheApp. The original Matrix renderer and visual recipe come from Morphe Manager and remain credited under GPL-3.0. See [Attribution](#attribution) and [`NOTICE.md`](NOTICE.md).

## Screenshots

<p align="center">
  <img src="docs/screenshots/home.svg" width="210" alt="Morphe Rain on the Android home screen" />
  &nbsp;&nbsp;
  <img src="docs/screenshots/palette.svg" width="210" alt="Image-derived palette settings" />
  &nbsp;&nbsp;
  <img src="docs/screenshots/controls.svg" width="210" alt="Hidden phrase and rain controls" />
</p>

## What matches the Morphe Easter egg

The default preset preserves the characteristic Morphe look:

- AMOLED-black background
- horizontal brand gradient `#1E5AA8` → `#00AFAE`
- 14dp monospace glyphs
- weighted `0` / `1` plus hex-style glyphs and punctuation
- 26-cell base tail length
- two streams per column, with a 45% chance of a third
- quadratic trail fade
- every third column dimmed for depth
- brighter stream heads
- 90ms glyph mutation cadence
- original hidden phrases: `USE MORPHE`, `NO ADS`, `WAKE UP`, `PATCHED`
- accelerometer parallax

## Added features

### Rain and phrases

- editable hidden phrases, one phrase per line
- adjustable hidden-phrase frequency
- speed, glyph size, column spacing and tail-length controls
- accelerometer parallax on/off
- 30 / 45 / 60 FPS modes

### Colour

- original Morphe blue → cyan preset
- classic Matrix green
- custom two-colour gradient with visual colour picker
- slowly cycling colour mode
- image-derived multi-stop palettes
- vividness control that favours saturated/bright colours instead of large muted regions
- palette preview and re-extraction from the selected source image

### Backgrounds

- solid background colour with visual colour picker
- reuse the palette-source image as the wallpaper background
- adjustable image opacity
- **Fill screen**, **Fit entire image**, and **Manual framing** modes
- manual zoom plus horizontal/vertical positioning
- EXIF-aware image decoding so rotated phone photos display correctly
- selected source-image preview in the settings app

## Build

The project currently uses:

- `compileSdk 36`
- `targetSdk 36`
- Java 17
- GitHub Actions for reproducible debug APK builds

### GitHub Actions

The included [`.github/workflows/build.yml`](.github/workflows/build.yml) builds a debug APK on every push to `main`, and can also be started manually from the **Actions** tab.

When the workflow finishes, download the artifact named:

```text
morphe-rain-debug-apk
```

The APK inside is `app-debug.apk`.

### Local build

Open the project in a current Android Studio installation with Android API 36 available and build the `app` module normally.

## Use

1. Install the APK.
2. Open **Morphe Rain**.
3. Configure the colours, phrases, rain and background as desired.
4. Tap **Set live wallpaper**.
5. Preview and apply it to the Home screen or Home + Lock screen.
6. Re-open Morphe Rain at any time; most setting changes are reflected by the running wallpaper immediately.

## Attribution

The original work that inspired and underpins this project is **Morphe Manager** by **MorpheApp**:

- upstream repository: https://github.com/MorpheApp/morphe-manager
- original renderer: [`MatrixBackground.kt`](https://github.com/MorpheApp/morphe-manager/blob/main/app/src/main/java/app/morphe/manager/ui/screen/shared/backgrounds/MatrixBackground.kt)
- upstream license: GNU General Public License v3.0

The standalone Android `WallpaperService`, settings UI, image-palette tooling, background-image handling and other extensions in this repository were developed for Morphe Rain. The Matrix visual constants, glyph/phrase recipe, stream behaviour, fades, gradient and related rendering concepts are derived from the GPL-3.0 upstream implementation.

If any of these extensions are useful to Morphe itself, they are intended to be straightforward to contribute back upstream as focused GPL-compatible changes.

## License

**GNU General Public License v3.0.** See [`LICENSE`](LICENSE) and [`NOTICE.md`](NOTICE.md).
