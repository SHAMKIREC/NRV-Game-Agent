package io.nrv.gameagent.agent;

import io.nrv.gameagent.vision.EnemyDirection;
import io.nrv.gameagent.vision.EnemyObservation;

/** Converts a strategic decision plus visual enemy position into a testable tactical intent. */
public final class TacticalPlanner {

    public TacticalIntent plan(Decision decision, GameState state, EnemyObservation enemy) {
        if (decision == null) decision = Decision.WAIT;
        if (state == null) return TacticalIntent.idle(decision);
        if (enemy == null) enemy = EnemyObservation.none();

        return switch (decision) {
            case WAIT -> TacticalIntent.idle(Decision.WAIT);
            case FARM -> TacticalIntent.idle(Decision.FARM);
            case GROUP -> TacticalIntent.idle(Decision.GROUP);
            case TAKE_OBJECTIVE -> TacticalIntent.idle(Decision.TAKE_OBJECTIVE);
            case RETREAT -> retreat(enemy);
            case ATTACK -> attack(state, enemy);
        };
    }

    private TacticalIntent attack(GameState state, EnemyObservation enemy) {
        if (!enemy.detected()) {
            return TacticalIntent.idle(Decision.ATTACK);
        }

        double[] vector = vectorFor(enemy.direction());
        boolean inRange = state.enemyInAttackRange();

        return new TacticalIntent(
                Decision.ATTACK,
                enemy.direction(),
                inRange ? 0.0 : vector[0],
                inRange ? 0.0 : vector[1],
                inRange,
                inRange && state.manaRatio() >= 0.20
        );
    }

    private TacticalIntent retreat(EnemyObservation enemy) {
        if (!enemy.detected()) {
            // No visible threat: prefer a conservative down-left fallback toward the allied side.
            return new TacticalIntent(
                    Decision.RETREAT,
                    EnemyDirection.NONE,
                    -0.70,
                    0.70,
                    false,
                    false
            );
        }

        double[] towardEnemy = vectorFor(enemy.direction());
        return new TacticalIntent(
                Decision.RETREAT,
                enemy.direction(),
                -towardEnemy[0],
                -towardEnemy[1],
                false,
                false
        );
    }

    private double[] vectorFor(EnemyDirection direction) {
        double diagonal = 0.707;
        return switch (direction) {
            case LEFT -> new double[]{-1.0, 0.0};
            case RIGHT -> new double[]{1.0, 0.0};
            case UP -> new double[]{0.0, -1.0};
            case DOWN -> new double[]{0.0, 1.0};
            case UP_LEFT -> new double[]{-diagonal, -diagonal};
            case UP_RIGHT -> new double[]{diagonal, -diagonal};
            case DOWN_LEFT -> new double[]{-diagonal, diagonal};
            case DOWN_RIGHT -> new double[]{diagonal, diagonal};
            case CENTER, NONE -> new double[]{0.0, 0.0};
        };
    }
}
