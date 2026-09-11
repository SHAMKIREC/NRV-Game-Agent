package io.nrv.gameagent.agent;

import io.nrv.gameagent.vision.EnemyDirection;

/**
 * High-level intent for a controlled/simulated input layer.
 * It describes what the agent wants to do without performing Android touches.
 */
public record TacticalIntent(
        Decision decision,
        EnemyDirection enemyDirection,
        double moveX,
        double moveY,
        boolean wantsBasicAttack,
        boolean wantsSkill
) {
    public TacticalIntent {
        moveX = clamp(moveX);
        moveY = clamp(moveY);
    }

    public static TacticalIntent idle(Decision decision) {
        return new TacticalIntent(decision, EnemyDirection.NONE, 0.0, 0.0, false, false);
    }

    private static double clamp(double value) {
        return Math.max(-1.0, Math.min(1.0, value));
    }
}
