package io.nrv.gameagent.agent;

public record GameState(
        double hpRatio,
        double manaRatio,
        int nearbyEnemies,
        int nearbyAllies,
        boolean enemyInAttackRange,
        boolean objectiveAvailable,
        boolean underEnemyTower,
        boolean dead,
        int level,
        int gold
) {
    public GameState {
        hpRatio = clamp(hpRatio);
        manaRatio = clamp(manaRatio);
        nearbyEnemies = Math.max(0, nearbyEnemies);
        nearbyAllies = Math.max(0, nearbyAllies);
        level = Math.max(1, level);
        gold = Math.max(0, gold);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
