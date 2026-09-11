package io.nrv.gameagent.vision;

import io.nrv.gameagent.agent.GameState;

/** Lightweight observation extracted from the MLBB frame. */
public record HudObservation(
        double hpRatio,
        double manaRatio,
        double hpConfidence,
        double manaConfidence,
        boolean landscape,
        boolean dead,
        EnemyObservation enemies
) {
    public HudObservation {
        hpRatio = clamp(hpRatio);
        manaRatio = clamp(manaRatio);
        hpConfidence = clamp(hpConfidence);
        manaConfidence = clamp(manaConfidence);
        enemies = enemies == null ? EnemyObservation.none() : enemies;
    }

    public GameState toGameState() {
        boolean enemyInAttackRange = enemies.detected()
                && distanceFromPlayer(enemies.nearestX(), enemies.nearestY()) <= 0.34;

        return new GameState(
                hpRatio,
                manaRatio,
                enemies.count(),
                0,
                enemyInAttackRange,
                false,
                false,
                dead,
                1,
                0
        );
    }

    private static double distanceFromPlayer(double x, double y) {
        // Approximate on-screen hero anchor for the supplied MLBB layout.
        double dx = x - 0.45;
        double dy = y - 0.54;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
