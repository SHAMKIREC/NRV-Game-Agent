package io.nrv.gameagent.vision;

import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MlbbHudAnalyzerTest {

    @Test
    public void readsSyntheticHpAndManaBars() {
        int width = 1000;
        int height = 500;
        int pixelStride = 4;
        int rowStride = width * pixelStride;
        ByteBuffer rgba = ByteBuffer.allocate(rowStride * height);

        fillBar(rgba, width, height, rowStride, pixelStride,
                MlbbHudLayout.PLAYER_HP_BAR, 0.50, 20, 220, 40);
        fillBar(rgba, width, height, rowStride, pixelStride,
                MlbbHudLayout.PLAYER_MANA_BAR, 0.75, 20, 90, 230);

        HudObservation observation = new MlbbHudAnalyzer().analyzeRgba(
                rgba, width, height, rowStride, pixelStride
        );

        assertEquals(0.50, observation.hpRatio(), 0.08);
        assertEquals(0.75, observation.manaRatio(), 0.08);
        assertTrue(observation.hpConfidence() > 0.9);
        assertTrue(observation.manaConfidence() > 0.9);
        assertTrue(observation.landscape());
    }

    @Test
    public void detectsEnemyRedHealthBarAndFeedsGameState() {
        int width = 1000;
        int height = 500;
        int pixelStride = 4;
        int rowStride = width * pixelStride;
        ByteBuffer rgba = ByteBuffer.allocate(rowStride * height);

        drawEnemyBar(rgba, rowStride, pixelStride, 500, 235, 82, 5);

        HudObservation observation = new MlbbHudAnalyzer().analyzeRgba(
                rgba, width, height, rowStride, pixelStride
        );

        assertTrue(observation.enemies().detected());
        assertTrue(observation.enemies().count() >= 1);
        assertEquals(0.50, observation.enemies().nearestX(), 0.08);
        assertTrue(observation.toGameState().nearbyEnemies() >= 1);
        assertTrue(observation.toGameState().enemyInAttackRange());
    }

    @Test
    public void ignoresShortRedNoise() {
        int width = 1000;
        int height = 500;
        int pixelStride = 4;
        int rowStride = width * pixelStride;
        ByteBuffer rgba = ByteBuffer.allocate(rowStride * height);

        drawEnemyBar(rgba, rowStride, pixelStride, 500, 235, 5, 2);

        HudObservation observation = new MlbbHudAnalyzer().analyzeRgba(
                rgba, width, height, rowStride, pixelStride
        );

        assertFalse(observation.enemies().detected());
        assertEquals(0, observation.toGameState().nearbyEnemies());
    }

    @Test
    public void rejectsPortraitFrames() {
        int width = 500;
        int height = 1000;
        int pixelStride = 4;
        int rowStride = width * pixelStride;
        ByteBuffer rgba = ByteBuffer.allocate(rowStride * height);

        HudObservation observation = new MlbbHudAnalyzer().analyzeRgba(
                rgba, width, height, rowStride, pixelStride
        );

        assertEquals(0.0, observation.hpConfidence(), 0.0);
        assertTrue(!observation.landscape());
    }

    private static void drawEnemyBar(
            ByteBuffer buffer,
            int rowStride,
            int pixelStride,
            int centerX,
            int y,
            int width,
            int height
    ) {
        int left = centerX - width / 2;
        int right = centerX + width / 2;
        for (int yy = y; yy < y + height; yy++) {
            for (int x = left; x < right; x++) {
                int offset = yy * rowStride + x * pixelStride;
                buffer.put(offset, (byte) 225);
                buffer.put(offset + 1, (byte) 45);
                buffer.put(offset + 2, (byte) 35);
                buffer.put(offset + 3, (byte) 255);
            }
        }
    }

    private static void fillBar(
            ByteBuffer buffer,
            int width,
            int height,
            int rowStride,
            int pixelStride,
            MlbbHudLayout.Region region,
            double fill,
            int r,
            int g,
            int b
    ) {
        int left = region.leftPx(width);
        int right = region.rightPx(width);
        int top = region.topPx(height);
        int bottom = region.bottomPx(height);
        int filledRight = left + (int) Math.round((right - left) * fill);

        for (int y = top; y < bottom; y++) {
            for (int x = left; x < filledRight; x++) {
                int offset = y * rowStride + x * pixelStride;
                buffer.put(offset, (byte) r);
                buffer.put(offset + 1, (byte) g);
                buffer.put(offset + 2, (byte) b);
                buffer.put(offset + 3, (byte) 255);
            }
        }
    }
}
