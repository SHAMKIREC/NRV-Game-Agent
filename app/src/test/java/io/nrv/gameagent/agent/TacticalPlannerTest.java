package io.nrv.gameagent.agent;

import org.junit.Test;

import io.nrv.gameagent.vision.EnemyDirection;
import io.nrv.gameagent.vision.EnemyObservation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TacticalPlannerTest {

    @Test
    public void approachesEnemyWhenAttackIsOutOfRange() {
        GameState state = new GameState(0.90, 0.80, 1, 1, false, false, false, false, 4, 1200);
        EnemyObservation enemy = new EnemyObservation(1, 0.72, 0.54, 0.9);

        TacticalIntent intent = new TacticalPlanner().plan(Decision.ATTACK, state, enemy);

        assertEquals(EnemyDirection.RIGHT, intent.enemyDirection());
        assertTrue(intent.moveX() > 0.9);
        assertEquals(0.0, intent.moveY(), 0.001);
        assertFalse(intent.wantsBasicAttack());
    }

    @Test
    public void attacksWhenEnemyIsInRange() {
        GameState state = new GameState(0.90, 0.80, 1, 1, true, false, false, false, 4, 1200);
        EnemyObservation enemy = new EnemyObservation(1, 0.56, 0.54, 0.9);

        TacticalIntent intent = new TacticalPlanner().plan(Decision.ATTACK, state, enemy);

        assertEquals(0.0, intent.moveX(), 0.001);
        assertEquals(0.0, intent.moveY(), 0.001);
        assertTrue(intent.wantsBasicAttack());
        assertTrue(intent.wantsSkill());
    }

    @Test
    public void retreatsOppositeVisibleEnemy() {
        GameState state = new GameState(0.20, 0.40, 1, 0, true, false, false, false, 2, 400);
        EnemyObservation enemy = new EnemyObservation(1, 0.70, 0.30, 0.9);

        TacticalIntent intent = new TacticalPlanner().plan(Decision.RETREAT, state, enemy);

        assertEquals(EnemyDirection.UP_RIGHT, intent.enemyDirection());
        assertTrue(intent.moveX() < 0.0);
        assertTrue(intent.moveY() > 0.0);
        assertFalse(intent.wantsBasicAttack());
    }
}
