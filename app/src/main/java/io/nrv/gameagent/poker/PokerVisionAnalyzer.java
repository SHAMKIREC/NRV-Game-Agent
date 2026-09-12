package io.nrv.gameagent.poker;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lightweight offline screenshot analyzer for Poker Lab.
 * Uses a calibrated World Poker Club profile first, then generic card detection as fallback.
 */
public final class PokerVisionAnalyzer {
    private static final int MAX_WIDTH = 720;
    private final WorldPokerClubProfile worldPokerClubProfile = new WorldPokerClubProfile();

    public PokerVisionObservation analyze(Bitmap source) {
        if (source == null || source.getWidth() < 40 || source.getHeight() < 40) {
            return new PokerVisionObservation(0, 0, 0, List.of(), 0.0,
                    "unknown", false, false, false, false, 0, false, 0);
        }

        String captureOrientation = source.getWidth() >= source.getHeight() ? "landscape" : "portrait";
        boolean normalized = false;
        Bitmap working = source;

        if (source.getHeight() > source.getWidth()) {
            Matrix matrix = new Matrix();
            matrix.postRotate(90f);
            working = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
            normalized = true;
        }

        Bitmap bitmap = working;
        if (working.getWidth() > MAX_WIDTH) {
            int scaledHeight = Math.max(1, Math.round(working.getHeight() * (MAX_WIDTH / (float) working.getWidth())));
            bitmap = Bitmap.createScaledBitmap(working, MAX_WIDTH, scaledHeight, true);
            if (working != source) working.recycle();
        }

        WorldPokerClubProfile.Result wpc = worldPokerClubProfile.analyze(bitmap);

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        boolean[] mask = new boolean[width * height];
        boolean[] seen = new boolean[width * height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int color = bitmap.getPixel(x, y);
                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                int brightness = (r + g + b) / 3;
                mask[y * width + x] = brightness >= 165 && max - min <= 125;
            }
        }

        List<PokerVisionObservation.Region> regions = new ArrayList<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (!mask[index] || seen[index]) continue;

                seen[index] = true;
                queue.add(index);
                int minX = x, maxX = x, minY = y, maxY = y, count = 0;

                while (!queue.isEmpty()) {
                    int current = queue.removeFirst();
                    int cx = current % width;
                    int cy = current / width;
                    count++;
                    minX = Math.min(minX, cx);
                    maxX = Math.max(maxX, cx);
                    minY = Math.min(minY, cy);
                    maxY = Math.max(maxY, cy);

                    if (cx > 0) visit(current - 1, mask, seen, queue);
                    if (cx + 1 < width) visit(current + 1, mask, seen, queue);
                    if (cy > 0) visit(current - width, mask, seen, queue);
                    if (cy + 1 < height) visit(current + width, mask, seen, queue);
                }

                int boxWidth = maxX - minX + 1;
                int boxHeight = maxY - minY + 1;
                double wf = boxWidth / (double) width;
                double hf = boxHeight / (double) height;
                double aspect = boxWidth / (double) Math.max(1, boxHeight);
                double fill = count / (double) Math.max(1, boxWidth * boxHeight);

