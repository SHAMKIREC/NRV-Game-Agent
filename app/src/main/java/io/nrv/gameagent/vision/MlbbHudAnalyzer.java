package io.nrv.gameagent.vision;

import android.media.Image;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Fast HUD analyzer for RGBA MediaProjection frames.
 * It avoids Bitmap allocations and reads calibrated HUD regions directly.
 */
public final class MlbbHudAnalyzer {

    public HudObservation analyze(Image image) {
        if (image == null || image.getPlanes().length == 0) {
            return new HudObservation(0, 0, 0, 0, false, false, EnemyObservation.none());
        }

        Image.Plane plane = image.getPlanes()[0];
        return analyzeRgba(
                plane.getBuffer(),
                image.getWidth(),
                image.getHeight(),
                plane.getRowStride(),
                plane.getPixelStride()
        );
    }

    HudObservation analyzeRgba(
            ByteBuffer source,
            int width,
            int height,
            int rowStride,
            int pixelStride
    ) {
        boolean landscape = MlbbHudLayout.looksLandscape(width, height);
        if (!landscape || source == null || pixelStride < 3 || rowStride <= 0) {
            return new HudObservation(0, 0, 0, 0, landscape, false, EnemyObservation.none());
        }

        ByteBuffer buffer = source.duplicate();
        BarReading hp = readBar(
                buffer,
                width,
                height,
                rowStride,
                pixelStride,
                MlbbHudLayout.PLAYER_HP_BAR,
                ColorKind.HP_GREEN
        );
        BarReading mana = readBar(
                buffer,
                width,
                height,
                rowStride,
                pixelStride,
                MlbbHudLayout.PLAYER_MANA_BAR,
                ColorKind.MANA_BLUE
        );
        EnemyObservation enemies = detectEnemies(buffer, width, height, rowStride, pixelStride);

        boolean dead = hp.confidence >= 0.25 && hp.ratio <= 0.025;
        return new HudObservation(
                hp.ratio,
                mana.ratio,
                hp.confidence,
                mana.confidence,
                true,
                dead,
                enemies
        );
    }

    private EnemyObservation detectEnemies(
            ByteBuffer buffer,
            int width,
            int height,
            int rowStride,
            int pixelStride
    ) {
        int left = (int) (width * 0.18);
        int right = (int) (width * 0.86);
        int top = (int) (height * 0.12);
        int bottom = (int) (height * 0.72);

        int stepY = Math.max(2, height / 320);
        int minRun = Math.max(14, width / 95);
        int maxGap = Math.max(2, width / 700);
        List<EnemyCandidate> candidates = new ArrayList<>();

        for (int y = top; y < bottom; y += stepY) {
            int runStart = -1;
            int lastRed = -1;
            int redCount = 0;

            for (int x = left; x < right; x++) {
                int offset = y * rowStride + x * pixelStride;
                if (offset < 0 || offset + 2 >= buffer.limit()) continue;

                int r = buffer.get(offset) & 0xff;
                int g = buffer.get(offset + 1) & 0xff;
                int b = buffer.get(offset + 2) & 0xff;
                boolean red = isEnemyRed(r, g, b);

                if (red) {
                    if (runStart < 0) runStart = x;
                    lastRed = x;
                    redCount++;
                } else if (runStart >= 0 && x - lastRed > maxGap) {
                    addEnemyRun(candidates, runStart, lastRed, y, redCount, minRun, width, height);
                    runStart = -1;
                    lastRed = -1;
                    redCount = 0;
                }
            }

            if (runStart >= 0) {
                addEnemyRun(candidates, runStart, lastRed, y, redCount, minRun, width, height);
            }
        }

        if (candidates.isEmpty()) return EnemyObservation.none();

        List<EnemyCandidate> merged = new ArrayList<>();
        for (EnemyCandidate candidate : candidates) {
            boolean mergedIntoExisting = false;
            for (int i = 0; i < merged.size(); i++) {
                EnemyCandidate existing = merged.get(i);
                if (Math.abs(existing.x - candidate.x) < 0.07
                        && Math.abs(existing.y - candidate.y) < 0.045) {
                    merged.set(i, existing.merge(candidate));
                    mergedIntoExisting = true;
                    break;
                }
            }
            if (!mergedIntoExisting) merged.add(candidate);
        }

        EnemyCandidate nearest = merged.get(0);
        double nearestDistance = distanceToPlayer(nearest.x, nearest.y);
        double confidenceSum = 0.0;
        for (EnemyCandidate candidate : merged) {
            confidenceSum += candidate.confidence;
            double distance = distanceToPlayer(candidate.x, candidate.y);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }

        double confidence = Math.min(1.0, confidenceSum / Math.max(1, merged.size()));
        return new EnemyObservation(merged.size(), nearest.x, nearest.y, confidence);
    }

