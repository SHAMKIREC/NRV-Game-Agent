package io.nrv.gameagent.poker;

import java.util.List;

/** Result of lightweight poker-table screenshot analysis. */
public record PokerVisionObservation(
        int cardCandidates,
        int likelyHeroCards,
        int likelyBoardCards,
        List<Region> regions,
        double confidence,
        String captureOrientation,
        boolean normalizedToLandscape,
        boolean tableDetected,
        boolean heroZoneDetected,
        boolean boardZoneDetected,
        int seatActivityCandidates
) {
    public PokerVisionObservation {
        cardCandidates = Math.max(0, cardCandidates);
        likelyHeroCards = Math.max(0, Math.min(2, likelyHeroCards));
        likelyBoardCards = Math.max(0, Math.min(5, likelyBoardCards));
        regions = regions == null ? List.of() : List.copyOf(regions);
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        captureOrientation = captureOrientation == null ? "unknown" : captureOrientation;
        seatActivityCandidates = Math.max(0, Math.min(10, seatActivityCandidates));
    }

    /** Compatibility constructor used by older callers/tests. */
    public PokerVisionObservation(
            int cardCandidates,
            int likelyHeroCards,
            int likelyBoardCards,
            List<Region> regions,
            double confidence
    ) {
        this(cardCandidates, likelyHeroCards, likelyBoardCards, regions, confidence,
                "unknown", false, false, false, false, 0);
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

    public String diagnosticLine() {
        return "table:" + yesNo(tableDetected)
                + " · hero-zone:" + yesNo(heroZoneDetected)
                + " · board-zone:" + yesNo(boardZoneDetected)
                + " · seat-zones:" + seatActivityCandidates;
    }

    private static String yesNo(boolean value) {
        return value ? "YES" : "NO";
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
