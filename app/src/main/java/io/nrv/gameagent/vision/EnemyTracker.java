package io.nrv.gameagent.vision;

/**
 * Tiny temporal smoother for enemy detections. It reduces frame-to-frame flicker
 * before strategic decisions are made.
 */
public final class EnemyTracker {
    private static final double ALPHA = 0.45;
    private static final int MAX_MISSES = 3;

    private EnemyObservation smoothed = EnemyObservation.none();
    private int misses = 0;

    public EnemyObservation update(EnemyObservation current) {
        if (current == null || !current.detected()) {
            misses++;
            if (misses > MAX_MISSES) {
                smoothed = EnemyObservation.none();
            }
            return smoothed;
        }

        misses = 0;
        if (!smoothed.detected()) {
            smoothed = current;
            return smoothed;
        }

        double x = lerp(smoothed.nearestX(), current.nearestX(), ALPHA);
        double y = lerp(smoothed.nearestY(), current.nearestY(), ALPHA);
        double confidence = lerp(smoothed.confidence(), current.confidence(), ALPHA);
        int count = Math.max(current.count(), smoothed.count());

        smoothed = new EnemyObservation(count, x, y, confidence);
        return smoothed;
    }

    public void reset() {
        smoothed = EnemyObservation.none();
        misses = 0;
    }

    private static double lerp(double from, double to, double alpha) {
        return from + (to - from) * alpha;
    }
}
