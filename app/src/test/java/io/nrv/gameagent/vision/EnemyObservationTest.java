package io.nrv.gameagent.vision;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EnemyObservationTest {

    @Test
    public void derivesEightWayDirection() {
        assertEquals(EnemyDirection.RIGHT, new EnemyObservation(1, 0.70, 0.54, 0.9).direction());
        assertEquals(EnemyDirection.LEFT, new EnemyObservation(1, 0.20, 0.54, 0.9).direction());
        assertEquals(EnemyDirection.UP, new EnemyObservation(1, 0.45, 0.30, 0.9).direction());
        assertEquals(EnemyDirection.DOWN, new EnemyObservation(1, 0.45, 0.80, 0.9).direction());
        assertEquals(EnemyDirection.UP_RIGHT, new EnemyObservation(1, 0.70, 0.30, 0.9).direction());
        assertEquals(EnemyDirection.DOWN_LEFT, new EnemyObservation(1, 0.20, 0.80, 0.9).direction());
    }

    @Test
    public void reportsDistanceFromPlayerAnchor() {
        EnemyObservation enemy = new EnemyObservation(1, 0.55, 0.54, 0.8);
        assertEquals(0.10, enemy.distance(), 0.001);
        assertTrue(enemy.detected());
    }

    @Test
    public void noEnemyHasNoDirection() {
        assertEquals(EnemyDirection.NONE, EnemyObservation.none().direction());
    }
}
