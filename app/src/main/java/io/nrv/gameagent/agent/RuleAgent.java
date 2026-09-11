package io.nrv.gameagent.agent;

public final class RuleAgent {

    public Decision decide(GameState state) {
        if (state.dead()) return Decision.WAIT;

        if (state.hpRatio() <= 0.25 || state.underEnemyTower()) {
            return Decision.RETREAT;
        }

        boolean outnumbered = state.nearbyEnemies() > state.nearbyAllies() + 1;
        if (outnumbered && state.hpRatio() < 0.70) {
            return Decision.RETREAT;
        }

        if (state.objectiveAvailable()
                && state.nearbyAllies() >= state.nearbyEnemies()
                && state.hpRatio() >= 0.60) {
            return Decision.TAKE_OBJECTIVE;
        }

        if (state.enemyInAttackRange()
                && state.hpRatio() >= 0.55
                && state.nearbyEnemies() <= state.nearbyAllies() + 1) {
            return Decision.ATTACK;
        }

        if (state.nearbyAllies() >= 2 && state.nearbyEnemies() >= 1) {
            return Decision.GROUP;
        }

        return Decision.FARM;
    }
}
