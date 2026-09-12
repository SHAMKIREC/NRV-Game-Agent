package io.nrv.gameagent.poker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class EquityCalculator {
    public record Result(double win, double tie, double lose, int simulations) {}

    private final Random random;

    public EquityCalculator() { this(new Random()); }
    EquityCalculator(Random random) { this.random = random; }

    public Result calculate(List<Card> hero, List<Card> board, int opponents, int simulations) {
        if (hero == null || hero.size() != 2) throw new IllegalArgumentException("Hero needs exactly 2 cards");
        if (board == null || board.size() > 5) throw new IllegalArgumentException("Board must contain 0 to 5 cards");
        if (opponents < 1 || opponents > 8) throw new IllegalArgumentException("Opponents must be 1 to 8");
        if (simulations < 100) throw new IllegalArgumentException("Use at least 100 simulations");

        List<Card> known = new ArrayList<>(hero);
        known.addAll(board);
        if (known.stream().distinct().count() != known.size()) throw new IllegalArgumentException("Duplicate cards");

        List<Card> fullDeck = deck();
        fullDeck.removeAll(known);

        int wins = 0, ties = 0, losses = 0;
        int needBoard = 5 - board.size();
        int need = needBoard + opponents * 2;
        if (need > fullDeck.size()) throw new IllegalArgumentException("Not enough unseen cards");

        for (int i = 0; i < simulations; i++) {
            List<Card> shuffled = new ArrayList<>(fullDeck);
            Collections.shuffle(shuffled, random);
            int index = 0;

            List<Card> runout = new ArrayList<>(board);
            for (int b = 0; b < needBoard; b++) runout.add(shuffled.get(index++));

            List<Card> heroSeven = new ArrayList<>(hero);
            heroSeven.addAll(runout);
            HandValue heroValue = HandEvaluator.evaluateBest(heroSeven);

            boolean beaten = false;
            boolean tied = false;
            for (int o = 0; o < opponents; o++) {
                List<Card> villain = List.of(shuffled.get(index++), shuffled.get(index++));
                List<Card> villainSeven = new ArrayList<>(villain);
                villainSeven.addAll(runout);
                int cmp = HandEvaluator.evaluateBest(villainSeven).compareTo(heroValue);
                if (cmp > 0) { beaten = true; break; }
                if (cmp == 0) tied = true;
            }

            if (beaten) losses++;
            else if (tied) ties++;
            else wins++;
        }

        double total = simulations;
        return new Result(wins / total, ties / total, losses / total, simulations);
    }

    public static List<Card> parseCards(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        String[] parts = raw.trim().split("[\\s,;]+");
        List<Card> result = new ArrayList<>();
        for (String part : parts) if (!part.isBlank()) result.add(Card.parse(part));
        return result;
    }

    private static List<Card> deck() {
        List<Card> cards = new ArrayList<>(52);
        for (Card.Suit suit : Card.Suit.values())
            for (Card.Rank rank : Card.Rank.values())
                cards.add(new Card(rank, suit));
        return cards;
    }
}
