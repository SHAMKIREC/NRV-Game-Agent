package io.nrv.gameagent.poker;

import java.util.Locale;

public record Card(Rank rank, Suit suit) {
    public enum Suit { CLUBS, DIAMONDS, HEARTS, SPADES }
    public enum Rank {
        TWO(2), THREE(3), FOUR(4), FIVE(5), SIX(6), SEVEN(7), EIGHT(8), NINE(9), TEN(10), JACK(11), QUEEN(12), KING(13), ACE(14);
        private final int value;
        Rank(int value) { this.value = value; }
        public int value() { return value; }
    }

    public static Card parse(String raw) {
        if (raw == null) throw new IllegalArgumentException("Card is null");
        String s = raw.trim().toUpperCase(Locale.ROOT).replace("10", "T");
        if (s.length() != 2) throw new IllegalArgumentException("Use format AS, QH, TD, 7C");
        Rank rank = switch (s.charAt(0)) {
            case '2' -> Rank.TWO; case '3' -> Rank.THREE; case '4' -> Rank.FOUR; case '5' -> Rank.FIVE;
            case '6' -> Rank.SIX; case '7' -> Rank.SEVEN; case '8' -> Rank.EIGHT; case '9' -> Rank.NINE;
            case 'T' -> Rank.TEN; case 'J' -> Rank.JACK; case 'Q' -> Rank.QUEEN; case 'K' -> Rank.KING; case 'A' -> Rank.ACE;
            default -> throw new IllegalArgumentException("Unknown rank: " + s.charAt(0));
        };
        Suit suit = switch (s.charAt(1)) {
            case 'C' -> Suit.CLUBS; case 'D' -> Suit.DIAMONDS; case 'H' -> Suit.HEARTS; case 'S' -> Suit.SPADES;
            default -> throw new IllegalArgumentException("Unknown suit: " + s.charAt(1));
        };
        return new Card(rank, suit);
    }

    @Override public String toString() {
        String r = switch (rank) {
            case TWO -> "2"; case THREE -> "3"; case FOUR -> "4"; case FIVE -> "5"; case SIX -> "6"; case SEVEN -> "7";
            case EIGHT -> "8"; case NINE -> "9"; case TEN -> "T"; case JACK -> "J"; case QUEEN -> "Q"; case KING -> "K"; case ACE -> "A";
        };
        String s = switch (suit) { case CLUBS -> "C"; case DIAMONDS -> "D"; case HEARTS -> "H"; case SPADES -> "S"; };
        return r + s;
    }
}
