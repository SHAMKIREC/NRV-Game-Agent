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
 * Combines WPC table geometry, card recognition and local Monte-Carlo equity.
 * A card set must be seen in two consecutive analysis frames before it is trusted.
 */
public final class PokerLiveStats {
    private final WorldPokerClubProfile profile = new WorldPokerClubProfile();
    private final TemplateCardReader reader = new TemplateCardReader();
    private final ExecutorService equityExecutor = Executors.newSingleThreadExecutor();

    private List<Card> candidateHero = List.of();
    private List<Card> candidateBoard = List.of();
    private int candidateRepeats = 0;
    private List<Card> stableHero = List.of();
    private List<Card> stableBoard = List.of();
    private int stablePlayers = 0;

    public record Snapshot(
            List<Card> hero,
            List<Card> board,
            int players,
            String stage,
            String handName,
            Double win,
            Double tie,
            Double lose,
            boolean profileMatched,
            boolean cardsStable
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
                        "\nШанс победы %.0f%% · ничья %.0f%%",
                        win * 100.0, tie * 100.0));
            } else if (!cardsStable) {
                out.append("\nПроверяю карты ещё раз…");
            } else {
                out.append("\nЖду точные карты и игроков…");
            }
            return out.toString();
        }
    }

    public void analyze(Bitmap source, Consumer<Snapshot> onSuccess, Consumer<Exception> onError) {
        Bitmap landscape = normalizeLandscape(source);
        WorldPokerClubProfile.Result table = profile.analyze(landscape);

        reader.read(landscape, cards -> equityExecutor.execute(() -> {
            try {
                StableRead read = stabilize(cards, table);
                List<Card> hero = read.hero();
                List<Card> board = read.board();
                int players = read.players();

                String stage = stage(board.size(), read.stable());
                String hand = read.stable() ? handName(hero, board) : "";

                Double win = null, tie = null, lose = null;
                if (read.stable()
                        && hero.size() == 2
                        && validBoardSize(board.size())
                        && players >= 2) {
                    int opponents = Math.max(1, players - 1);
                    EquityCalculator.Result equity = new EquityCalculator()
                            .calculate(hero, board, opponents, 8_000);
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
                        table.matched(),
                        read.stable()
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

    private synchronized StableRead stabilize(TemplateCardReader.Result cards, WorldPokerClubProfile.Result table) {
        if (table.players() >= 2) stablePlayers = table.players();

        int expectedBoard = table.boardCards();
        List<Card> hero = cards.hero();
        List<Card> board = cards.board();

        // New hand / board reset: never carry a river from the previous hand into preflop.
        if (expectedBoard == 0 && !stableBoard.isEmpty()) {
            stableHero = List.of();
            stableBoard = List.of();
            candidateHero = List.of();
            candidateBoard = List.of();
            candidateRepeats = 0;
        }

        boolean countMatches = hero.size() == 2
                && validBoardSize(board.size())
                && board.size() == expectedBoard;

        if (!countMatches) {
            boolean stableStillFits = stableHero.size() == 2
                    && stableBoard.size() == expectedBoard
                    && validBoardSize(stableBoard.size());
            if (stableStillFits) {
                return new StableRead(stableHero, stableBoard, stablePlayers, true);
            }
            return new StableRead(hero, board, stablePlayers, false);
        }

        if (hero.equals(candidateHero) && board.equals(candidateBoard)) {
            candidateRepeats++;
        } else {
            candidateHero = List.copyOf(hero);
            candidateBoard = List.copyOf(board);
            candidateRepeats = 1;
        }

        if (candidateRepeats >= 2) {
            stableHero = candidateHero;
            stableBoard = candidateBoard;
        }

        boolean stable = stableHero.equals(candidateHero)
                && stableBoard.equals(candidateBoard)
                && candidateRepeats >= 2;
        return stable
                ? new StableRead(stableHero, stableBoard, stablePlayers, true)
                : new StableRead(hero, board, stablePlayers, false);
    }

    private record StableRead(List<Card> hero, List<Card> board, int players, boolean stable) {}

    private static boolean validBoardSize(int n) {
        return n == 0 || n == 3 || n == 4 || n == 5;
    }

    private static String stage(int board, boolean stable) {
        if (!stable) return "РАСПОЗНАЮ";
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
