package app.morpherain.wallpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class Config {
    static final String PREFS = "morphe_rain_settings";

    static final String KEY_COLOR_MODE = "color_mode";
    static final String KEY_START_HUE = "start_hue";
    static final String KEY_END_HUE = "end_hue";
    static final String KEY_START_COLOR = "start_color";
    static final String KEY_END_COLOR = "end_color";
    static final String KEY_IMAGE_PALETTE = "image_palette";
    static final String KEY_IMAGE_URI = "image_palette_source_uri";
    static final String KEY_PALETTE_VIVIDNESS = "palette_vividness_percent";
    static final String KEY_BACKGROUND_MODE = "background_mode";
    static final String KEY_BACKGROUND_COLOR = "background_color";
    static final String KEY_BACKGROUND_IMAGE_OPACITY = "background_image_opacity_percent";
    static final String KEY_BACKGROUND_FIT_MODE = "background_fit_mode";
    static final String KEY_BACKGROUND_ZOOM = "background_zoom_percent";
    static final String KEY_BACKGROUND_OFFSET_X = "background_offset_x";
    static final String KEY_BACKGROUND_OFFSET_Y = "background_offset_y";
    static final String KEY_SPEED = "speed_percent";
    static final String KEY_GLYPH = "glyph_dp";
    static final String KEY_SPACING = "spacing_percent";
    static final String KEY_TAIL = "tail_cells";
    static final String KEY_PHRASE = "phrase_percent";
    static final String KEY_PHRASES = "hidden_phrases";
    static final String KEY_PARALLAX = "parallax";
    static final String KEY_FPS = "fps";
    static final String KEY_CYCLE_SECONDS = "cycle_seconds";

    static final int MODE_MORPHE = 0;
    static final int MODE_GREEN = 1;
    static final int MODE_CUSTOM = 2;
    static final int MODE_RAINBOW = 3;
    static final int MODE_IMAGE = 4;

    static final int BACKGROUND_SOLID = 0;
    static final int BACKGROUND_IMAGE = 1;

    static final int FIT_FILL = 0;
    static final int FIT_INSIDE = 1;
    static final int FIT_MANUAL = 2;

    static final int MORPHE_START = Color.rgb(0x1E, 0x5A, 0xA8);
    static final int MORPHE_END = Color.rgb(0x00, 0xAF, 0xAE);
    static final String DEFAULT_PHRASES = "USE MORPHE\nNO ADS\nWAKE UP\nPATCHED";

    private Config() {}

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int mode(SharedPreferences p) { return p.getInt(KEY_COLOR_MODE, MODE_MORPHE); }
    static float speed(SharedPreferences p) { return p.getInt(KEY_SPEED, 100) / 100f; }
    static float glyphDp(SharedPreferences p) { return p.getInt(KEY_GLYPH, 14); }
    static float spacing(SharedPreferences p) { return p.getInt(KEY_SPACING, 100) / 100f; }
    static int tail(SharedPreferences p) { return p.getInt(KEY_TAIL, 26); }
    static float phraseChance(SharedPreferences p) { return p.getInt(KEY_PHRASE, 12) / 100f; }
    static boolean parallax(SharedPreferences p) { return p.getBoolean(KEY_PARALLAX, true); }
    static int fps(SharedPreferences p) { return p.getInt(KEY_FPS, 60); }
    static int cycleSeconds(SharedPreferences p) { return p.getInt(KEY_CYCLE_SECONDS, 45); }
    static int paletteVividness(SharedPreferences p) { return p.getInt(KEY_PALETTE_VIVIDNESS, 75); }
    static String imageUri(SharedPreferences p) { return p.getString(KEY_IMAGE_URI, null); }
    static int backgroundMode(SharedPreferences p) { return p.getInt(KEY_BACKGROUND_MODE, BACKGROUND_SOLID); }
    static int backgroundColor(SharedPreferences p) { return p.getInt(KEY_BACKGROUND_COLOR, Color.BLACK); }
    static int backgroundImageOpacity(SharedPreferences p) {
        return Math.max(0, Math.min(100, p.getInt(KEY_BACKGROUND_IMAGE_OPACITY, 35)));
    }
    static int backgroundFitMode(SharedPreferences p) {
        return Math.max(FIT_FILL, Math.min(FIT_MANUAL, p.getInt(KEY_BACKGROUND_FIT_MODE, FIT_FILL)));
    }
    static int backgroundZoomPercent(SharedPreferences p) {
        return Math.max(50, Math.min(200, p.getInt(KEY_BACKGROUND_ZOOM, 100)));
    }
    static int backgroundOffsetX(SharedPreferences p) {
        return Math.max(-100, Math.min(100, p.getInt(KEY_BACKGROUND_OFFSET_X, 0)));
    }
    static int backgroundOffsetY(SharedPreferences p) {
        return Math.max(-100, Math.min(100, p.getInt(KEY_BACKGROUND_OFFSET_Y, 0)));
    }

    static int customStart(SharedPreferences p) {
        if (p.contains(KEY_START_COLOR)) return p.getInt(KEY_START_COLOR, MORPHE_START);
        float hue = p.getInt(KEY_START_HUE, 210);
        return Color.HSVToColor(new float[]{hue, 0.82f, 0.72f});
    }

    static int customEnd(SharedPreferences p) {
        if (p.contains(KEY_END_COLOR)) return p.getInt(KEY_END_COLOR, MORPHE_END);
        float hue = p.getInt(KEY_END_HUE, 180);
        return Color.HSVToColor(new float[]{hue, 0.88f, 0.76f});
    }

    static int[] gradient(SharedPreferences p, float timeMs) {
        int mode = mode(p);
        if (mode == MODE_GREEN) {
            return new int[]{Color.rgb(0x00, 0x9A, 0x55), Color.rgb(0x66, 0xFF, 0xAA)};
        }
        if (mode == MODE_CUSTOM) {
            return new int[]{customStart(p), customEnd(p)};
        }
        if (mode == MODE_RAINBOW) {
            float seconds = Math.max(10, cycleSeconds(p));
            float base = ((timeMs / 1000f) / seconds * 360f) % 360f;
            return new int[]{
                    Color.HSVToColor(new float[]{base, 0.82f, 0.75f}),
                    Color.HSVToColor(new float[]{(base + 55f) % 360f, 0.88f, 0.82f})
            };
        }
        if (mode == MODE_IMAGE) {
            return imagePalette(p);
        }
        return new int[]{MORPHE_START, MORPHE_END};
    }

    static String phrasesText(SharedPreferences p) {
        return p.getString(KEY_PHRASES, DEFAULT_PHRASES);
    }

    static String[] phrases(SharedPreferences p) {
        String raw = phrasesText(p);
        if (raw == null) raw = DEFAULT_PHRASES;
        String[] lines = raw.replace('\r', '\n').split("\\n+");
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            String phrase = line.trim();
            if (phrase.isEmpty()) continue;
            if (phrase.length() > 32) phrase = phrase.substring(0, 32);
            result.add(phrase);
            if (result.size() >= 20) break;
        }
        if (result.isEmpty()) return DEFAULT_PHRASES.split("\\n");
        return result.toArray(new String[0]);
    }

    static int[] imagePalette(SharedPreferences p) {
        String raw = p.getString(KEY_IMAGE_PALETTE, null);
        if (raw == null || raw.trim().isEmpty()) {
            return new int[]{MORPHE_START, MORPHE_END};
        }
        String[] parts = raw.split(",");
        List<Integer> colors = new ArrayList<>();
        for (String part : parts) {
            try {
                colors.add(Color.parseColor(part.trim()));
            } catch (IllegalArgumentException ignored) {}
        }
        if (colors.size() < 2) return new int[]{MORPHE_START, MORPHE_END};
        int[] out = new int[colors.size()];
        for (int i = 0; i < colors.size(); i++) out[i] = colors.get(i);
        return out;
    }

    static String encodePalette(int[] colors) {
        if (colors == null || colors.length == 0) return "";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < colors.length; i++) {
            if (i > 0) builder.append(',');
            builder.append(String.format(Locale.ROOT, "#%02X%02X%02X",
                    Color.red(colors[i]), Color.green(colors[i]), Color.blue(colors[i])));
        }
        return builder.toString();
    }

    static void reset(SharedPreferences p) {
        p.edit().clear().apply();
    }
}
