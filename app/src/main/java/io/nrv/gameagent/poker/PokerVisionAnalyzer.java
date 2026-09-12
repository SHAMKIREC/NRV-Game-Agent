package io.nrv.gameagent.poker;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lightweight offline screenshot analyzer for Poker Lab.
 * It detects bright, card-shaped connected regions without OCR or network calls.
 */
public final class PokerVisionAnalyzer {
    private static final int MAX_WIDTH = 640;

    public PokerVisionObservation analyze(Bitmap source) {
        if (source == null || source.getWidth() < 40 || source.getHeight() < 40) {
            return new PokerVisionObservation(0, 0, 0, List.of(), 0.0);
        }

        Bitmap bitmap = source;
        if (source.getWidth() > MAX_WIDTH) {
            int scaledHeight = Math.max(1, Math.round(source.getHeight() * (MAX_WIDTH / (float) source.getWidth())));
            bitmap = Bitmap.createScaledBitmap(source, MAX_WIDTH, scaledHeight, true);
        }

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
                mask[y * width + x] = brightness >= 190 && max - min <= 85;
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

                if (wf >= 0.025 && wf <= 0.16
                        && hf >= 0.05 && hf <= 0.30
                        && aspect >= 0.42 && aspect <= 0.95
                        && fill >= 0.30) {
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

        int likelyHero = 0;
        int likelyBoard = 0;
        for (PokerVisionObservation.Region region : regions) {
            double cy = region.centerY();
            double cx = region.centerX();
            if (cy >= 0.62 && cx >= 0.22 && cx <= 0.78) {
                likelyHero++;
            } else if (cy >= 0.28 && cy <= 0.68 && cx >= 0.18 && cx <= 0.82) {
                likelyBoard++;
            }
        }

        likelyHero = Math.min(2, likelyHero);
        likelyBoard = Math.min(5, likelyBoard);
        double confidence = regions.isEmpty() ? 0.0 : Math.min(1.0, regions.size() / 7.0);

        return new PokerVisionObservation(regions.size(), likelyHero, likelyBoard, regions, confidence);
    }

    private static void visit(
            int index,
            boolean[] mask,
            boolean[] seen,
            ArrayDeque<Integer> queue
    ) {
        if (index < 0 || index >= mask.length || seen[index] || !mask[index]) return;
        seen[index] = true;
        queue.add(index);
    }
}
