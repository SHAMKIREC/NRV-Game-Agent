package io.nrv.gameagent.poker;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.util.Base64;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Deterministic card reader for the fixed landscape table.
 *
 * Rank OCR is intentionally not used here. The printed rank glyph is isolated,
 * normalized to a 25x25 binary mask and matched against examples captured from
 * this exact game UI. This follows the same contour/template idea as the
 * original OpenCV card-recognition project, but keeps the Android build small
 * and avoids a fragile general-purpose OCR step.
 */
public final class TemplateCardReader {
    private static final double[][] HERO = {
            {0.495, 0.575, 0.548, 0.825},
            {0.540, 0.565, 0.602, 0.825}
    };

    private static final double[][] BOARD = {
            {0.363, 0.282, 0.416, 0.510},
            {0.417, 0.282, 0.470, 0.510},
            {0.471, 0.282, 0.524, 0.510},
            {0.525, 0.282, 0.578, 0.510},
            {0.579, 0.282, 0.632, 0.510}
    };

    private static final int MASK_SIZE = 25;
    private static final int MASK_BITS = MASK_SIZE * MASK_SIZE;
    private static final Template[] TEMPLATES = {
            new Template(Card.Rank.KING, "AAAAAAAAAAAwAAD4AHwwAA4QAAcYAA+GAAMDAAGBgAAwwAAYYAAH+AAA4AAA8AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.FIVE, "AAAAAAAAAAYAAAP4AAP8AAH+AADAAA+AAAOAAANgAAGwAADwAABgAABgAABwAAA4AAAcAAA8AAD8AAf4AB/AAfAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.QUEEN, "AAAAAAAAAAcAAA/AAA4wAAYMAAcGAAcDAAcDgAcBgAcBgAcBgAcDgAcDgAcHAAcPAAYeAAc8AAP4AAHwAADgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.FOUR, "AAAAAAAAAAAAAAAAAAMAAAGAAADgAADwAAHYAAHjAAODgAODwAPDwAHDwAGHwA//4Af/8A//4ABgAAGAAAGAAAGAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.NINE, "AAAAAAAAAAHgAAP4AAf8AA4cAAwOAA4DgA4DgA4DgA4DgA8DgA8HgA4PgA4/gA4fwA4DwAAHwAAPgAAHAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.ACE, "AAAAAAAAAAAAAAMAAAHgAADwAAH4AAG+AADzgAHhgAHDgAHB4AODwAODwAP/4AP/8AH/8AHAcAHAcAHAeAHgOAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.TWO, "AAAAAAAAAAHwAAP8AAf8AA4cAAwOAAAAOAAAOAAAcAAAcAAA4AABwAABwAADgAADgAAHAAAfgAP/8Af/8A//4AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.TEN, "AAAAAAAAAAcAAAPgAAH4AAD8AAB8AAAYwAAMYAAHMAADmAAByAAAwgAAYQAAOGAAHDgAA4wAAYYAAeMAAPgAAHAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.SEVEN, "AAAAAAAAAAf///8///+f//8AAA4AAAcAAAcAAA4AAA4AAAcAAAcAAA4AAA4AAAcAAAcAAA4AAA4AAAcAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.FOUR, "AAAAAAAAAAAAAAAAAAMAAAGAAADgAADwAAHYAAHjAAODgAODwAPDwAHBwAGHwA//4Af/8A//4ABgAAGAAAGAAAGAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.JACK, "AAAAAAAAAAAAAHwAAD4AAB8AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA8AAA8AAH8AB/wAfgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.FIVE, "AAAAAAAAAAYAAAP4AAP8AAH+AADAAA+AAAOAAANgAAGwAADwAABgAABgAABwAAA4AAAcAAA8AAD8AAf4AB/AAfAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.SEVEN, "AAAAAAAAAAf///8///+f//8AAA4AAAcAAAcAAA4AAA4AAAcAAAcAAA4AAA4AAAcAAAcAAA4AAA4AAAcAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.TWO, "AAAAAAAAAAHwAAP8AAf8AA4cAAwOAAAAOAAAOAAAcAAAcAAA4AABwAABwAADgAADgAAHAAAfgAP/8Af/8A//4AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.JACK, "AAAAAAAAAAAAAHwAAD4AAB8AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA8AAA8AAH8AB/wAfgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.EIGHT, "AAAAAAAAAAHgAAP4AAf8AA4cAAwOAA4DgA4DgA4DgA8HgA4PgA4/gA4fwA4DgA4DgA4DgA4DgA4DgA8HgA4PgAA/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.KING, "AAAAAAAAAAAwAAD4AHwwAA4QAAcYAA+GAAMDAAGBgAAwwAAYYAAH+AAA4AAA8AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.ACE, "AAAAAAAAAAAAAAMAAAHgAADwAAH4AAG+AADzgAHhgAHDgAHB4AODwAODwAP/4AP/8AH/8AHAcAHAcAHAeAHgOAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.KING, "AAAAAAAAAAAwAAD4AHwwAA4QAAcYAA+GAAMDAAGBgAAwwAAYYAAH+AAA4AAA8AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.FIVE, "AAAAAAAAAAYAAAP4AAP8AAH+AADAAA+AAAOAAANgAAGwAADwAABgAABgAABwAAA4AAAcAAA8AAD8AAf4AB/AAfAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.EIGHT, "AAAAAAAAAAHgAAP4AAf8AA4cAAwOAA4DgA4DgA4DgA8HgA4PgA4/gA4fwA4DgA4DgA4DgA4DgA4DgA8HgA4PgAA/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.TWO, "AAAAAAAAAAHwAAP8AAf8AA4cAAwOAAAAOAAAOAAAcAAAcAAA4AABwAABwAADgAADgAAHAAAfgAP/8Af/8A//4AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.TWO, "AAAAAAAAAAHwAAP8AAf8AA4cAAwOAAAAOAAAOAAAcAAAcAAA4AABwAABwAADgAADgAAHAAAfgAP/8Af/8A//4AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.SIX, "AAAAAAAAAABwAAD4AAf8AA4cAAwOAA4AAA4AAA4AAA4HgA4PgA4/gA4fwA4DgA4DgA4DgA4DgA4DgA8HgA4PgAA/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.FOUR, "AAAAAAAAAAAAAAAAAAMAAAGAAADgAADwAAHYAAHjAAODgAODwAPDwAHBwAGHwA//4Af/8A//4ABgAAGAAAGAAAGAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.QUEEN, "AAAAAAAAAAcAAA/AAA4wAAYMAAcGAAcDAAcDgAcBgAcBgAcBgAcDgAcDgAcHAAcPAAYeAAc8AAP4AAHwAADgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.KING, "AAAAAAAAAAAwAAD4AHwwAA4QAAcYAA+GAAMDAAGBgAAwwAAYYAAH+AAA4AAA8AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.ACE, "AAAAAAAAAAAAAAMAAAHgAADwAAH4AAG+AADzgAHhgAHDgAHB4AODwAODwAP/4AP/8AH/8AHAcAHAcAHAeAHgOAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.EIGHT, "AAAAAAAAAAHgAAP4AAf8AA4cAAwOAA4DgA4DgA4DgA8HgA4PgA4/gA4fwA4DgA4DgA4DgA4DgA4DgA8HgA4PgAA/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.TEN, "AAAAAAAAAAcAAAPgAAH4AAD8AAB8AAAYwAAMYAAHMAADmAAByAAAwgAAYQAAOGAAHDgAA4wAAYYAAeMAAPgAAHAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.FIVE, "AAAAAAAAAAYAAAP4AAP8AAH+AADAAA+AAAOAAANgAAGwAADwAABgAABgAABwAAA4AAAcAAA8AAD8AAf4AB/AAfAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.SEVEN, "AAAAAAAAAAf///8///+f//8AAA4AAAcAAAcAAA4AAA4AAAcAAAcAAA4AAA4AAAcAAAcAAA4AAA4AAAcAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.NINE, "AAAAAAAAAAHgAAP4AAf8AA4cAAwOAA4DgA4DgA4DgA8DgA8HgA4PgA4/gA4fwA4DwAAHwAAPgAAHAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.ACE, "AAAAAAAAAAAAAAMAAAHgAADwAAH4AAG+AADzgAHhgAHDgAHB4AODwAODwAP/4AP/8AH/8AHAcAHAcAHAeAHgOAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.TWO, "AAAAAAAAAAHwAAP8AAf8AA4cAAwOAAAAOAAAOAAAcAAAcAAA4AABwAABwAADgAADgAAHAAAfgAP/8Af/8A//4AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.QUEEN, "AAAAAAAAAAcAAA/AAA4wAAYMAAcGAAcDAAcDgAcBgAcBgAcBgAcDgAcDgAcHAAcPAAYeAAc8AAP4AAHwAADgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.SIX, "AAAAAAAAAABwAAD4AAf8AA4cAAwOAA4AAA4AAA4AAA4HgA4PgA4/gA4fwA4DgA4DgA4DgA4DgA4DgA8HgA4PgAA/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.SEVEN, "AAAAAAAAAAf///8///+f//8AAA4AAAcAAAcAAA4AAA4AAAcAAAcAAA4AAA4AAAcAAAcAAA4AAA4AAAcAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.JACK, "AAAAAAAAAAAAAHwAAD4AAB8AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA8AAA8AAH8AB/wAfgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.JACK, "AAAAAAAAAAAAAHwAAD4AAB8AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA8AAA8AAH8AB/wAfgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.FOUR, "AAAAAAAAAAAAAAAAAAMAAAGAAADgAADwAAHYAAHjAAODgAODwAPDwAHBwAGHwA//4Af/8A//4ABgAAGAAAGAAAGAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.FOUR, "AAAAAAAAAAAAAAAAAAMAAAGAAADgAADwAAHYAAHjAAODgAODwAPDwAHBwAGHwA//4Af/8A//4ABgAAGAAAGAAAGAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.QUEEN, "AAAAAAAAAAcAAA/AAA4wAAYMAAcGAAcDAAcDgAcBgAcBgAcBgAcDgAcDgAcHAAcPAAYeAAc8AAP4AAHwAADgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.SIX, "AAAAAAAAAABwAAD4AAf8AA4cAAwOAA4AAA4AAA4AAA4HgA4PgA4/gA4fwA4DgA4DgA4DgA4DgA4DgA8HgA4PgAA/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.SEVEN, "AAAAAAAAAAf///8///+f//8AAA4AAAcAAAcAAA4AAA4AAAcAAAcAAA4AAA4AAAcAAAcAAA4AAA4AAAcAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.JACK, "AAAAAAAAAAAAAHwAAD4AAB8AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA8AAA8AAH8AB/wAfgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.JACK, "AAAAAAAAAAAAAHwAAD4AAB8AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA4AAA8AAA8AAH8AB/wAfgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.THREE, "AAAAAAAAAAHwAAP8AAf8AA4cAAAOGAAAOAAAcAAA4AADwAAf4AAD/AAAHgAADgAADgAADgAADgAAPAAB/AAf4AH8AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.ACE, "AAAAAAAAAAAAAAMAAAHgAADwAAH4AAG+AADzgAHhgAHDgAHB4AODwAODwAP/4AP/8AH/8AHAcAHAcAHAeAHgOAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="),
            new Template(Card.Rank.SEVEN, "AAAAAAAAAAf///8///+f//8AAA4AAAcAAAcAAA4AAA4AAAcAAAcAAA4AAA4AAAcAAAcAAA4AAA4AAAcAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==")
    };

    public record Result(List<Card> hero, List<Card> board, int recognizedSlots) {
        public Result {
            hero = hero == null ? List.of() : List.copyOf(hero);
            board = board == null ? List.of() : List.copyOf(board);
            recognizedSlots = Math.max(0, recognizedSlots);
        }

        public boolean heroComplete() { return hero.size() == 2; }
        public boolean boardConsistent() {
            int n = board.size();
            return n == 0 || n == 3 || n == 4 || n == 5;
        }
    }

    public void read(Bitmap source, Consumer<Result> onSuccess, Consumer<Exception> onError) {
        if (source == null) {
            onError.accept(new IllegalArgumentException("bitmap is null"));
            return;
        }

        Bitmap landscape = normalizeLandscape(source);
        List<Slot> slots = new ArrayList<>(7);
        try {
            for (int i = 0; i < HERO.length; i++) {
                Bitmap card = crop(landscape, HERO[i]);
                slots.add(new Slot(true, i, card, cardLooksPresent(card)));
            }
            for (int i = 0; i < BOARD.length; i++) {
                Bitmap card = crop(landscape, BOARD[i]);
                slots.add(new Slot(false, i, card, cardLooksPresent(card)));
            }

            List<Card> hero = new ArrayList<>(2);
            List<Card> board = new ArrayList<>(5);
            int recognized = 0;

            for (Slot slot : slots) {
                if (!slot.present()) continue;

                Card.Rank rank = matchRank(slot.bitmap());
                if (rank == null) continue;

                Card.Suit suit = inferSuit(slot.bitmap(), rank, slot.hero(), slot.index());
                if (suit == null) continue;

                Card card = new Card(rank, suit);
                recognized++;
                if (slot.hero()) hero.add(card);
                else board.add(card);
            }

            onSuccess.accept(new Result(hero, board, recognized));
        } catch (Exception e) {
            onError.accept(e);
        } finally {
            cleanup(landscape, source, slots);
        }
    }

    private static Card.Rank matchRank(Bitmap card) {
        boolean[] mask = extractRankMask(card);
        if (mask == null) return null;

        double best = Double.POSITIVE_INFINITY;
        Card.Rank bestRank = null;
        for (Template template : TEMPLATES) {
            double d = shiftedJaccardDistance(mask, template.mask());
            if (d < best) {
                best = d;
                bestRank = template.rank();
            }
        }
        return best <= 0.62 ? bestRank : null;
    }

    private static boolean[] extractRankMask(Bitmap card) {
        Box body = findCardBody(card);
        if (body == null) return null;

        int rx0 = body.x();
        int ry0 = body.y();
        int rw = Math.max(1, Math.min(card.getWidth() - rx0, (int) Math.round(body.w() * 0.60)));
        int rh = Math.max(1, Math.min(card.getHeight() - ry0, (int) Math.round(body.h() * 0.50)));

        boolean[] raw = new boolean[rw * rh];
        for (int y = 0; y < rh; y++) {
            for (int x = 0; x < rw; x++) {
                int c = card.getPixel(rx0 + x, ry0 + y);
                int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
                boolean black = r < 135 && g < 135 && b < 135 && (r + g + b) < 330;
                boolean red = r > 110 && r > g + 20 && r > b + 20 && g < 165 && b < 150;
                raw[y * rw + x] = black || red;
            }
        }

        List<ComponentEx> components = components(raw, rw, rh);
        ComponentEx base = null;
        for (ComponentEx c : components) {
            if (c.area() < 5 || c.h() < 4) continue;
            int minH = Math.max(10, (int) (body.h() * 0.18));
            int maxH = Math.max(minH + 1, (int) (body.h() * 0.46));
            if (c.h() < minH || c.h() > maxH) continue;
            if (c.w() > body.w() * 0.70) continue;
            if (c.y() == 0 && c.h() < body.h() * 0.15) continue;

            if (base == null || c.y() < base.y() || (c.y() == base.y() && c.area() > base.area())) {
                base = c;
            }
        }
        if (base == null) return null;

        boolean[] selected = new boolean[rw * rh];
        copyComponent(raw, selected, rw, rh, base);

        for (ComponentEx c : components) {
            if (c == base) continue;
            int gap = c.x() - (base.x() + base.w());
            boolean aligned = Math.abs(c.y() - base.y()) <= Math.max(5, (int) (base.h() * 0.25));
            boolean similarHeight = Math.abs(c.h() - base.h()) <= Math.max(10, (int) (base.h() * 0.30));
            boolean leftCorner = c.x() < body.w() * 0.40;
            boolean close = gap >= -3 && gap <= body.w() * 0.15;
            if (aligned && similarHeight && leftCorner && close) {
                copyComponent(raw, selected, rw, rh, c);
                break;
            }
        }

        return normalizeMask(selected, rw, rh);
    }

    private static Box findCardBody(Bitmap card) {
        int w = card.getWidth(), h = card.getHeight();
        boolean[] neutral = new boolean[w * h];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = card.getPixel(x, y);
                int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                int brightness = (r + g + b) / 3;
                neutral[y * w + x] = brightness > 105 && (max - min) < 85;
            }
        }

