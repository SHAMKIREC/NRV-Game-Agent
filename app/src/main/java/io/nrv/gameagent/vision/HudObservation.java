package io.nrv.gameagent.vision;

import io.nrv.gameagent.agent.GameState;

/**
 * Lightweight observation extracted from the MLBB HUD.
 * Unknown fields are intentionally left conservative until dedicated detectors exist.
 */
public record HudObservation(
        double hpRatio,
        double manaRatio,
        double hpConfidence,
        double manaConfidence,
        boolean landscape,
        boolean dead
) {
    public HudObservation {
        hpRatio = clamp(hpRatio);
        manaRatio = clamp(manaRatio);
        hpConfidence = clamp(hpConfidence);
        manaConfidence = clamp(manaConfidence);
    }

    public GameState toGameState() {
        return new GameState(
                hpRatio,
                manaRatio,
                0,
                0,
                false,
                false,
                false,
                dead,
                1,
                0
        );
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
