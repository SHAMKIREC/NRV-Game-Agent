package io.nrv.gameagent.poker;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * Geometry profile calibrated from World Poker Club landscape screenshots.
 * The table has one hero seat plus four opponent seats; the dealer NPC is ignored.
 */
public final class WorldPokerClubProfile {
    private static final double[] BOARD_X = {0.390, 0.445, 0.500, 0.555, 0.610};
    private static final double[] HERO_X = {0.522, 0.565};

    public Result analyze(Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() < bitmap.getHeight()) {
            return new Result(false, 0, 0, 0, 0.0);
        }

        double tableGreen = greenRatio(bitmap, 0.25, 0.18, 0.75, 0.63);
        boolean matched = tableGreen >= 0.26;

        int board = 0;
        for (double cx : BOARD_X) {
            if (cardPresent(bitmap, cx - 0.027, 0.285, cx + 0.027, 0.505)) board++;
        }

        int hero = 0;
        for (double cx : HERO_X) {
            if (cardPresent(bitmap, cx - 0.030, 0.585, cx + 0.030, 0.820)) hero++;
        }

        int players = 0;
        if (matched) {
            players = 1; // hero
            if (occupied(bitmap, 0.23, 0.01, 0.43, 0.28)) players++; // upper-left
            if (occupied(bitmap, 0.57, 0.01, 0.79, 0.28)) players++; // upper-right
            if (occupied(bitmap, 0.12, 0.43, 0.35, 0.76)) players++; // lower-left
            if (occupied(bitmap, 0.66, 0.43, 0.90, 0.76)) players++; // lower-right
            // A running hand cannot have only the hero. Keep a conservative minimum of heads-up.
            players = Math.max(2, Math.min(5, players));
        }

        double completeness = 0.0;
        if (matched) completeness += 0.35;
        completeness += Math.min(0.25, hero * 0.125);
        completeness += Math.min(0.30, board * 0.06);
        if (players >= 2) completeness += 0.10;

        return new Result(matched, hero, board, players, Math.min(1.0, completeness));
    }

    private static boolean cardPresent(Bitmap b, double l, double t, double r, double bottom) {
        return paleCardRatio(b, l, t, r, bottom) >= 0.13;
    }

    private static boolean occupied(Bitmap b, double l, double t, double r, double bottom) {
        double skin = skinRatio(b, l, t, r, bottom);
        double redBack = redRatio(b, l, t, r, bottom);
        double pale = paleCardRatio(b, l, t, r, bottom);
        return skin >= 0.005 || redBack >= 0.0035 || pale >= 0.018;
    }

    private static double paleCardRatio(Bitmap b, double l, double t, double r, double bottom) {
        return ratio(b, l, t, r, bottom, (rr, gg, bb) -> {
            int max = Math.max(rr, Math.max(gg, bb));
            int min = Math.min(rr, Math.min(gg, bb));
            int brightness = (rr + gg + bb) / 3;
            return brightness >= 165 && max - min <= 125;
        });
    }

    private static double greenRatio(Bitmap b, double l, double t, double r, double bottom) {
        return ratio(b, l, t, r, bottom,
                (rr, gg, bb) -> gg > 65 && gg > rr * 1.10 && gg > bb * 1.06);
    }

    private static double skinRatio(Bitmap b, double l, double t, double r, double bottom) {
        return ratio(b, l, t, r, bottom, (rr, gg, bb) ->
                rr > 80 && gg > 40 && bb > 20 && rr > gg && gg >= bb * 0.68
                        && rr - bb > 18 && rr - gg < 130);
    }

    private static double redRatio(Bitmap b, double l, double t, double r, double bottom) {
        return ratio(b, l, t, r, bottom,
                (rr, gg, bb) -> rr > 115 && rr > gg * 1.28 && rr > bb * 1.28);
    }

    private static double ratio(Bitmap b, double l, double t, double r, double bottom, PixelRule rule) {
        int x0 = clamp((int) Math.round(l * b.getWidth()), 0, b.getWidth() - 1);
        int y0 = clamp((int) Math.round(t * b.getHeight()), 0, b.getHeight() - 1);
        int x1 = clamp((int) Math.round(r * b.getWidth()), x0 + 1, b.getWidth());
        int y1 = clamp((int) Math.round(bottom * b.getHeight()), y0 + 1, b.getHeight());
        int step = Math.max(1, b.getWidth() / 600);
        int total = 0;
        int hits = 0;
        for (int y = y0; y < y1; y += step) {
            for (int x = x0; x < x1; x += step) {
                int c = b.getPixel(x, y);
                total++;
                if (rule.test(Color.red(c), Color.green(c), Color.blue(c))) hits++;
            }
        }
        return total == 0 ? 0.0 : hits / (double) total;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private interface PixelRule {
        boolean test(int r, int g, int b);
    }

    public record Result(
            boolean matched,
            int heroCards,
            int boardCards,
            int players,
            double completeness
    ) {}
}
