package io.nrv.gameagent.poker;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Card reader for the fixed World Poker Club landscape table.
 *
 * Each known card slot is cropped first. Only the printed rank corner is then
 * enlarged for OCR, so face artwork and large suit symbols cannot be mistaken
 * for ranks. Suits are classified locally from the card pixels.
 */
public final class WpcCardReader {
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

    private static final int SHEET_WIDTH = 420;
    private static final int ROW_HEIGHT = 230;
    private static final int ROW_PADDING = 20;

    private final TextRecognizer recognizer =
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

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
        for (int i = 0; i < HERO.length; i++) {
            Bitmap card = crop(landscape, HERO[i]);
            slots.add(new Slot(true, i, card, cardLooksPresent(card)));
        }
        for (int i = 0; i < BOARD.length; i++) {
            Bitmap card = crop(landscape, BOARD[i]);
            slots.add(new Slot(false, i, card, cardLooksPresent(card)));
        }

        Bitmap sheet = buildRankSheet(slots);
        recognizer.process(InputImage.fromBitmap(sheet, 0))
                .addOnSuccessListener(text -> {
                    try {
                        Card.Rank[] ranks = readRanksFromSheet(text, slots.size());
                        List<Card> hero = new ArrayList<>(2);
                        List<Card> board = new ArrayList<>(5);
                        int recognized = 0;

                        for (int i = 0; i < slots.size(); i++) {
                            Slot slot = slots.get(i);
                            if (!slot.present()) continue;
                            Card.Rank rank = ranks[i];
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
                        cleanup(landscape, source, slots, sheet);
                    }
                })
                .addOnFailureListener(error -> {
                    cleanup(landscape, source, slots, sheet);
                    onError.accept(error instanceof Exception
                            ? (Exception) error
                            : new RuntimeException(error));
                });
    }

