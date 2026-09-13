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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Card reader calibrated for the World Poker Club landscape table.
 * Rank OCR is fully on-device; suit is inferred from the card glyph.
 */
public final class WpcCardReader {
    // Tight crops around the two visible hero cards. They overlap visually, so the
    // second crop starts slightly before the first one ends to preserve the rank corner.
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

    private final TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

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
        List<Slot> slots = new ArrayList<>();
        for (int i = 0; i < HERO.length; i++) slots.add(new Slot(true, i, crop(landscape, HERO[i])));
        for (int i = 0; i < BOARD.length; i++) slots.add(new Slot(false, i, crop(landscape, BOARD[i])));

        final int cellW = 112;
        final int cellH = 144;
        Bitmap strip = Bitmap.createBitmap(cellW * slots.size(), cellH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(strip);
        canvas.drawColor(Color.WHITE);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        for (int i = 0; i < slots.size(); i++) {
            Bitmap rankCrop = rankCrop(slots.get(i).bitmap());
            Bitmap prepared = prepareRankForOcr(rankCrop);
            Rect dst = new Rect(i * cellW + 10, 8, (i + 1) * cellW - 10, cellH - 8);
            canvas.drawBitmap(prepared, null, dst, paint);

            // A visible divider prevents ML Kit from merging glyphs from neighbour cards.
            paint.setColor(Color.rgb(225, 225, 225));
            paint.setStrokeWidth(2f);
            canvas.drawLine((i + 1) * cellW - 1, 0, (i + 1) * cellW - 1, cellH, paint);
            paint.setColor(Color.BLACK);

            if (prepared != rankCrop && !prepared.isRecycled()) prepared.recycle();
            if (rankCrop != slots.get(i).bitmap() && !rankCrop.isRecycled()) rankCrop.recycle();
        }

        recognizer.process(InputImage.fromBitmap(strip, 0))
                .addOnSuccessListener(text -> {
                    try {
                        Map<Integer, Card.Rank> ranks = mapRanks(text, cellW, slots.size());
                        List<Card> hero = new ArrayList<>();
                        List<Card> board = new ArrayList<>();
                        int recognized = 0;

                        for (int i = 0; i < slots.size(); i++) {
                            Slot slot = slots.get(i);
                            if (!cardLooksPresent(slot.bitmap())) continue;
                            Card.Rank rank = ranks.get(i);
                            Card.Suit suit = inferSuit(slot.bitmap());
                            if (rank == null || suit == null) continue;

                            Card card = new Card(rank, suit);
                            recognized++;
                            if (slot.hero()) hero.add(card);
                            else board.add(card);
                        }

                        onSuccess.accept(new Result(hero, board, recognized));
                    } catch (Exception e) {
                        onError.accept(e);
                    } finally {
                        cleanup(strip, landscape, source, slots);
                    }
                })
                .addOnFailureListener(error -> {
                    cleanup(strip, landscape, source, slots);
                    onError.accept(error instanceof Exception ? (Exception) error : new RuntimeException(error));
                });
    }

