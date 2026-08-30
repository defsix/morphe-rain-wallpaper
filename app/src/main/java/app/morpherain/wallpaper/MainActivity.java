package app.morpherain.wallpaper;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends Activity {
    private static final int REQUEST_PICK_IMAGE = 4102;

    private SharedPreferences prefs;
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Config.prefs(this);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(10, 10, 10));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(22), dp(20), dp(40));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("Morphe Rain", 28, Color.WHITE);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        content.addView(title);
        TextView subtitle = text("Standalone live wallpaper using the visual recipe from the GPL-3.0 Matrix Easter egg in Morphe Manager.", 14, Color.LTGRAY);
        subtitle.setPadding(0, dp(6), 0, dp(20));
        content.addView(subtitle);

        Button set = button("Set live wallpaper");
        set.setOnClickListener(v -> openWallpaperPicker());
        content.addView(set);

        addSection("Colour");
        Spinner mode = new Spinner(this);
        String[] modes = {
                "Morphe blue → cyan",
                "Classic green",
                "Custom gradient",
                "Slow colour cycle",
                "Image-derived palette"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modes);
        mode.setAdapter(adapter);
        mode.setSelection(Math.max(0, Math.min(modes.length - 1, Config.mode(prefs))));
        mode.setOnItemSelectedListener(new SimpleItemSelectedListener(position ->
                prefs.edit().putInt(Config.KEY_COLOR_MODE, position).apply()));
        content.addView(mode, matchWrap());

        addColorControl("Custom start colour", Config.customStart(prefs), Config.KEY_START_COLOR);
        addColorControl("Custom end colour", Config.customEnd(prefs), Config.KEY_END_COLOR);

        addSeek("Colour-cycle period", 10, 120, Config.cycleSeconds(prefs),
                value -> prefs.edit().putInt(Config.KEY_CYCLE_SECONDS, value).apply(), " s");

        TextView imageHelp = text("Choose any image and Morphe Rain will build a multi-stop gradient from it. The extractor favours vivid colours over large muted background areas.", 13, Color.LTGRAY);
        imageHelp.setPadding(0, dp(14), 0, dp(8));
        content.addView(imageHelp);

        addSeek("Palette vividness", 0, 100, Config.paletteVividness(prefs),
                value -> prefs.edit().putInt(Config.KEY_PALETTE_VIVIDNESS, value).apply(), "%");
        TextView vividHelp = text("Higher values favour saturated, bright colours and reduce the influence of dark or grey areas. 75% is the recommended default. Re-extract after changing it.", 12, Color.GRAY);
        vividHelp.setPadding(0, 0, 0, dp(8));
        content.addView(vividHelp);

        Button imageButton = button("Choose image & generate palette");
        imageButton.setOnClickListener(v -> choosePaletteImage());
        content.addView(imageButton);

        addImageSourcePreview();

        TextView paletteLabel = text("Current image palette", 13, Color.GRAY);
        paletteLabel.setPadding(0, dp(10), 0, dp(5));
        content.addView(paletteLabel);
        addPalettePreview(Config.imagePalette(prefs));

        addSection("Background");
        TextView backgroundHelp = text("Use a plain colour behind the rain, or reuse the palette-source image. The image can now be fitted, zoomed and repositioned. The selected solid colour remains underneath it.", 13, Color.LTGRAY);
        backgroundHelp.setPadding(0, 0, 0, dp(8));
        content.addView(backgroundHelp);

        Spinner backgroundMode = new Spinner(this);
        String[] backgroundModes = {"Solid colour", "Palette source image"};
        ArrayAdapter<String> backgroundAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, backgroundModes);
        backgroundMode.setAdapter(backgroundAdapter);
        backgroundMode.setSelection(Math.max(0, Math.min(backgroundModes.length - 1,
                Config.backgroundMode(prefs))));
        backgroundMode.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            if (position == Config.BACKGROUND_IMAGE && Config.imageUri(prefs) == null) {
                Toast.makeText(this, "Choose a palette image first", Toast.LENGTH_SHORT).show();
                prefs.edit().putInt(Config.KEY_BACKGROUND_MODE, Config.BACKGROUND_SOLID).apply();
                backgroundMode.setSelection(Config.BACKGROUND_SOLID);
            } else if (position != Config.backgroundMode(prefs)) {
                prefs.edit().putInt(Config.KEY_BACKGROUND_MODE, position).apply();
                buildUi();
            }
        }));
        content.addView(backgroundMode, matchWrap());

        addBackgroundColorControl("Background colour", Config.backgroundColor(prefs));

        if (Config.backgroundMode(prefs) == Config.BACKGROUND_IMAGE && Config.imageUri(prefs) != null) {
            addSeek("Background image opacity", 0, 100, Config.backgroundImageOpacity(prefs),
                    value -> prefs.edit().putInt(Config.KEY_BACKGROUND_IMAGE_OPACITY, value).apply(), "%");

            TextView fitLabel = text("Image framing", 14, Color.LTGRAY);
            fitLabel.setPadding(0, dp(12), 0, dp(3));
            content.addView(fitLabel);

            Spinner fitMode = new Spinner(this);
            String[] fitModes = {
                    "Fill screen (crop)",
                    "Fit entire image",
                    "Manual framing"
            };
            ArrayAdapter<String> fitAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_dropdown_item, fitModes);
            fitMode.setAdapter(fitAdapter);
            fitMode.setSelection(Math.max(0, Math.min(fitModes.length - 1,
                    Config.backgroundFitMode(prefs))));
            fitMode.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
                if (position != Config.backgroundFitMode(prefs)) {
                    prefs.edit().putInt(Config.KEY_BACKGROUND_FIT_MODE, position).apply();
                    buildUi();
                }
            }));
            content.addView(fitMode, matchWrap());

            if (Config.backgroundFitMode(prefs) == Config.FIT_MANUAL) {
                TextView manualHelp = text("100% shows the whole image fitted to the screen. Go below 100% to zoom farther out, or above 100% to crop in. Position sliders move the image within the available space/crop.", 12, Color.GRAY);
                manualHelp.setPadding(0, dp(6), 0, dp(8));
                content.addView(manualHelp);

                addSeek("Background zoom", 50, 200, Config.backgroundZoomPercent(prefs),
                        value -> prefs.edit().putInt(Config.KEY_BACKGROUND_ZOOM, value).apply(), "%");
                addSeek("Horizontal position", -100, 100, Config.backgroundOffsetX(prefs),
                        value -> prefs.edit().putInt(Config.KEY_BACKGROUND_OFFSET_X, value).apply(), "");
                addSeek("Vertical position", -100, 100, Config.backgroundOffsetY(prefs),
                        value -> prefs.edit().putInt(Config.KEY_BACKGROUND_OFFSET_Y, value).apply(), "");

                Button resetFraming = button("Reset framing");
                resetFraming.setOnClickListener(v -> {
                    prefs.edit()
                            .putInt(Config.KEY_BACKGROUND_ZOOM, 100)
                            .putInt(Config.KEY_BACKGROUND_OFFSET_X, 0)
                            .putInt(Config.KEY_BACKGROUND_OFFSET_Y, 0)
                            .apply();
                    buildUi();
                });
                content.addView(resetFraming);
            }
        } else if (Config.imageUri(prefs) == null) {
            TextView noImage = text("No palette-source image is currently stored. Select one in the Colour section to enable image background mode.", 12, Color.GRAY);
            noImage.setPadding(0, dp(2), 0, 0);
            content.addView(noImage);
        }

        addSection("Hidden phrases");
        TextView phraseHelp = text("One phrase per line. These are inserted vertically into the rain at the frequency set below. Short phrases usually look best.", 13, Color.LTGRAY);
        phraseHelp.setPadding(0, 0, 0, dp(8));
        content.addView(phraseHelp);

        EditText phrases = new EditText(this);
        phrases.setText(Config.phrasesText(prefs));
        phrases.setTextColor(Color.WHITE);
        phrases.setHintTextColor(Color.GRAY);
        phrases.setHint("USE MORPHE\nNO ADS\nWAKE UP\nPATCHED");
        phrases.setMinLines(4);
        phrases.setMaxLines(10);
        phrases.setGravity(Gravity.TOP | Gravity.START);
        phrases.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(0, 175, 174)));
        content.addView(phrases, matchWrap());

        LinearLayout phraseButtons = new LinearLayout(this);
        phraseButtons.setOrientation(LinearLayout.HORIZONTAL);
        phraseButtons.setGravity(Gravity.END);
        phraseButtons.setPadding(0, dp(6), 0, 0);
        content.addView(phraseButtons, matchWrap());

        Button restorePhrases = button("Original phrases");
        restorePhrases.setOnClickListener(v -> phrases.setText(Config.DEFAULT_PHRASES));
        phraseButtons.addView(restorePhrases, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button savePhrases = button("Save phrases");
        savePhrases.setOnClickListener(v -> {
            prefs.edit().putString(Config.KEY_PHRASES, phrases.getText().toString()).apply();
            Toast.makeText(this, "Hidden phrases updated", Toast.LENGTH_SHORT).show();
        });
        phraseButtons.addView(savePhrases, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        addSection("Rain");
        addSeek("Speed", 25, 200, prefs.getInt(Config.KEY_SPEED, 100),
                value -> prefs.edit().putInt(Config.KEY_SPEED, value).apply(), "%");
        addSeek("Glyph size", 8, 28, prefs.getInt(Config.KEY_GLYPH, 14),
                value -> prefs.edit().putInt(Config.KEY_GLYPH, value).apply(), " dp");
        addSeek("Column spacing", 70, 220, prefs.getInt(Config.KEY_SPACING, 100),
                value -> prefs.edit().putInt(Config.KEY_SPACING, value).apply(), "%");
        addSeek("Tail length", 8, 50, prefs.getInt(Config.KEY_TAIL, 26),
                value -> prefs.edit().putInt(Config.KEY_TAIL, value).apply(), " cells");
        addSeek("Hidden phrase frequency", 0, 30, prefs.getInt(Config.KEY_PHRASE, 12),
                value -> prefs.edit().putInt(Config.KEY_PHRASE, value).apply(), "%");

        addSection("Motion & battery");
        CheckBox parallax = new CheckBox(this);
        parallax.setText("Tilt / parallax effect");
        parallax.setTextColor(Color.WHITE);
        parallax.setChecked(Config.parallax(prefs));
        parallax.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(Config.KEY_PARALLAX, isChecked).apply());
        content.addView(parallax);

        Spinner fps = new Spinner(this);
        Integer[] fpsValues = {30, 45, 60};
        ArrayAdapter<Integer> fpsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, fpsValues);
        fps.setAdapter(fpsAdapter);
        int currentFps = Config.fps(prefs);
        fps.setSelection(currentFps == 30 ? 0 : currentFps == 45 ? 1 : 2);
        fps.setOnItemSelectedListener(new SimpleItemSelectedListener(position ->
                prefs.edit().putInt(Config.KEY_FPS, fpsValues[position]).apply()));
        TextView fpsLabel = text("Frame rate", 15, Color.LTGRAY);
        fpsLabel.setPadding(0, dp(12), 0, dp(2));
        content.addView(fpsLabel);
        content.addView(fps, matchWrap());

        addSection("Defaults");
        Button reset = button("Reset to Morphe original look");
        reset.setOnClickListener(v -> {
            Config.reset(prefs);
            buildUi();
        });
        content.addView(reset);

        TextView note = text("Original preset: #1E5AA8 → #00AFAE, black background, 14dp monospace glyphs, 26-cell tails, 2–3 streams per column, 12% phrase chance and parallax enabled.", 12, Color.GRAY);
        note.setPadding(0, dp(18), 0, 0);
        content.addView(note);

        setContentView(scroll);
    }

    private void addColorControl(String label, int currentColor, String prefKey) {
        TextView title = text(label, 14, Color.LTGRAY);
        title.setPadding(0, dp(12), 0, dp(3));
        content.addView(title);

        Button colorButton = button(ColorPickerDialog.toHex(currentColor));
        styleColorButton(colorButton, currentColor);
        colorButton.setOnClickListener(v -> ColorPickerDialog.show(this, label, currentColor, color -> {
            prefs.edit()
                    .putInt(prefKey, color)
                    .putInt(Config.KEY_COLOR_MODE, Config.MODE_CUSTOM)
                    .apply();
            buildUi();
        }));
        content.addView(colorButton, matchWrap());
    }

    private void addBackgroundColorControl(String label, int currentColor) {
        TextView title = text(label, 14, Color.LTGRAY);
        title.setPadding(0, dp(12), 0, dp(3));
        content.addView(title);

        Button colorButton = button(ColorPickerDialog.toHex(currentColor));
        styleColorButton(colorButton, currentColor);
        colorButton.setOnClickListener(v -> ColorPickerDialog.show(this, label, currentColor, color -> {
            prefs.edit().putInt(Config.KEY_BACKGROUND_COLOR, color).apply();
            buildUi();
        }));
        content.addView(colorButton, matchWrap());
    }

    private void styleColorButton(Button button, int color) {
        button.setBackgroundTintList(ColorStateList.valueOf(color));
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
        button.setTextColor(luminance > 0.62 ? Color.BLACK : Color.WHITE);
    }

    private void addPalettePreview(int[] colors) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 0, 0, dp(4));
        for (int color : colors) {
            View swatch = new View(this);
            swatch.setBackgroundColor(color);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1f);
            lp.setMargins(dp(1), 0, dp(1), 0);
            row.addView(swatch, lp);
        }
        content.addView(row, matchWrap());
    }

    private void addImageSourcePreview() {
        String uriText = Config.imageUri(prefs);
        if (uriText == null || uriText.trim().isEmpty()) return;

        Uri uri;
        try {
            uri = Uri.parse(uriText);
        } catch (Exception e) {
            return;
        }

        String displayName = getDisplayName(uri);
        TextView sourceLabel = text("Palette source: " + displayName, 13, Color.LTGRAY);
        sourceLabel.setPadding(0, dp(12), 0, dp(6));
        content.addView(sourceLabel);

        try {
            Bitmap preview = decodeSampledBitmap(uri, 720);
            if (preview != null) {
                ImageView image = new ImageView(this);
                image.setImageBitmap(preview);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setAdjustViewBounds(false);
                image.setBackgroundColor(Color.rgb(20, 20, 20));
                content.addView(image, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(180)));
            }
        } catch (IOException ignored) {
            TextView unavailable = text("Image preview unavailable. The saved palette will still work.", 12, Color.GRAY);
            content.addView(unavailable);
        }

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(6), 0, 0);
        content.addView(buttons, matchWrap());

        Button reExtract = button("Re-extract palette");
        reExtract.setOnClickListener(v -> extractPaletteFromUri(uri));
        buttons.addView(reExtract, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button clear = button("Clear image");
        clear.setOnClickListener(v -> {
            prefs.edit()
                    .remove(Config.KEY_IMAGE_URI)
                    .remove(Config.KEY_IMAGE_PALETTE)
                    .putInt(Config.KEY_COLOR_MODE, Config.MODE_MORPHE)
                    .putInt(Config.KEY_BACKGROUND_MODE, Config.BACKGROUND_SOLID)
                    .apply();
            buildUi();
        });
        buttons.addView(clear, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }

    private void choosePaletteImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {}

        prefs.edit().putString(Config.KEY_IMAGE_URI, uri.toString()).apply();
        extractPaletteFromUri(uri);
    }

    private void extractPaletteFromUri(Uri uri) {
        Toast.makeText(this, "Extracting vivid colours…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = decodeSampledBitmap(uri, 640);
                if (bitmap == null) throw new IOException("Could not decode image");
                int[] palette = PaletteExtractor.extract(bitmap, 5, Config.paletteVividness(prefs));
                prefs.edit()
                        .putString(Config.KEY_IMAGE_URI, uri.toString())
                        .putString(Config.KEY_IMAGE_PALETTE, Config.encodePalette(palette))
                        .putInt(Config.KEY_COLOR_MODE, Config.MODE_IMAGE)
                        .apply();
                runOnUiThread(() -> {
                    buildUi();
                    Toast.makeText(this, "Vivid image palette applied", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Could not read that image", Toast.LENGTH_LONG).show());
            } finally {
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            }
        }, "palette-extractor").start();
    }

    private String getDisplayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) {
                    String value = cursor.getString(column);
                    if (value != null && !value.trim().isEmpty()) return value;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        String last = uri.getLastPathSegment();
        return last == null ? "selected image" : last;
    }

    private Bitmap decodeSampledBitmap(Uri uri, int maxSide) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) return null;
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int sample = 1;
        while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > maxSide) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) return null;
            return BitmapFactory.decodeStream(input, null, options);
        }
    }

    private void openWallpaperPicker() {
        ComponentName component = new ComponentName(this, RainWallpaperService.class);
        try {
            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component);
            startActivity(intent);
        } catch (Exception e) {
            startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
        }
    }

    private void addSection(String name) {
        TextView section = text(name, 18, Color.rgb(0, 200, 200));
        section.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        section.setPadding(0, dp(24), 0, dp(8));
        content.addView(section);
    }

    private void addSeek(String label, int min, int max, int initial, IntConsumer consumer, String suffix) {
        TextView title = text(label + ": " + initial + suffix, 14, Color.LTGRAY);
        content.addView(title);
        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        seek.setProgress(Math.max(0, Math.min(max - min, initial - min)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = min + progress;
                title.setText(label + ": " + value + suffix);
                if (fromUser) consumer.accept(value);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        content.addView(seek, matchWrap());
    }

    private TextView text(String value, int sp, int color) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        return tv;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        return button;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private interface IntConsumer { void accept(int value); }

    private interface PositionConsumer { void accept(int position); }

    private static final class SimpleItemSelectedListener implements android.widget.AdapterView.OnItemSelectedListener {
        private final PositionConsumer consumer;
        SimpleItemSelectedListener(PositionConsumer consumer) { this.consumer = consumer; }
        @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
            consumer.accept(position);
        }
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
    }
}
