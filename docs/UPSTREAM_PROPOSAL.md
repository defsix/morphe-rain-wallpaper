Hi Morphe team,

I liked Morphe Manager's Matrix animated background enough that I ported the GPL-3.0 renderer into a small standalone Android live wallpaper and then experimented with extending it.

Standalone project: https://github.com/defsix/morphe-rain-wallpaper

The repository clearly credits Morphe Manager and the original `MatrixBackground.kt`, remains GPL-3.0, and is explicitly marked as unofficial/not endorsed by MorpheApp. There are also screenshots in the README.

While building it, the following additions ended up being useful:

- editable hidden Matrix phrases and phrase frequency
- visual custom colour picker / custom gradients
- image-derived multi-stop colour palettes
- a vividness bias so palette extraction favours bright/saturated colours over large dark or muted regions
- source-image preview and palette re-extraction
- optional source-image background with adjustable opacity
- configurable solid background colour
- fill / fit / manual background-image framing with zoom and X/Y positioning
- EXIF-aware image decoding for phone photos

I'm **not** proposing that Morphe Manager absorb the standalone live-wallpaper app. I wanted to share the work back upstream because some of the individual Matrix-background improvements may be useful in Morphe itself, particularly its existing animated-background/customisation system.

If any of these ideas are of interest, I'm happy to separate the relevant pieces and submit a focused PR against `dev`, following the contribution guidelines. Let me know which parts, if any, would fit Morphe's direction.

Thanks for making the original Matrix background — it was a fun renderer to build on.
