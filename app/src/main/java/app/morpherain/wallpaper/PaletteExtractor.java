package app.morpherain.wallpaper;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class PaletteExtractor {
    private PaletteExtractor() {}

    static int[] extract(Bitmap source, int wanted) {
        return extract(source, wanted, 75);
    }

    static int[] extract(Bitmap source, int wanted, int vividnessPercent) {
        if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
            return new int[]{Config.MORPHE_START, Config.MORPHE_END};
        }

        int target = Math.max(2, Math.min(7, wanted));
        float vividBias = Math.max(0f, Math.min(1f, vividnessPercent / 100f));

        Bitmap sample = source;
        int maxSide = Math.max(source.getWidth(), source.getHeight());
        if (maxSide > 128) {
            float scale = 128f / maxSide;
            int w = Math.max(1, Math.round(source.getWidth() * scale));
            int h = Math.max(1, Math.round(source.getHeight() * scale));
            sample = Bitmap.createScaledBitmap(source, w, h, true);
        }

        Map<Integer, Integer> histogram = new HashMap<>();
        float[] hsv = new float[3];
        int width = sample.getWidth();
        int height = sample.getHeight();
        int[] pixels = new int[width * height];
        sample.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int color : pixels) {
            if (Color.alpha(color) < 160) continue;
            Color.colorToHSV(color, hsv);
            float sat = hsv[1];
            float value = hsv[2];

            // Discard true background black and almost-white noise. Bright pale highlights
            // are deliberately retained because they can make a useful final gradient stop.
            if (value < 0.075f) continue;
            if (value > 0.992f && sat < 0.035f) continue;

            int r = Color.red(color) >> 3;
            int g = Color.green(color) >> 3;
            int b = Color.blue(color) >> 3;
            int key = (r << 10) | (g << 5) | b;
            histogram.put(key, histogram.getOrDefault(key, 0) + 1);
        }

        if (histogram.isEmpty()) {
            if (sample != source) sample.recycle();
            return new int[]{Config.MORPHE_START, Config.MORPHE_END};
        }

        List<Bucket> candidates = new ArrayList<>(histogram.size());
        for (Map.Entry<Integer, Integer> entry : histogram.entrySet()) {
            int key = entry.getKey();
            int r = (((key >> 10) & 31) << 3) + 4;
            int g = (((key >> 5) & 31) << 3) + 4;
            int b = ((key & 31) << 3) + 4;
            int color = Color.rgb(Math.min(255, r), Math.min(255, g), Math.min(255, b));
            Color.colorToHSV(color, hsv);

            float sat = hsv[1];
            float value = hsv[2];
            float population = (float) Math.pow(entry.getValue(), 0.47);
            float saturationWeight = 0.20f + 1.55f * (float) Math.pow(sat, 0.90);
            float brightnessWeight = 0.38f + 0.82f * (float) Math.sqrt(value);

            // At higher vividness settings, population matters less and chroma matters more.
            float vividWeight = 1f + vividBias * (1.95f * sat + 0.35f * value);
            float dullPenalty = 1f;
            if (sat < 0.12f && value < 0.72f) {
                dullPenalty = 0.12f + (1f - vividBias) * 0.40f;
            } else if (sat < 0.22f && value < 0.62f) {
                dullPenalty = 0.35f + (1f - vividBias) * 0.35f;
            }

            float score = population * saturationWeight * brightnessWeight * vividWeight * dullPenalty;
            candidates.add(new Bucket(color, entry.getValue(), score, hsv[0], sat, value));
        }

        Collections.sort(candidates, (a, b) -> Float.compare(b.score, a.score));
        int candidateLimit = Math.min(140, candidates.size());
        List<Bucket> selected = new ArrayList<>(target);

        if (!candidates.isEmpty()) selected.add(candidates.get(0));
        while (selected.size() < target) {
            Bucket best = null;
            float bestScore = -1f;
            int neutralCount = 0;
            for (Bucket chosen : selected) if (chosen.saturation < 0.16f) neutralCount++;

            for (int i = 0; i < candidateLimit; i++) {
                Bucket candidate = candidates.get(i);
                if (containsColor(selected, candidate.color)) continue;
                if (candidate.saturation < 0.10f && candidate.value < 0.70f) continue;
                if (candidate.saturation < 0.16f && neutralCount >= 1) continue;

                float minDistance = Float.MAX_VALUE;
                for (Bucket existing : selected) {
                    minDistance = Math.min(minDistance, perceptualDistance(candidate, existing));
                }
                if (minDistance < 0.12f) continue;

                float diversity = 0.40f + minDistance * 1.85f;
                float weighted = candidate.score * diversity;
                if (weighted > bestScore) {
                    bestScore = weighted;
                    best = candidate;
                }
            }
            if (best == null) break;
            selected.add(best);
        }

        if (selected.size() < 2) {
            if (sample != source) sample.recycle();
            return new int[]{Config.MORPHE_START, Config.MORPHE_END};
        }

        List<Integer> colors = new ArrayList<>(selected.size());
        for (Bucket bucket : selected) colors.add(boostVibrance(bucket.color, vividBias));
        orderForGradient(colors);

        int[] out = new int[colors.size()];
        for (int i = 0; i < colors.size(); i++) out[i] = colors.get(i);
        if (sample != source) sample.recycle();
        return out;
    }

    private static boolean containsColor(List<Bucket> selected, int color) {
        for (Bucket bucket : selected) if (bucket.color == color) return true;
        return false;
    }

    private static float perceptualDistance(Bucket a, Bucket b) {
        float hueDelta = Math.abs(a.hue - b.hue);
        hueDelta = Math.min(hueDelta, 360f - hueDelta) / 180f;
        float chroma = Math.max(0.20f, Math.min(a.saturation, b.saturation));
        float ds = a.saturation - b.saturation;
        float dv = a.value - b.value;

        float dr = (Color.red(a.color) - Color.red(b.color)) / 255f;
        float dg = (Color.green(a.color) - Color.green(b.color)) / 255f;
        float db = (Color.blue(a.color) - Color.blue(b.color)) / 255f;
        float rgb = (float) Math.sqrt(dr * dr * 0.30f + dg * dg * 0.59f + db * db * 0.11f);

        return (float) Math.sqrt(
                hueDelta * hueDelta * chroma * 0.72f +
                ds * ds * 0.34f +
                dv * dv * 0.24f +
                rgb * rgb * 0.58f);
    }

    private static int boostVibrance(int color, float vividBias) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);

        if (hsv[1] >= 0.08f) {
            float saturationBoost = 0.10f + 0.34f * vividBias;
            hsv[1] = clamp(hsv[1] + (1f - hsv[1]) * saturationBoost, 0f, 1f);
        }
        float brightnessBoost = 0.03f + 0.12f * vividBias;
        hsv[2] = clamp(hsv[2] + (1f - hsv[2]) * brightnessBoost, 0f, 1f);
        return Color.HSVToColor(hsv);
    }

    private static void orderForGradient(List<Integer> colors) {
        colors.sort(new Comparator<Integer>() {
            @Override public int compare(Integer a, Integer b) {
                float[] ah = new float[3];
                float[] bh = new float[3];
                Color.colorToHSV(a, ah);
                Color.colorToHSV(b, bh);

                boolean aNeutral = ah[1] < 0.14f;
                boolean bNeutral = bh[1] < 0.14f;
                if (aNeutral != bNeutral) return aNeutral ? 1 : -1;
                if (aNeutral) return Float.compare(ah[2], bh[2]);
                return Float.compare(ah[0], bh[0]);
            }
        });
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class Bucket {
        final int color;
        final int count;
        final float score;
        final float hue;
        final float saturation;
        final float value;

        Bucket(int color, int count, float score, float hue, float saturation, float value) {
            this.color = color;
            this.count = count;
            this.score = score;
            this.hue = hue;
            this.saturation = saturation;
            this.value = value;
        }
    }
}
