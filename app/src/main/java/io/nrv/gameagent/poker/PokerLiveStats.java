package io.nrv.gameagent.poker;

import android.graphics.Bitmap;
import android.graphics.Matrix;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Combines WPC table geometry, card recognition and Monte-Carlo equity.
 * Designed for a non-money training game.
 */
public final class PokerLiveStats {
    private final WorldPokerClubProfile profile = new WorldPokerClubProfile();
    private final WpcCardReader reader = new WpcCardReader();
    private final ExecutorService equityExecutor = Executors.newSingleThreadExecutor();

    public record Snapshot(
            List<Card> hero,
            List<Card> board,
            int players,
            String stage,
            String handName,
            Double win,
            Double tie,
            Double lose,
            boolean profileMatched
    ) {
        public String compactRussian() {
            StringBuilder out = new StringBuilder();
            out.append("Мои: ").append(cards(hero)).append("\n");
            out.append("Стол: ").append(cards(board)).append("\n");
            out.append("Игроков: ").append(players > 0 ? players : "?")
                    .append(" · ").append(stage);
            if (handName != null && !handName.isBlank()) out.append(" · ").append(handName);
            if (win != null) {
                out.append(String.format(Locale.US,
                        "\nПобеда %.0f%% · Ничья %.0f%% · Проигрыш %.0f%%",
                        win * 100.0, tie * 100.0, lose * 100.0));
            } else {
                out.append("\nСчитаю после точного чтения карт…");
            }
            return out.toString();
        }
    }

    public void analyze(Bitmap source, Consumer<Snapshot> onSuccess, Consumer<Exception> onError) {
        Bitmap landscape = normalizeLandscape(source);
        WorldPokerClubProfile.Result table = profile.analyze(landscape);
        int players = table.players();

        reader.read(landscape, cards -> equityExecutor.execute(() -> {
            try {
                List<Card> hero = cards.hero();
                List<Card> board = cards.board();
                String stage = stage(board.size());
                String hand = handName(hero, board);

                Double win = null, tie = null, lose = null;
                int opponents = Math.max(1, players - 1);
                if (hero.size() == 2 && validBoardSize(board.size()) && players >= 2) {
                    EquityCalculator.Result equity = new EquityCalculator()
                            .calculate(hero, board, opponents, 5_000);
                    win = equity.win();
                    tie = equity.tie();
                    lose = equity.lose();
                }

                onSuccess.accept(new Snapshot(
                        hero,
                        board,
                        players,
                        stage,
                        hand,
                        win,
                        tie,
                        lose,
                        table.matched()
                ));
            } catch (Exception e) {
                onError.accept(e);
            } finally {
                if (landscape != source && !landscape.isRecycled()) landscape.recycle();
            }
        }), error -> {
            if (landscape != source && !landscape.isRecycled()) landscape.recycle();
            onError.accept(error);
        });
    }

    private static boolean validBoardSize(int n) {
        return n == 0 || n == 3 || n == 4 || n == 5;
    }

    private static String stage(int board) {
        return switch (board) {
            case 0 -> "ПРЕФЛОП";
            case 3 -> "ФЛОП";
            case 4 -> "ТЁРН";
            case 5 -> "РИВЕР";
            default -> "РАСПОЗНАЮ";
        };
    }

    private static String handName(List<Card> hero, List<Card> board) {
        List<Card> all = new ArrayList<>();
        if (hero != null) all.addAll(hero);
        if (board != null) all.addAll(board);
        if (all.size() < 5) return "";
        HandValue value = HandEvaluator.evaluateBest(all);
        return switch (value.category()) {
            case HIGH_CARD -> "старшая карта";
            case ONE_PAIR -> "пара";
            case TWO_PAIR -> "две пары";
            case THREE_OF_A_KIND -> "сет/тройка";
            case STRAIGHT -> "стрит";
            case FLUSH -> "флеш";
            case FULL_HOUSE -> "фулл-хаус";
            case FOUR_OF_A_KIND -> "каре";
            case STRAIGHT_FLUSH -> "стрит-флеш";
        };
    }

    private static String cards(List<Card> cards) {
        if (cards == null || cards.isEmpty()) return "—";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < cards.size(); i++) {
            if (i > 0) out.append(' ');
            out.append(pretty(cards.get(i)));
        }
        return out.toString();
    }

    private static String pretty(Card card) {
        String rank = switch (card.rank()) {
            case TWO -> "2";
            case THREE -> "3";
            case FOUR -> "4";
            case FIVE -> "5";
            case SIX -> "6";
            case SEVEN -> "7";
            case EIGHT -> "8";
            case NINE -> "9";
            case TEN -> "10";
            case JACK -> "J";
            case QUEEN -> "Q";
            case KING -> "K";
            case ACE -> "A";
        };
        String suit = switch (card.suit()) {
            case CLUBS -> "♣";
            case DIAMONDS -> "♦";
            case HEARTS -> "♥";
            case SPADES -> "♠";
        };
        return rank + suit;
    }

    private static Bitmap normalizeLandscape(Bitmap source) {
        if (source.getWidth() >= source.getHeight()) return source;
        Matrix matrix = new Matrix();
        matrix.postRotate(90f);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }
}
