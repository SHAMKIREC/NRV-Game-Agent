package io.nrv.gameagent.poker;

import java.util.List;

/** Result of lightweight poker-table screenshot analysis. */
public record PokerVisionObservation(
        int cardCandidates,
        int likelyHeroCards,
        int likelyBoardCards,
        List<Region> regions,
        double confidence
) {
    public PokerVisionObservation {
        cardCandidates = Math.max(0, cardCandidates);
        likelyHeroCards = Math.max(0, Math.min(2, likelyHeroCards));
        likelyBoardCards = Math.max(0, Math.min(5, likelyBoardCards));
        regions = regions == null ? List.of() : List.copyOf(regions);
        confidence = Math.max(0.0, Math.min(1.0, confidence));
    }

    public String stage() {
        return switch (likelyBoardCards) {
            case 0 -> "PREFLOP / board not detected";
            case 3 -> "FLOP";
            case 4 -> "TURN";
            case 5 -> "RIVER";
            default -> "UNKNOWN";
        };
    }

    public record Region(double left, double top, double right, double bottom) {
        public Region {
            left = clamp(left);
            top = clamp(top);
            right = clamp(right);
            bottom = clamp(bottom);
        }

        public double centerX() { return (left + right) * 0.5; }
        public double centerY() { return (top + bottom) * 0.5; }
        public double width() { return Math.max(0.0, right - left); }
        public double height() { return Math.max(0.0, bottom - top); }

        private static double clamp(double value) {
            return Math.max(0.0, Math.min(1.0, value));
        }
    }
}
