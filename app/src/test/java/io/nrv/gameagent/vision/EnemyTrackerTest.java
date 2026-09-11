package io.nrv.gameagent.vision;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EnemyTrackerTest {

    @Test
    public void smoothsPositionAcrossFrames() {
        EnemyTracker tracker = new EnemyTracker();
        tracker.update(new EnemyObservation(1, 0.60, 0.50, 0.8));
        EnemyObservation smoothed = tracker.update(new EnemyObservation(1, 0.80, 0.50, 0.8));

        assertTrue(smoothed.nearestX() > 0.60);
        assertTrue(smoothed.nearestX() < 0.80);
        assertEquals(EnemyDirection.RIGHT, smoothed.direction());
    }

    @Test
    public void toleratesShortDetectionDropouts() {
        EnemyTracker tracker = new EnemyTracker();
        tracker.update(new EnemyObservation(1, 0.70, 0.54, 0.9));

        assertTrue(tracker.update(EnemyObservation.none()).detected());
        assertTrue(tracker.update(EnemyObservation.none()).detected());
        assertTrue(tracker.update(EnemyObservation.none()).detected());
        assertTrue(!tracker.update(EnemyObservation.none()).detected());
    }
}
