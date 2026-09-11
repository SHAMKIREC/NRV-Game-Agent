package io.nrv.gameagent.vision;

import android.media.Image;

import java.nio.ByteBuffer;

/**
 * Fast HUD analyzer for RGBA MediaProjection frames.
 * It avoids Bitmap allocations and reads only tiny calibrated HUD regions.
 */
public final class MlbbHudAnalyzer {

    public HudObservation analyze(Image image) {
        if (image == null || image.getPlanes().length == 0) {
            return new HudObservation(0, 0, 0, 0, false, false);
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
            return new HudObservation(0, 0, 0, 0, landscape, false);
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

        boolean dead = hp.confidence >= 0.25 && hp.ratio <= 0.025;
        return new HudObservation(
                hp.ratio,
                mana.ratio,
                hp.confidence,
                mana.confidence,
                true,
                dead
        );
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
        int regionHeight = bottom - top;
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

            // A filled bar column should contain the target color in a visible part
            // of its vertical span. 20% tolerates antialiasing, glow and borders.
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
}
