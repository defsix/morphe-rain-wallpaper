package app.morpherain.wallpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

final class Config {
    static final String PREFS = "morphe_rain_settings";

    static final String KEY_COLOR_MODE = "color_mode";
    static final String KEY_START_HUE = "start_hue";
    static final String KEY_END_HUE = "end_hue";
    static final String KEY_SPEED = "speed_percent";
    static final String KEY_GLYPH = "glyph_dp";
    static final String KEY_SPACING = "spacing_percent";
    static final String KEY_TAIL = "tail_cells";
    static final String KEY_PHRASE = "phrase_percent";
    static final String KEY_PARALLAX = "parallax";
    static final String KEY_FPS = "fps";
    static final String KEY_CYCLE_SECONDS = "cycle_seconds";

    static final int MODE_MORPHE = 0;
    static final int MODE_GREEN = 1;
    static final int MODE_CUSTOM = 2;
    static final int MODE_RAINBOW = 3;

    static final int MORPHE_START = Color.rgb(0x1E, 0x5A, 0xA8);
    static final int MORPHE_END = Color.rgb(0x00, 0xAF, 0xAE);

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

    static int[] gradient(SharedPreferences p, float timeMs) {
        int mode = mode(p);
        if (mode == MODE_GREEN) {
            return new int[]{Color.rgb(0x00, 0x9A, 0x55), Color.rgb(0x66, 0xFF, 0xAA)};
        }
        if (mode == MODE_CUSTOM) {
            float startHue = p.getInt(KEY_START_HUE, 210);
            float endHue = p.getInt(KEY_END_HUE, 180);
            return new int[]{
                    Color.HSVToColor(new float[]{startHue, 0.82f, 0.72f}),
                    Color.HSVToColor(new float[]{endHue, 0.88f, 0.76f})
            };
        }
        if (mode == MODE_RAINBOW) {
            float seconds = Math.max(10, cycleSeconds(p));
            float base = ((timeMs / 1000f) / seconds * 360f) % 360f;
            return new int[]{
                    Color.HSVToColor(new float[]{base, 0.82f, 0.75f}),
                    Color.HSVToColor(new float[]{(base + 55f) % 360f, 0.88f, 0.82f})
            };
        }
        return new int[]{MORPHE_START, MORPHE_END};
    }

    static void reset(SharedPreferences p) {
        p.edit().clear().apply();
    }
}
