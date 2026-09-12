package io.nrv.gameagent.poker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HandEvaluator {
    private HandEvaluator() {}

    public static HandValue evaluateBest(List<Card> cards) {
        if (cards == null || cards.size() < 5 || cards.size() > 7) {
            throw new IllegalArgumentException("Need 5 to 7 cards");
        }
        HandValue best = null;
        int n = cards.size();
        for (int a = 0; a < n - 4; a++)
            for (int b = a + 1; b < n - 3; b++)
                for (int c = b + 1; c < n - 2; c++)
                    for (int d = c + 1; d < n - 1; d++)
                        for (int e = d + 1; e < n; e++) {
                            HandValue value = evaluateFive(List.of(cards.get(a), cards.get(b), cards.get(c), cards.get(d), cards.get(e)));
                            if (best == null || value.compareTo(best) > 0) best = value;
                        }
        return best;
    }

    static HandValue evaluateFive(List<Card> cards) {
        List<Integer> ranks = cards.stream().map(c -> c.rank().value()).sorted(Comparator.reverseOrder()).toList();
        boolean flush = cards.stream().map(Card::suit).distinct().count() == 1;
        int straightHigh = straightHigh(ranks);

        if (flush && straightHigh > 0) return new HandValue(HandValue.Category.STRAIGHT_FLUSH, List.of(straightHigh));

        Map<Integer, Integer> counts = new HashMap<>();
        for (int rank : ranks) counts.merge(rank, 1, Integer::sum);

        List<Integer> quads = valuesWithCount(counts, 4);
        if (!quads.isEmpty()) {
            int q = quads.get(0);
            int kicker = ranks.stream().filter(r -> r != q).findFirst().orElse(0);
            return new HandValue(HandValue.Category.FOUR_OF_A_KIND, List.of(q, kicker));
        }

        List<Integer> trips = valuesWithCount(counts, 3);
        List<Integer> pairs = valuesWithCount(counts, 2);
        if (!trips.isEmpty() && (!pairs.isEmpty() || trips.size() > 1)) {
            int trip = trips.get(0);
            int pair = !pairs.isEmpty() ? pairs.get(0) : trips.get(1);
            return new HandValue(HandValue.Category.FULL_HOUSE, List.of(trip, pair));
        }

        if (flush) return new HandValue(HandValue.Category.FLUSH, ranks);
        if (straightHigh > 0) return new HandValue(HandValue.Category.STRAIGHT, List.of(straightHigh));

        if (!trips.isEmpty()) {
            int trip = trips.get(0);
            List<Integer> kickers = new ArrayList<>();
            kickers.add(trip);
            ranks.stream().filter(r -> r != trip).distinct().limit(2).forEach(kickers::add);
            return new HandValue(HandValue.Category.THREE_OF_A_KIND, kickers);
        }

        if (pairs.size() >= 2) {
            int highPair = pairs.get(0), lowPair = pairs.get(1);
            int kicker = ranks.stream().filter(r -> r != highPair && r != lowPair).findFirst().orElse(0);
            return new HandValue(HandValue.Category.TWO_PAIR, List.of(highPair, lowPair, kicker));
        }

        if (pairs.size() == 1) {
            int pair = pairs.get(0);
            List<Integer> kickers = new ArrayList<>();
            kickers.add(pair);
            ranks.stream().filter(r -> r != pair).distinct().limit(3).forEach(kickers::add);
            return new HandValue(HandValue.Category.ONE_PAIR, kickers);
        }

        return new HandValue(HandValue.Category.HIGH_CARD, ranks);
    }

    private static List<Integer> valuesWithCount(Map<Integer, Integer> counts, int target) {
        return counts.entrySet().stream()
                .filter(e -> e.getValue() == target)
                .map(Map.Entry::getKey)
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    private static int straightHigh(List<Integer> ranksDesc) {
        List<Integer> unique = ranksDesc.stream().distinct().sorted().toList();
        if (unique.contains(14)) {
            List<Integer> wheel = new ArrayList<>(unique);
            wheel.add(1);
            unique = wheel.stream().distinct().sorted().toList();
        }
        int run = 1;
        int best = 0;
        for (int i = 1; i < unique.size(); i++) {
            if (unique.get(i) == unique.get(i - 1) + 1) {
                run++;
                if (run >= 5) best = unique.get(i);
            } else run = 1;
        }
        return best;
    }
}