    private static Map<Integer, Card.Rank> mapRanks(Text text, int cellW, int slots) {
        Map<Integer, Card.Rank> out = new HashMap<>();
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                for (Text.Element element : line.getElements()) {
                    Rect box = element.getBoundingBox();
                    if (box == null) continue;
                    int slot = Math.max(0, Math.min(slots - 1, box.centerX() / cellW));
                    Card.Rank rank = parseRank(element.getText());
                    if (rank != null) out.putIfAbsent(slot, rank);
                }
            }
        }
        return out;
    }

    private static Card.Rank parseRank(String raw) {
        if (raw == null) return null;
        String s = raw.toUpperCase(Locale.ROOT)
                .replace(" ", "")
                .replace("O", "0")
                .replace("I", "1")
                .replace("L", "1")
                .replace("|", "1");
        if (s.contains("10") || s.equals("T") || s.equals("1O")) return Card.Rank.TEN;
        if (s.contains("A")) return Card.Rank.ACE;
        if (s.contains("K")) return Card.Rank.KING;
        if (s.contains("Q")) return Card.Rank.QUEEN;
        if (s.contains("J")) return Card.Rank.JACK;
        if (s.contains("9")) return Card.Rank.NINE;
        if (s.contains("8")) return Card.Rank.EIGHT;
        if (s.contains("7")) return Card.Rank.SEVEN;
        if (s.contains("6")) return Card.Rank.SIX;
        if (s.contains("5") || s.contains("S")) return Card.Rank.FIVE;
        if (s.contains("4")) return Card.Rank.FOUR;
        if (s.contains("3")) return Card.Rank.THREE;
        if (s.contains("2")) return Card.Rank.TWO;
        return null;
    }

    private static Bitmap rankCrop(Bitmap card) {
        int w = card.getWidth();
        int h = card.getHeight();
        int cw = Math.max(1, (int) Math.round(w * 0.48));
        int ch = Math.max(1, (int) Math.round(h * 0.34));
        return Bitmap.createBitmap(card, 0, 0, Math.min(cw, w), Math.min(ch, h));
    }

    /** Convert the decorative WPC rank glyph to a clean black-on-white OCR image. */
    private static Bitmap prepareRankForOcr(Bitmap source) {
        int w = source.getWidth();
        int h = source.getHeight();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = source.getPixel(x, y);
                int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
                int brightness = (r + g + b) / 3;
                boolean redInk = r > 110 && r > g * 1.18 && r > b * 1.18;
                boolean darkInk = brightness < 145;
                out.setPixel(x, y, (redInk || darkInk) ? Color.BLACK : Color.WHITE);
            }
        }
        return out;
    }

    private static Card.Suit inferSuit(Bitmap card) {
        if (card == null) return null;
        int w = card.getWidth();
        int h = card.getHeight();
        int x0 = Math.max(0, (int) (w * 0.02));
        int x1 = Math.min(w, Math.max(x0 + 1, (int) (w * 0.58)));
        int y0 = Math.max(0, (int) (h * 0.28));
        int y1 = Math.min(h, Math.max(y0 + 1, (int) (h * 0.76)));

        boolean[] mask = new boolean[(x1 - x0) * (y1 - y0)];
        int red = 0, black = 0, count = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int c = card.getPixel(x, y);
                int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
                boolean isRed = r > 110 && r > g * 1.22 && r > b * 1.22;
                boolean isBlack = r < 110 && g < 110 && b < 110;
                int mx = x - x0, my = y - y0;
                mask[my * (x1 - x0) + mx] = isRed || isBlack;
                if (isRed) red++;
                if (isBlack) black++;
                count++;
            }
        }
        if (count == 0 || red + black < count * 0.008) return null;
        boolean redSuit = red >= black;

        int mw = x1 - x0, mh = y1 - y0;
        double top = occupancy(mask, mw, mh, 0.0, 0.0, 1.0, 0.34);
        double middle = occupancy(mask, mw, mh, 0.0, 0.34, 1.0, 0.70);
        double bottom = occupancy(mask, mw, mh, 0.0, 0.70, 1.0, 1.0);
        double topCenter = occupancy(mask, mw, mh, 0.30, 0.0, 0.70, 0.35);

        if (redSuit) {
            return top > middle * 0.72 || topCenter > 0.27
                    ? Card.Suit.HEARTS
                    : Card.Suit.DIAMONDS;
        }
        if (topCenter > 0.31 && top >= bottom * 0.68) return Card.Suit.SPADES;
        return Card.Suit.CLUBS;
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

    private static double occupancy(boolean[] mask, int w, int h, double l, double t, double r, double b) {
        int x0 = Math.max(0, Math.min(w - 1, (int) Math.round(l * w)));
        int y0 = Math.max(0, Math.min(h - 1, (int) Math.round(t * h)));
        int x1 = Math.max(x0 + 1, Math.min(w, (int) Math.round(r * w)));
        int y1 = Math.max(y0 + 1, Math.min(h, (int) Math.round(b * h)));
        int total = 0, hit = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                total++;
                if (mask[y * w + x]) hit++;
            }
        }
        return total == 0 ? 0 : hit / (double) total;
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

    private static void cleanup(Bitmap strip, Bitmap landscape, Bitmap source, List<Slot> slots) {
        if (strip != null && !strip.isRecycled()) strip.recycle();
        for (Slot slot : slots) {
            Bitmap b = slot.bitmap();
            if (b != null && !b.isRecycled()) b.recycle();
        }
        if (landscape != source && landscape != null && !landscape.isRecycled()) landscape.recycle();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Slot(boolean hero, int index, Bitmap bitmap) {}
}
