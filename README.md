# Morphe Rain — Android live wallpaper

A small standalone Android live wallpaper derived from Morphe Manager's GPL-3.0 Matrix Easter egg.

## What matches the Morphe Easter egg

The default preset preserves the characteristic look:

- black AMOLED background
- horizontal brand gradient `#1E5AA8` → `#00AFAE`
- 14dp monospace glyphs
- weighted `0`/`1` plus hex-style glyphs and punctuation
- 26-cell base tail length
- two streams per column, with a 45% chance of a third
- quadratic trail fade
- every third column dimmed for depth
- brighter stream heads
- 90ms glyph mutation cadence
- hidden phrases: `USE MORPHE`, `NO ADS`, `WAKE UP`, `PATCHED`
- accelerometer parallax

## Added controls

- Morphe blue→cyan / green / custom gradient / slow cycling colour
- speed
- glyph size
- column spacing
- tail length
- phrase frequency
- parallax on/off
- 30 / 45 / 60 FPS

## Build in Android Studio

Use a current Android Studio release with Android API 37 installed. Open the project folder and let Gradle sync. The project targets API 37 and uses AGP 9.3.0.

Build with **Build > Build APK(s)**, then install `app-debug.apk` on the phone.

## Build with GitHub Actions

Push this folder to a GitHub repository. The included `.github/workflows/build.yml` builds a debug APK on every push to `main`, or manually via **Actions > Build APK > Run workflow**. Download the `morphe-rain-debug-apk` artifact when the job finishes.

## Use

1. Install the APK.
2. Open **Morphe Rain**.
3. Tap **Set live wallpaper**.
4. Preview and apply it to Home screen or Home + Lock screen.
5. Re-open the app to adjust the effect; changes apply to the running wallpaper.

## License

GPL-3.0. See `LICENSE` and `NOTICE.md`.