                if (wf >= 0.025 && wf <= 0.17
                        && hf >= 0.07 && hf <= 0.34
                        && aspect >= 0.38 && aspect <= 1.05
                        && fill >= 0.20) {
                    regions.add(new PokerVisionObservation.Region(
                            minX / (double) width,
                            minY / (double) height,
                            (maxX + 1) / (double) width,
                            (maxY + 1) / (double) height
                    ));
                }
            }
        }

        regions.sort(Comparator.comparingDouble(PokerVisionObservation.Region::centerY)
                .thenComparingDouble(PokerVisionObservation.Region::centerX));

        int genericHero = 0;
        int genericBoard = 0;
        for (PokerVisionObservation.Region region : regions) {
            double cy = region.centerY();
            double cx = region.centerX();
            if (cy >= 0.58 && cy <= 0.90 && cx >= 0.34 && cx <= 0.66) {
                genericHero++;
            } else if (cy >= 0.25 && cy <= 0.62 && cx >= 0.25 && cx <= 0.75) {
                genericBoard++;
            }
        }

        genericHero = Math.min(2, genericHero);
        genericBoard = Math.min(5, genericBoard);

        int heroCards = wpc.matched() ? wpc.heroCards() : genericHero;
        int boardCards = wpc.matched() ? wpc.boardCards() : genericBoard;
        boolean tableDetected = wpc.matched() || greenRatio(bitmap, 0.23, 0.18, 0.77, 0.70) >= 0.18;
        boolean heroZoneDetected = heroCards > 0
                || brightRatio(bitmap, 0.34, 0.58, 0.66, 0.90) >= 0.025;
        boolean boardZoneDetected = boardCards > 0
                || brightRatio(bitmap, 0.25, 0.25, 0.75, 0.62) >= 0.020;
        int seatActivity = wpc.matched() ? wpc.players() : estimateSeatActivity(bitmap);
        int playersDetected = wpc.matched() ? wpc.players() : Math.max(0, Math.min(9, seatActivity));

        double confidence;
        if (wpc.matched()) {
            confidence = wpc.completeness();
        } else {
            confidence = 0.0;
            if (tableDetected) confidence += 0.35;
            if (heroZoneDetected) confidence += 0.25;
            if (boardZoneDetected) confidence += 0.25;
            confidence += Math.min(0.15, regions.size() * 0.02);
        }

        if (bitmap != source) bitmap.recycle();

        return new PokerVisionObservation(
                regions.size(),
                heroCards,
                boardCards,
                regions,
                confidence,
                captureOrientation,
                normalized,
                tableDetected,
                heroZoneDetected,
                boardZoneDetected,
                seatActivity,
                wpc.matched(),
                playersDetected
        );
    }

    private static double greenRatio(Bitmap bitmap, double l, double t, double r, double b) {
        int x0 = clamp((int) Math.round(l * bitmap.getWidth()), 0, bitmap.getWidth() - 1);
        int y0 = clamp((int) Math.round(t * bitmap.getHeight()), 0, bitmap.getHeight() - 1);
        int x1 = clamp((int) Math.round(r * bitmap.getWidth()), x0 + 1, bitmap.getWidth());
        int y1 = clamp((int) Math.round(b * bitmap.getHeight()), y0 + 1, bitmap.getHeight());
        int total = 0, green = 0;
        int step = Math.max(1, bitmap.getWidth() / 360);
        for (int y = y0; y < y1; y += step) {
            for (int x = x0; x < x1; x += step) {
                int c = bitmap.getPixel(x, y);
                int rr = Color.red(c), gg = Color.green(c), bb = Color.blue(c);
                total++;
                if (gg > 70 && gg > rr * 1.10 && gg > bb * 1.08) green++;
            }
        }
        return total == 0 ? 0.0 : green / (double) total;
    }

    private static double brightRatio(Bitmap bitmap, double l, double t, double r, double b) {
        int x0 = clamp((int) Math.round(l * bitmap.getWidth()), 0, bitmap.getWidth() - 1);
        int y0 = clamp((int) Math.round(t * bitmap.getHeight()), 0, bitmap.getHeight() - 1);
        int x1 = clamp((int) Math.round(r * bitmap.getWidth()), x0 + 1, bitmap.getWidth());
        int y1 = clamp((int) Math.round(b * bitmap.getHeight()), y0 + 1, bitmap.getHeight());
        int total = 0, bright = 0;
        int step = Math.max(1, bitmap.getWidth() / 420);
        for (int y = y0; y < y1; y += step) {
            for (int x = x0; x < x1; x += step) {
                int c = bitmap.getPixel(x, y);
                int rr = Color.red(c), gg = Color.green(c), bb = Color.blue(c);
                int max = Math.max(rr, Math.max(gg, bb));
                int min = Math.min(rr, Math.min(gg, bb));
                int value = (rr + gg + bb) / 3;
                total++;
                if (value >= 155 && max - min <= 150) bright++;
            }
        }
        return total == 0 ? 0.0 : bright / (double) total;
    }

    private static int estimateSeatActivity(Bitmap bitmap) {
        double[][] zones = {
                {0.39, 0.68, 0.61, 0.98},
                {0.08, 0.50, 0.31, 0.82},
                {0.02, 0.27, 0.23, 0.58},
                {0.16, 0.02, 0.38, 0.30},
                {0.62, 0.02, 0.84, 0.30},
                {0.77, 0.27, 0.98, 0.58},
                {0.69, 0.50, 0.92, 0.82}
        };
        int active = 0;
        for (double[] z : zones) {
            double skin = skinRatio(bitmap, z[0], z[1], z[2], z[3]);
            if (skin >= 0.004) active++;
        }
        return active;
    }

    private static double skinRatio(Bitmap bitmap, double l, double t, double r, double b) {
        int x0 = clamp((int) Math.round(l * bitmap.getWidth()), 0, bitmap.getWidth() - 1);
        int y0 = clamp((int) Math.round(t * bitmap.getHeight()), 0, bitmap.getHeight() - 1);
        int x1 = clamp((int) Math.round(r * bitmap.getWidth()), x0 + 1, bitmap.getWidth());
        int y1 = clamp((int) Math.round(b * bitmap.getHeight()), y0 + 1, bitmap.getHeight());
        int total = 0, skin = 0;
        int step = Math.max(1, bitmap.getWidth() / 420);
        for (int y = y0; y < y1; y += step) {
            for (int x = x0; x < x1; x += step) {
                int c = bitmap.getPixel(x, y);
                int rr = Color.red(c), gg = Color.green(c), bb = Color.blue(c);
                total++;
                if (rr > 80 && gg > 40 && bb > 20 && rr > gg && gg >= bb * 0.75
                        && (rr - bb) > 20 && (rr - gg) < 110) {
                    skin++;
                }
            }
        }
        return total == 0 ? 0.0 : skin / (double) total;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void visit(int index, boolean[] mask, boolean[] seen, ArrayDeque<Integer> queue) {
        if (index < 0 || index >= mask.length || seen[index] || !mask[index]) return;
        seen[index] = true;
        queue.add(index);
    }
}
