package app.morpherain.wallpaper;

import android.app.WallpaperManager;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Standalone live-wallpaper port of Morphe Manager's MatrixBackground.kt renderer.
 * Original project: https://github.com/MorpheApp/morphe-manager
 * License: GPL-3.0
 */
public class RainWallpaperService extends WallpaperService {

    @Override
    public Engine onCreateEngine() {
        return new RainEngine();
    }

    private final class RainEngine extends Engine implements
            SharedPreferences.OnSharedPreferenceChangeListener,
            SensorEventListener {

        private static final float ROW_STEP_RATIO = 1.02f;
        private static final float MUTATION_INTERVAL_MS = 90f;
        private static final float CYCLE_MS = 120_000f;
        private static final float CYCLE_FADE_MS = 1_500f;
        private static final float MAX_ALPHA = 0.85f;
        private static final float EXTRA_STREAM_CHANCE = 0.45f;
        private static final int STREAMS_PER_COLUMN = 2;

        private final char[] randomGlyphs = new char[]{
                '0','1','0','1','0','1','A','B','C','D','E','F',
                '2','3','4','5','6','7','8','9',':',';','/','<','>'
        };
        private final String[] phrases = new String[]{"USE MORPHE", "NO ADS", "WAKE UP", "PATCHED"};
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Random random = new Random();
        private final SharedPreferences prefs = Config.prefs(RainWallpaperService.this);
        private final Paint glyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Rect source = new Rect();
        private final RectF destination = new RectF();

        private SensorManager sensorManager;
        private Sensor accelerometer;
        private boolean sensorRegistered;
        private boolean visible;
        private boolean surfaceReady;
        private boolean rebuildRequired = true;

        private int width;
        private int height;
        private float density;
        private float glyphPx;
        private float columnStep;
        private float rowStep;
        private int rowCount;
        private int tailCells;
        private List<MatrixColumn> columns = new ArrayList<>();
        private GlyphAtlas atlas;

        private long lastFrameMs;
        private float animatedTimeMs;
        private float currentSpeed = 1f;

        private float baselineX;
        private float baselineY;
        private boolean calibrated;
        private float tiltTargetX;
        private float tiltTargetY;
        private float tiltX;
        private float tiltY;

        private final Runnable drawRunnable = new Runnable() {
            @Override public void run() {
                drawFrame();
                if (visible && surfaceReady) {
                    int fps = Math.max(15, Math.min(60, Config.fps(prefs)));
                    handler.postDelayed(this, Math.max(1, 1000 / fps));
                }
            }
        };

        RainEngine() {
            density = getResources().getDisplayMetrics().density;
            prefs.registerOnSharedPreferenceChangeListener(this);
            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            if (sensorManager != null) {
                accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            handler.removeCallbacks(drawRunnable);
            unregisterSensor();
            prefs.unregisterOnSharedPreferenceChangeListener(this);
            if (atlas != null) atlas.recycle();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            this.visible = visible;
            if (visible) {
                lastFrameMs = SystemClock.uptimeMillis();
                updateSensorRegistration();
                handler.removeCallbacks(drawRunnable);
                handler.post(drawRunnable);
            } else {
                handler.removeCallbacks(drawRunnable);
                unregisterSensor();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            this.width = width;
            this.height = height;
            surfaceReady = true;
            rebuildRequired = true;
            drawFrame();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            surfaceReady = false;
            handler.removeCallbacks(drawRunnable);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (Config.KEY_GLYPH.equals(key) || Config.KEY_SPACING.equals(key) ||
                    Config.KEY_TAIL.equals(key) || Config.KEY_PHRASE.equals(key)) {
                rebuildRequired = true;
            }
            if (Config.KEY_PARALLAX.equals(key)) updateSensorRegistration();
            handler.removeCallbacks(drawRunnable);
            if (visible && surfaceReady) handler.post(drawRunnable);
        }

        private void rebuild() {
            if (width <= 0 || height <= 0) return;

            glyphPx = Config.glyphDp(prefs) * density;
            float spacing = Math.max(0.7f, Config.spacing(prefs));
            columnStep = glyphPx * spacing;
            rowStep = glyphPx * ROW_STEP_RATIO;
            rowCount = Math.max(1, (int) (height / rowStep));
            tailCells = Config.tail(prefs);

            if (atlas != null) atlas.recycle();
            atlas = new GlyphAtlas(glyphPx, randomGlyphs, phrases);

            int columnCount = Math.max(1, (int) (width / columnStep));
            List<MatrixColumn> next = new ArrayList<>(columnCount);
            float phraseChance = Config.phraseChance(prefs);
            for (int index = 0; index < columnCount; index++) {
                List<MatrixStream> streams = new ArrayList<>(3);
                for (int i = 0; i < STREAMS_PER_COLUMN; i++) streams.add(randomStream(phraseChance));
                if (random.nextFloat() < EXTRA_STREAM_CHANCE) streams.add(randomStream(phraseChance));
                next.add(new MatrixColumn(random.nextInt(), index % 3 == 0, streams));
            }
            columns = next;
            rebuildRequired = false;
        }

        private MatrixStream randomStream(float phraseChance) {
            String phrase = random.nextFloat() < phraseChance ? phrases[random.nextInt(phrases.length)] : null;
            int[] phraseGlyphs = phrase == null ? null : atlas.toIndices(phrase);
            float fallSpeed = phrase != null
                    ? 0.002f + random.nextFloat() * 0.002f
                    : 0.004f + random.nextFloat() * 0.007f;
            int tail = phraseGlyphs != null
                    ? phraseGlyphs.length
                    : Math.max(4, (int) (tailCells * (0.45f + random.nextFloat() * 0.75f)));
            return new MatrixStream(random.nextFloat(), fallSpeed, tail, phraseGlyphs);
        }

        private void drawFrame() {
            if (!surfaceReady) return;
            if (rebuildRequired || atlas == null) rebuild();
            if (atlas == null || columns.isEmpty()) return;

            long now = SystemClock.uptimeMillis();
            float delta = Math.min(64f, Math.max(0f, now - lastFrameMs));
            lastFrameMs = now;
            float targetSpeed = Config.speed(prefs);
            currentSpeed += (targetSpeed - currentSpeed) * Math.min(1f, delta / 1000f * 2.5f);
            animatedTimeMs += delta * currentSpeed;
            if (animatedTimeMs > 10_000_000f) animatedTimeMs %= CYCLE_MS;

            tiltX += (tiltTargetX - tiltX) * Math.min(1f, delta * 0.012f);
            tiltY += (tiltTargetY - tiltY) * Math.min(1f, delta * 0.012f);

            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas == null) return;
                canvas.drawColor(Color.BLACK);
                renderRain(canvas, animatedTimeMs);
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas);
            }
        }