        List<ComponentEx> parts = components(neutral, w, h);
        ComponentEx best = null;
        for (ComponentEx c : parts) {
            if (best == null || c.area() > best.area()) best = c;
        }
        if (best == null || best.area() < 120) return null;
        return new Box(best.x(), best.y(), best.w(), best.h());
    }

    private static List<ComponentEx> components(boolean[] mask, int w, int h) {
        boolean[] seen = new boolean[mask.length];
        int[] queue = new int[mask.length];
        List<ComponentEx> out = new ArrayList<>();

        for (int start = 0; start < mask.length; start++) {
            if (!mask[start] || seen[start]) continue;
            int head = 0, tail = 0;
            queue[tail++] = start;
            seen[start] = true;
            int area = 0;
            int minX = w, minY = h, maxX = -1, maxY = -1;

            while (head < tail) {
                int p = queue[head++];
                int px = p % w;
                int py = p / w;
                area++;
                minX = Math.min(minX, px);
                minY = Math.min(minY, py);
                maxX = Math.max(maxX, px);
                maxY = Math.max(maxY, py);

                for (int ny = Math.max(0, py - 1); ny <= Math.min(h - 1, py + 1); ny++) {
                    for (int nx = Math.max(0, px - 1); nx <= Math.min(w - 1, px + 1); nx++) {
                        int np = ny * w + nx;
                        if (mask[np] && !seen[np]) {
                            seen[np] = true;
                            queue[tail++] = np;
                        }
                    }
                }
            }

            out.add(new ComponentEx(minX, minY, maxX - minX + 1, maxY - minY + 1, area));
        }
        return out;
    }

    private static void copyComponent(boolean[] source, boolean[] target, int w, int h, ComponentEx box) {
        int x1 = Math.min(w, box.x() + box.w());
        int y1 = Math.min(h, box.y() + box.h());
        for (int y = Math.max(0, box.y()); y < y1; y++) {
            for (int x = Math.max(0, box.x()); x < x1; x++) {
                int p = y * w + x;
                if (source[p]) target[p] = true;
            }
        }
    }

    private static boolean[] normalizeMask(boolean[] source, int w, int h) {
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!source[y * w + x]) continue;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        if (maxX < minX || maxY < minY) return null;

        int sw = maxX - minX + 1;
        int sh = maxY - minY + 1;
        double scale = Math.min(21.0 / sw, 21.0 / sh);
        int dw = Math.max(1, (int) Math.round(sw * scale));
        int dh = Math.max(1, (int) Math.round(sh * scale));
        int ox = (MASK_SIZE - dw) / 2;
        int oy = (MASK_SIZE - dh) / 2;

        boolean[] out = new boolean[MASK_BITS];
        for (int y = 0; y < dh; y++) {
            int sy = minY + Math.min(sh - 1, (int) Math.floor(y / scale));
            for (int x = 0; x < dw; x++) {
                int sx = minX + Math.min(sw - 1, (int) Math.floor(x / scale));
                if (source[sy * w + sx]) out[(oy + y) * MASK_SIZE + (ox + x)] = true;
            }
        }
        return out;
    }

    private static double shiftedJaccardDistance(boolean[] a, boolean[] b) {
        double best = 1.0;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                int union = 0, xor = 0;
                for (int y = 0; y < MASK_SIZE; y++) {
                    int ay = y - dy;
                    for (int x = 0; x < MASK_SIZE; x++) {
                        int ax = x - dx;
                        boolean av = ay >= 0 && ay < MASK_SIZE && ax >= 0 && ax < MASK_SIZE && a[ay * MASK_SIZE + ax];
                        boolean bv = b[y * MASK_SIZE + x];
                        if (av || bv) union++;
                        if (av != bv) xor++;
                    }
                }
                if (union > 0) best = Math.min(best, xor / (double) union);
            }
        }
        return best;
    }

    private static boolean[] decodeMask(String encoded) {
        byte[] packed = Base64.decode(encoded, Base64.DEFAULT);
        boolean[] out = new boolean[MASK_BITS];
        for (int i = 0; i < MASK_BITS; i++) {
            int value = packed[i >>> 3] & 0xff;
            out[i] = (value & (1 << (7 - (i & 7)))) != 0;
        }
        return out;
    }

    private static Card.Suit inferSuit(Bitmap card, Card.Rank rank, boolean hero, int slotIndex) {
        boolean face = rank == Card.Rank.JACK || rank == Card.Rank.QUEEN || rank == Card.Rank.KING;

        Region region;
        if (face) {
            if (hero && slotIndex == 0) region = new Region(0.26, 0.28, 0.88, 0.72);
            else if (hero) region = new Region(0.02, 0.28, 0.62, 0.72);
            else region = new Region(0.02, 0.30, 0.44, 0.66);
        } else {
            if (hero && slotIndex == 0) region = new Region(0.25, 0.28, 0.98, 0.96);
            else if (hero) region = new Region(0.08, 0.28, 0.98, 0.96);
            else region = new Region(0.12, 0.28, 0.98, 0.95);
        }

        Component red = largestSuitComponent(card, region, true);
        Component black = largestSuitComponent(card, region, false);
        Component chosen;
        boolean redSuit;

        if (red == null && black == null) return null;
        if (black == null || (red != null && red.score() > black.score())) {
            chosen = red;
            redSuit = true;
        } else {
            chosen = black;
            redSuit = false;
        }
        if (chosen == null) return null;

        if (face) {
            double ratio = chosen.width() / (double) Math.max(1, chosen.height());
            if (redSuit) return ratio < 0.62 ? Card.Suit.DIAMONDS : Card.Suit.HEARTS;
            return ratio < 0.62 ? Card.Suit.SPADES : Card.Suit.CLUBS;
        }

        if (redSuit) return chosen.topFill() > 0.45 ? Card.Suit.HEARTS : Card.Suit.DIAMONDS;
        return chosen.topFill() > 0.32 ? Card.Suit.CLUBS : Card.Suit.SPADES;
    }

    private static Component largestSuitComponent(Bitmap card, Region r, boolean red) {
        int w = card.getWidth();
        int h = card.getHeight();
        int x0 = clamp((int) Math.round(r.left() * w), 0, w - 1);
        int y0 = clamp((int) Math.round(r.top() * h), 0, h - 1);
        int x1 = clamp((int) Math.round(r.right() * w), x0 + 1, w);
        int y1 = clamp((int) Math.round(r.bottom() * h), y0 + 1, h);
        int rw = x1 - x0, rh = y1 - y0;

        boolean[] mask = new boolean[rw * rh];
        for (int y = 0; y < rh; y++) {
            for (int x = 0; x < rw; x++) {
                int c = card.getPixel(x0 + x, y0 + y);
                int rr = Color.red(c), gg = Color.green(c), bb = Color.blue(c);
                boolean hit;
                if (red) {
                    hit = rr > 120 && gg < 150 && bb < 120 && rr > gg * 1.20 && rr > bb * 1.25;
                } else {
                    hit = rr < 105 && gg < 105 && bb < 105;
                }
                mask[y * rw + x] = hit;
            }
        }

        boolean[] seen = new boolean[mask.length];
        int[] queue = new int[mask.length];
        Component best = null;

        for (int start = 0; start < mask.length; start++) {
            if (!mask[start] || seen[start]) continue;
            int head = 0, tail = 0;
            queue[tail++] = start;
            seen[start] = true;

            int area = 0, minX = rw, minY = rh, maxX = -1, maxY = -1;
            ArrayList<Integer> pixels = new ArrayList<>();

            while (head < tail) {
                int p = queue[head++];
                pixels.add(p);
                int px = p % rw, py = p / rw;
                area++;
                minX = Math.min(minX, px); minY = Math.min(minY, py);
                maxX = Math.max(maxX, px); maxY = Math.max(maxY, py);

                for (int ny = Math.max(0, py - 1); ny <= Math.min(rh - 1, py + 1); ny++) {
                    for (int nx = Math.max(0, px - 1); nx <= Math.min(rw - 1, px + 1); nx++) {
                        int np = ny * rw + nx;
                        if (mask[np] && !seen[np]) {
                            seen[np] = true;
                            queue[tail++] = np;
                        }
                    }
                }
            }

            int cw = maxX - minX + 1, ch = maxY - minY + 1;
            if (area < 18 || cw < 3 || ch < 5) continue;
            double ratio = cw / (double) ch;
            if (ratio < 0.16 || ratio > 2.10 || ch > rh * 0.95) continue;

            int touches = 0;
            if (minX == 0) touches++;
            if (minY == 0) touches++;
            if (maxX == rw - 1) touches++;
            if (maxY == rh - 1) touches++;
            double score = area * (1.0 - touches * 0.18);

            int topLimit = minY + Math.max(1, (int) Math.round(ch * 0.20));
            int topPixels = 0;
            int topArea = Math.max(1, cw * Math.max(1, topLimit - minY));
            for (int p : pixels) {
                int px = p % rw, py = p / rw;
                if (px >= minX && px <= maxX && py >= minY && py < topLimit) topPixels++;
            }
            double topFill = topPixels / (double) topArea;

            Component candidate = new Component(area, cw, ch, score, topFill);
            if (best == null || candidate.score() > best.score()) best = candidate;
        }
        return best;
    }

    private static boolean cardLooksPresent(Bitmap card) {
        int total = 0, pale = 0;
        int step = Math.max(1, card.getWidth() / 36);
        for (int y = 0; y < card.getHeight(); y += step) {
            for (int x = 0; x < card.getWidth(); x += step) {
                int c = card.getPixel(x, y);
                int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                int br = (r + g + b) / 3;
                total++;
                if (br > 145 && max - min < 150) pale++;
            }
        }
        return total > 0 && pale / (double) total > 0.12;
    }

    private static Bitmap normalizeLandscape(Bitmap source) {
        if (source.getWidth() >= source.getHeight()) return source;
        Matrix matrix = new Matrix();
        matrix.postRotate(90f);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private static Bitmap crop(Bitmap source, double[] z) {
        int x0 = clamp((int) Math.round(z[0] * source.getWidth()), 0, source.getWidth() - 1);
        int y0 = clamp((int) Math.round(z[1] * source.getHeight()), 0, source.getHeight() - 1);
        int x1 = clamp((int) Math.round(z[2] * source.getWidth()), x0 + 1, source.getWidth());
        int y1 = clamp((int) Math.round(z[3] * source.getHeight()), y0 + 1, source.getHeight());
        return Bitmap.createBitmap(source, x0, y0, x1 - x0, y1 - y0);
    }

    private static void cleanup(Bitmap landscape, Bitmap source, List<Slot> slots) {
        for (Slot slot : slots) {
            Bitmap b = slot.bitmap();
            if (b != null && !b.isRecycled()) b.recycle();
        }
        if (landscape != source && landscape != null && !landscape.isRecycled()) landscape.recycle();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Slot(boolean hero, int index, Bitmap bitmap, boolean present) {}
    private record Box(int x, int y, int w, int h) {}
    private record ComponentEx(int x, int y, int w, int h, int area) {}
    private record Region(double left, double top, double right, double bottom) {}
    private record Component(int area, int width, int height, double score, double topFill) {}

    private record Template(Card.Rank rank, boolean[] mask) {
        Template(Card.Rank rank, String encoded) {
            this(rank, decodeMask(encoded));
        }
    }
}