    private void addEnemyRun(
            List<EnemyCandidate> out,
            int start,
            int end,
            int y,
            int redCount,
            int minRun,
            int width,
            int height
    ) {
        int span = Math.max(0, end - start + 1);
        if (span < minRun || redCount < minRun * 0.70) return;

        double x = ((start + end) * 0.5) / Math.max(1.0, width);
        double ny = y / Math.max(1.0, height);
        double density = Math.min(1.0, redCount / Math.max(1.0, span));
        double lengthScore = Math.min(1.0, span / Math.max(1.0, width * 0.08));
        out.add(new EnemyCandidate(x, ny, density * lengthScore));
    }

    private boolean isEnemyRed(int r, int g, int b) {
        return r >= 125
                && r >= g * 1.28
                && r >= b * 1.22
                && g <= 150;
    }

    private double distanceToPlayer(double x, double y) {
        double dx = x - 0.45;
        double dy = y - 0.54;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private BarReading readBar(
            ByteBuffer buffer,
            int width,
            int height,
            int rowStride,
            int pixelStride,
            MlbbHudLayout.Region region,
            ColorKind kind
    ) {
        int left = region.leftPx(width);
        int right = region.rightPx(width);
        int top = region.topPx(height);
        int bottom = region.bottomPx(height);

        if (right <= left || bottom <= top) {
            return new BarReading(0, 0);
        }

        int regionWidth = right - left;
        int matchedColumns = 0;
        int usefulColumns = 0;

        for (int x = left; x < right; x++) {
            int matches = 0;
            int samples = 0;

            for (int y = top; y < bottom; y++) {
                int offset = y * rowStride + x * pixelStride;
                if (offset < 0 || offset + 2 >= buffer.limit()) continue;

                int r = buffer.get(offset) & 0xff;
                int g = buffer.get(offset + 1) & 0xff;
                int b = buffer.get(offset + 2) & 0xff;
                samples++;

                if (matches(kind, r, g, b)) matches++;
            }

            if (samples == 0) continue;
            usefulColumns++;
            if ((double) matches / samples >= 0.20) {
                matchedColumns++;
            }
        }

        if (usefulColumns == 0) return new BarReading(0, 0);

        double ratio = (double) matchedColumns / usefulColumns;
        double confidence = Math.min(1.0, (double) usefulColumns / Math.max(1, regionWidth));
        return new BarReading(ratio, confidence);
    }

    private boolean matches(ColorKind kind, int r, int g, int b) {
        return switch (kind) {
            case HP_GREEN -> g >= 100 && g >= r * 1.18 && g >= b * 1.05;
            case MANA_BLUE -> b >= 105 && b >= r * 1.20 && b >= g * 1.02;
        };
    }

    private enum ColorKind {
        HP_GREEN,
        MANA_BLUE
    }

    private record BarReading(double ratio, double confidence) {}

    private record EnemyCandidate(double x, double y, double confidence) {
        EnemyCandidate merge(EnemyCandidate other) {
            double total = Math.max(0.0001, confidence + other.confidence);
            return new EnemyCandidate(
                    (x * confidence + other.x * other.confidence) / total,
                    (y * confidence + other.y * other.confidence) / total,
                    Math.min(1.0, Math.max(confidence, other.confidence) + 0.08)
            );
        }
    }
}