        private void renderRain(Canvas canvas, float timeMs) {
            float globalTime = timeMs % CYCLE_MS;
            float cycleFade;
            if (globalTime < CYCLE_FADE_MS) cycleFade = globalTime / CYCLE_FADE_MS;
            else if (globalTime > CYCLE_MS - CYCLE_FADE_MS) cycleFade = (CYCLE_MS - globalTime) / CYCLE_FADE_MS;
            else cycleFade = 1f;

            int mutationTick = (int) (globalTime / MUTATION_INTERVAL_MS);
            int travel = rowCount + tailCells * 2;
            float parallaxX = Config.parallax(prefs) ? tiltX * 40f : 0f;
            float parallaxY = Config.parallax(prefs) ? tiltY * 40f : 0f;
            float half = atlas.cell / 2f;
            int[] gradient = Config.gradient(prefs, timeMs);

            int count = columns.size();
            for (int columnIndex = 0; columnIndex < count; columnIndex++) {
                MatrixColumn column = columns.get(columnIndex);
                float x = columnIndex * columnStep + columnStep / 2f + parallaxX;
                float position = count == 1 ? 0f : columnIndex / (float) (count - 1);
                int trailColor = blend(gradient[0], gradient[1], position);
                int headColor = blend(trailColor, Color.WHITE, 0.55f);

                for (MatrixStream stream : column.streams) {
                    float depthAlpha = (column.dimmed && stream.phrase == null) ? 0.6f : 1f;
                    float head = (globalTime * stream.fallSpeed + stream.phase * travel) % travel;

                    for (int offset = 0; offset < stream.tail; offset++) {
                        float alpha = stream.fadeAt(offset) * depthAlpha * MAX_ALPHA * cycleFade;
                        if (alpha < 0.02f) break;
                        int row = (int) head - offset;
                        if (row < 0 || row > rowCount) continue;

                        int glyphIndex = stream.phraseGlyphAt(offset);
                        if (glyphIndex == MatrixStream.NOT_A_PHRASE) {
                            int randomSlot = column.randomGlyphAt(row, mutationTick, randomGlyphs.length);
                            glyphIndex = atlas.randomGlyphAtlasIndex[randomSlot];
                        }
                        if (glyphIndex == GlyphAtlas.BLANK) continue;

                        float centerY = row * rowStep + rowStep / 2f + parallaxY;
                        int color = offset == 0 ? headColor : trailColor;
                        glyphPaint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
                        glyphPaint.setAlpha(Math.max(0, Math.min(255, (int) (alpha * 255f))));
                        atlas.selectGlyph(glyphIndex, source);
                        destination.set(x - half, centerY - half, x + half, centerY + half);
                        canvas.drawBitmap(atlas.bitmap, source, destination, glyphPaint);
                    }
                }
            }
            glyphPaint.setColorFilter(null);
            glyphPaint.setAlpha(255);
        }

