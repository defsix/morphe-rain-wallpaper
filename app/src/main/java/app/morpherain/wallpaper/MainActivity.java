package app.morpherain.wallpaper;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

public class MainActivity extends Activity {
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
        TextView subtitle = text("Standalone live wallpaper using the visual recipe from Morphe Manager's GPL-3.0 Matrix Easter egg.", 14, Color.LTGRAY);
        subtitle.setPadding(0, dp(6), 0, dp(20));
        content.addView(subtitle);

        Button set = button("Set live wallpaper");
        set.setOnClickListener(v -> openWallpaperPicker());
        content.addView(set);

        addSection("Colour");
        Spinner mode = new Spinner(this);
        String[] modes = {"Morphe blue → cyan", "Classic green", "Custom gradient", "Slow colour cycle"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modes);
        mode.setAdapter(adapter);
        mode.setSelection(Config.mode(prefs));
        mode.setOnItemSelectedListener(new SimpleItemSelectedListener(position ->
                prefs.edit().putInt(Config.KEY_COLOR_MODE, position).apply()));
        content.addView(mode, matchWrap());

        addSeek("Custom start hue", 0, 359, prefs.getInt(Config.KEY_START_HUE, 210),
                value -> prefs.edit().putInt(Config.KEY_START_HUE, value).apply(), "°");
        addSeek("Custom end hue", 0, 359, prefs.getInt(Config.KEY_END_HUE, 180),
                value -> prefs.edit().putInt(Config.KEY_END_HUE, value).apply(), "°");
        addSeek("Colour-cycle period", 10, 120, Config.cycleSeconds(prefs),
                value -> prefs.edit().putInt(Config.KEY_CYCLE_SECONDS, value).apply(), " s");

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

        TextView note = text("Default preset: #1E5AA8 → #00AFAE, 14dp monospace glyphs, 26-cell tails, 2–3 streams per column, 12% phrase chance and parallax enabled.", 12, Color.GRAY);
        note.setPadding(0, dp(18), 0, 0);
        content.addView(note);

        setContentView(scroll);
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
