package io.nrv.gameagent.poker;

import java.util.List;

public record HandValue(Category category, List<Integer> kickers) implements Comparable<HandValue> {
    public enum Category {
        HIGH_CARD, ONE_PAIR, TWO_PAIR, THREE_OF_A_KIND, STRAIGHT, FLUSH, FULL_HOUSE, FOUR_OF_A_KIND, STRAIGHT_FLUSH
    }

    @Override
    public int compareTo(HandValue other) {
        int categoryCmp = Integer.compare(category.ordinal(), other.category.ordinal());
        if (categoryCmp != 0) return categoryCmp;
        int n = Math.min(kickers.size(), other.kickers.size());
        for (int i = 0; i < n; i++) {
            int cmp = Integer.compare(kickers.get(i), other.kickers.get(i));
            if (cmp != 0) return cmp;
        }
        return Integer.compare(kickers.size(), other.kickers.size());
    }
}
