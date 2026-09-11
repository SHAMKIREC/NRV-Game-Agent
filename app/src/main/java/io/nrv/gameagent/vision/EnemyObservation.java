package io.nrv.gameagent.vision;

/** Lightweight result of enemy-health-bar detection in the gameplay viewport. */
public record EnemyObservation(
        int count,
        double nearestX,
        double nearestY,
        double confidence
) {
    public EnemyObservation {
        count = Math.max(0, count);
        nearestX = clamp(nearestX);
        nearestY = clamp(nearestY);
        confidence = clamp(confidence);
    }

    public static EnemyObservation none() {
        return new EnemyObservation(0, 0.5, 0.5, 0.0);
    }

    public boolean detected() {
        return count > 0 && confidence >= 0.20;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
