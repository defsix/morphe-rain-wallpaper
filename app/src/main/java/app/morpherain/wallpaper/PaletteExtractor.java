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
        if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
            return new int[]{Config.MORPHE_START, Config.MORPHE_END};
        }

        int target = Math.max(2, Math.min(7, wanted));
        Bitmap sample = source;
        int maxSide = Math.max(source.getWidth(), source.getHeight());
        if (maxSide > 96) {
            float scale = 96f / maxSide;
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
            if (value < 0.10f) continue;
            if (value > 0.985f && sat < 0.08f) continue;

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
            float vividness = 0.35f + hsv[1] * 0.65f;
            float brightness = 0.6f + Math.min(1f, hsv[2] * 1.1f) * 0.4f;
            float score = entry.getValue() * vividness * brightness;
            candidates.add(new Bucket(color, entry.getValue(), score));
        }

        Collections.sort(candidates, (a, b) -> Float.compare(b.score, a.score));
        int candidateLimit = Math.min(80, candidates.size());
        List<Integer> selected = new ArrayList<>(target);

        if (!candidates.isEmpty()) selected.add(candidates.get(0).color);
        while (selected.size() < target && selected.size() < candidateLimit) {
            Bucket best = null;
            float bestScore = -1f;
            for (int i = 0; i < candidateLimit; i++) {
                Bucket candidate = candidates.get(i);
                if (selected.contains(candidate.color)) continue;
                float minDistance = Float.MAX_VALUE;
                for (int existing : selected) {
                    minDistance = Math.min(minDistance, colorDistance(candidate.color, existing));
                }
                float diversity = Math.max(0.05f, minDistance);
                float weighted = diversity * (float) Math.sqrt(candidate.count) * (0.4f + candidate.score / Math.max(1f, candidates.get(0).score));
                if (weighted > bestScore) {
                    bestScore = weighted;
                    best = candidate;
                }
            }
            if (best == null) break;
            selected.add(best.color);
        }

        if (selected.size() < 2) {
            selected.clear();
            selected.add(Config.MORPHE_START);
            selected.add(Config.MORPHE_END);
        }

        final float[] tmp = new float[3];
        selected.sort(new Comparator<Integer>() {
            @Override public int compare(Integer a, Integer b) {
                Color.colorToHSV(a, tmp);
                float ah = tmp[0];
                float as = tmp[1];
                Color.colorToHSV(b, tmp);
                float bh = tmp[0];
                float bs = tmp[1];
                if (as < 0.12f && bs >= 0.12f) return -1;
                if (bs < 0.12f && as >= 0.12f) return 1;
                return Float.compare(ah, bh);
            }
        });

        int[] out = new int[selected.size()];
        for (int i = 0; i < selected.size(); i++) out[i] = selected.get(i);
        if (sample != source) sample.recycle();
        return out;
    }

    private static float colorDistance(int a, int b) {
        float dr = (Color.red(a) - Color.red(b)) / 255f;
        float dg = (Color.green(a) - Color.green(b)) / 255f;
        float db = (Color.blue(a) - Color.blue(b)) / 255f;
        return (float) Math.sqrt(dr * dr * 0.30f + dg * dg * 0.59f + db * db * 0.11f);
    }

    private static final class Bucket {
        final int color;
        final int count;
        final float score;

        Bucket(int color, int count, float score) {
            this.color = color;
            this.count = count;
            this.score = score;
        }
    }
}