    private static Bitmap buildRankSheet(List<Slot> slots) {
        Bitmap out = Bitmap.createBitmap(
                SHEET_WIDTH,
                ROW_HEIGHT * slots.size(),
                Bitmap.Config.ARGB_8888
        );
        Canvas canvas = new Canvas(out);
        canvas.drawColor(Color.WHITE);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (!slot.present()) continue;

            Bitmap card = slot.bitmap();
            Rect src = rankSourceRect(card, slot);

            int rowTop = i * ROW_HEIGHT;
            Rect dst = new Rect(
                    ROW_PADDING,
                    rowTop + ROW_PADDING,
                    SHEET_WIDTH - ROW_PADDING,
                    rowTop + ROW_HEIGHT - ROW_PADDING
            );

            float cx = dst.exactCenterX();
            float cy = dst.exactCenterY();
            canvas.save();
            if (slot.hero()) {
                float angle = slot.index() == 0 ? 7.0f : -7.0f;
                canvas.rotate(angle, cx, cy);
            }
            canvas.drawBitmap(card, src, dst, paint);
            canvas.restore();
        }
        return out;
    }

    /**
     * The broad slot rectangles intentionally include table background because
     * the hero cards are tilted/overlapped. OCR must therefore use a different
     * inner rank rectangle for hero-0, hero-1 and upright board cards.
     */
    private static Rect rankSourceRect(Bitmap card, Slot slot) {
        double left;
        double top;
        double right;
        double bottom;
        if (slot.hero() && slot.index() == 0) {
            left = 0.29; top = 0.22; right = 0.84; bottom = 0.59;
        } else if (slot.hero()) {
            left = 0.14; top = 0.14; right = 0.57; bottom = 0.50;
        } else {
            left = 0.00; top = 0.02; right = 0.46; bottom = 0.35;
        }
        int x0 = clamp((int) Math.round(left * card.getWidth()), 0, card.getWidth() - 1);
        int y0 = clamp((int) Math.round(top * card.getHeight()), 0, card.getHeight() - 1);
        int x1 = clamp((int) Math.round(right * card.getWidth()), x0 + 1, card.getWidth());
        int y1 = clamp((int) Math.round(bottom * card.getHeight()), y0 + 1, card.getHeight());
        return new Rect(x0, y0, x1, y1);
    }

    private static Card.Rank[] readRanksFromSheet(Text text, int slots) {
        Card.Rank[] result = new Card.Rank[slots];
        int[] score = new int[slots];
        Arrays.fill(score, -1);

        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                acceptRankCandidate(line.getText(), line.getBoundingBox(), result, score);
                for (Text.Element element : line.getElements()) {
                    acceptRankCandidate(element.getText(), element.getBoundingBox(), result, score);
                }
            }
        }
        return result;
    }

    private static void acceptRankCandidate(
            String raw,
            Rect box,
            Card.Rank[] ranks,
            int[] scores
    ) {
        if (box == null) return;
        Card.Rank rank = parseRank(raw);
        if (rank == null) return;

        int row = clamp(box.centerY() / ROW_HEIGHT, 0, ranks.length - 1);
        int area = Math.max(1, box.width()) * Math.max(1, box.height());
        if (area > scores[row]) {
            scores[row] = area;
            ranks[row] = rank;
        }
    }

    private static Card.Rank parseRank(String raw) {
        if (raw == null) return null;
        String s = raw.toUpperCase(Locale.ROOT)
                .replace(" ", "")
                .replace("O", "0")
                .replace("I", "1")
                .replace("L", "1")
                .replace("|", "1")
                .replace("Z", "2");

        if (s.contains("10") || s.equals("T") || s.equals("1O")) return Card.Rank.TEN;
        // The WPC Q glyph often looks like O/0 to OCR. Zero is not a legal rank.
        if (s.equals("0") || s.equals("D")) return Card.Rank.QUEEN;
        if (s.equals("A") || s.startsWith("A")) return Card.Rank.ACE;
        if (s.equals("K") || s.startsWith("K")) return Card.Rank.KING;
        if (s.equals("Q") || s.startsWith("Q")) return Card.Rank.QUEEN;
        if (s.equals("J") || s.startsWith("J")) return Card.Rank.JACK;
        if (s.equals("B")) return Card.Rank.EIGHT;
        if (s.equals("G")) return Card.Rank.SIX;
        if (s.contains("9")) return Card.Rank.NINE;
        if (s.contains("8")) return Card.Rank.EIGHT;
        if (s.contains("7")) return Card.Rank.SEVEN;
        if (s.contains("6")) return Card.Rank.SIX;
        if (s.contains("5") || s.equals("S")) return Card.Rank.FIVE;
        if (s.contains("4")) return Card.Rank.FOUR;
        if (s.contains("3")) return Card.Rank.THREE;
        if (s.contains("2")) return Card.Rank.TWO;
        return null;
    }

    private static Card.Suit inferSuit(
            Bitmap card,
            Card.Rank rank,
            boolean hero,
            int slotIndex
    ) {
        if (card == null) return null;
        boolean face = rank == Card.Rank.JACK
                || rank == Card.Rank.QUEEN
                || rank == Card.Rank.KING;

        Region region;
        if (face) {
            if (hero && slotIndex == 0) {
                region = new Region(0.26, 0.28, 0.88, 0.72);
            } else if (hero) {
                region = new Region(0.02, 0.28, 0.62, 0.72);
            } else {
                region = new Region(0.02, 0.30, 0.44, 0.66);
            }
        } else {
            if (hero && slotIndex == 0) {
                region = new Region(0.25, 0.28, 0.98, 0.96);
            } else if (hero) {
                region = new Region(0.08, 0.28, 0.98, 0.96);
            } else {
                region = new Region(0.12, 0.28, 0.98, 0.95);
            }
        }

        Component red = largestComponent(card, region, true);
        Component black = largestComponent(card, region, false);
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
            if (redSuit) {
                return ratio < 0.62 ? Card.Suit.DIAMONDS : Card.Suit.HEARTS;
            }
            return ratio < 0.62 ? Card.Suit.SPADES : Card.Suit.CLUBS;
        }

        double topFill = chosen.topFill();
        if (redSuit) {
            return topFill > 0.45 ? Card.Suit.HEARTS : Card.Suit.DIAMONDS;
        }
        return topFill > 0.32 ? Card.Suit.CLUBS : Card.Suit.SPADES;
    }

    private static Component largestComponent(Bitmap card, Region r, boolean red) {
        int w = card.getWidth();
        int h = card.getHeight();
        int x0 = clamp((int) Math.round(r.left() * w), 0, w - 1);
        int y0 = clamp((int) Math.round(r.top() * h), 0, h - 1);
        int x1 = clamp((int) Math.round(r.right() * w), x0 + 1, w);
        int y1 = clamp((int) Math.round(r.bottom() * h), y0 + 1, h);
        int rw = x1 - x0;
        int rh = y1 - y0;

        boolean[] mask = new boolean[rw * rh];
        for (int y = 0; y < rh; y++) {
            for (int x = 0; x < rw; x++) {
                int c = card.getPixel(x0 + x, y0 + y);
                int rr = Color.red(c), gg = Color.green(c), bb = Color.blue(c);
                boolean hit;
                if (red) {
                    hit = rr > 120
                            && gg < 150
                            && bb < 120
                            && rr > gg * 1.20
                            && rr > bb * 1.25;
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
            int qHead = 0, qTail = 0;
            queue[qTail++] = start;
            seen[start] = true;

            int area = 0;
            int minX = rw, minY = rh, maxX = -1, maxY = -1;
            ArrayList<Integer> pixels = new ArrayList<>();

            while (qHead < qTail) {
                int p = queue[qHead++];
                pixels.add(p);
                int px = p % rw;
                int py = p / rw;
                area++;
                minX = Math.min(minX, px);
                minY = Math.min(minY, py);
                maxX = Math.max(maxX, px);
                maxY = Math.max(maxY, py);

                for (int ny = Math.max(0, py - 1); ny <= Math.min(rh - 1, py + 1); ny++) {
                    for (int nx = Math.max(0, px - 1); nx <= Math.min(rw - 1, px + 1); nx++) {
                        int np = ny * rw + nx;
                        if (mask[np] && !seen[np]) {
                            seen[np] = true;
                            queue[qTail++] = np;
                        }
                    }
                }
            }

            int cw = maxX - minX + 1;
            int ch = maxY - minY + 1;
            if (area < 18 || cw < 3 || ch < 5) continue;
            double ratio = cw / (double) ch;
            if (ratio < 0.16 || ratio > 2.10) continue;
            if (ch > rh * 0.95) continue;

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
                int px = p % rw;
                int py = p / rw;
                if (px >= minX && px <= maxX && py >= minY && py < topLimit) topPixels++;
            }
            double topFill = topPixels / (double) topArea;

            Component c = new Component(area, cw, ch, score, topFill);
            if (best == null || c.score() > best.score()) best = c;
        }
        return best;
    }

    private static boolean cardLooksPresent(Bitmap card) {
        if (card == null) return false;
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

    private static void cleanup(Bitmap landscape, Bitmap source, List<Slot> slots, Bitmap sheet) {
        for (Slot slot : slots) {
            Bitmap b = slot.bitmap();
            if (b != null && !b.isRecycled()) b.recycle();
        }
        if (sheet != null && !sheet.isRecycled()) sheet.recycle();
        if (landscape != source && landscape != null && !landscape.isRecycled()) landscape.recycle();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Slot(boolean hero, int index, Bitmap bitmap, boolean present) {}
    private record Region(double left, double top, double right, double bottom) {}
    private record Component(int area, int width, int height, double score, double topFill) {}
}