        private int blend(int from, int to, float fraction) {
            fraction = Math.max(0f, Math.min(1f, fraction));
            int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * fraction);
            int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * fraction);
            int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * fraction);
            return Color.rgb(r, g, b);
        }

        private void updateSensorRegistration() {
            boolean shouldRegister = visible && Config.parallax(prefs) && accelerometer != null;
            if (shouldRegister && !sensorRegistered) {
                calibrated = false;
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
                sensorRegistered = true;
            } else if (!shouldRegister && sensorRegistered) {
                unregisterSensor();
            }
        }

        private void unregisterSensor() {
            if (sensorRegistered && sensorManager != null) {
                sensorManager.unregisterListener(this);
            }
            sensorRegistered = false;
            calibrated = false;
            tiltTargetX = tiltTargetY = 0f;
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
            if (!calibrated) {
                baselineX = event.values[0];
                baselineY = event.values[1];
                calibrated = true;
            }
            float rawX = event.values[0] - baselineX;
            float rawY = -(event.values[1] - baselineY);
            float nextX = rawX * 0.15f;
            float nextY = rawY * 0.15f;
            if (Math.hypot(nextX - tiltTargetX, nextY - tiltTargetY) > 0.02f) {
                tiltTargetX = nextX;
                tiltTargetY = nextY;
            }
        }

        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    }

    private static final class MatrixStream {
        static final int NOT_A_PHRASE = Integer.MIN_VALUE;
        final float phase;
        final float fallSpeed;
        final int tail;
        final int[] phrase;

        MatrixStream(float phase, float fallSpeed, int tail, int[] phrase) {
            this.phase = phase;
            this.fallSpeed = fallSpeed;
            this.tail = tail;
            this.phrase = phrase;
        }

        int phraseGlyphAt(int offset) {
            if (phrase == null) return NOT_A_PHRASE;
            return phrase[phrase.length - 1 - offset];
        }

        float fadeAt(int offset) {
            float progress = offset / (float) tail;
            if (phrase == null) {
                float v = 1f - progress;
                return v * v;
            }
            return 1f - progress * 0.4f;
        }
    }

    private static final class MatrixColumn {
        final int seed;
        final boolean dimmed;
        final List<MatrixStream> streams;

        MatrixColumn(int seed, boolean dimmed, List<MatrixStream> streams) {
            this.seed = seed;
            this.dimmed = dimmed;
            this.streams = streams;
        }

        int randomGlyphAt(int row, int tick, int glyphCount) {
            int hash = (seed * 73856093) ^ (row * 19349663) ^ (tick * 83492791);
            return (hash & Integer.MAX_VALUE) % glyphCount;
        }
    }

    private static final class GlyphAtlas {
        static final int BLANK = -1;
        final Bitmap bitmap;
        final int cell;
        final Map<Character, Integer> index = new LinkedHashMap<>();
        final int[] randomGlyphAtlasIndex;

        GlyphAtlas(float glyphPx, char[] randomGlyphs, String[] phrases) {
            for (char c : randomGlyphs) add(c);
            for (String phrase : phrases) {
                for (char c : phrase.toCharArray()) if (c != ' ') add(c);
            }

            cell = Math.max(1, (int) Math.ceil(glyphPx * 1.35f));
            bitmap = Bitmap.createBitmap(cell * index.size(), cell, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setTypeface(Typeface.MONOSPACE);
            paint.setTextSize(glyphPx);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(Color.WHITE);
            Paint.FontMetrics fm = paint.getFontMetrics();
            float baseline = cell / 2f - (fm.descent + fm.ascent) / 2f;

            int i = 0;
            for (Character c : index.keySet()) {
                canvas.drawText(String.valueOf(c), i * cell + cell / 2f, baseline, paint);
                i++;
            }

            randomGlyphAtlasIndex = new int[randomGlyphs.length];
            for (i = 0; i < randomGlyphs.length; i++) {
                randomGlyphAtlasIndex[i] = index.get(randomGlyphs[i]);
            }
        }

        private void add(char c) {
            if (!index.containsKey(c)) index.put(c, index.size());
        }

        int[] toIndices(String text) {
            int[] out = new int[text.length()];
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                out[i] = c == ' ' ? BLANK : index.getOrDefault(c, BLANK);
            }
            return out;
        }

        void selectGlyph(int glyph, Rect into) {
            into.set(glyph * cell, 0, (glyph + 1) * cell, cell);
        }

        void recycle() {
            if (!bitmap.isRecycled()) bitmap.recycle();
        }
    }
}
