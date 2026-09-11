package io.nrv.gameagent.vision;

/** Lightweight result of enemy-health-bar detection in the gameplay viewport. */
public record EnemyObservation(
        int count,
        double nearestX,
        double nearestY,
        double confidence
) {
    private static final double PLAYER_X = 0.45;
    private static final double PLAYER_Y = 0.54;
    private static final double CENTER_DEAD_ZONE = 0.045;

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

    public double deltaX() {
        return detected() ? nearestX - PLAYER_X : 0.0;
    }

    public double deltaY() {
        return detected() ? nearestY - PLAYER_Y : 0.0;
    }

    public double distance() {
        if (!detected()) return 1.0;
        double dx = deltaX();
        double dy = deltaY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    public EnemyDirection direction() {
        if (!detected()) return EnemyDirection.NONE;

        double dx = deltaX();
        double dy = deltaY();
        boolean horizontalCenter = Math.abs(dx) <= CENTER_DEAD_ZONE;
        boolean verticalCenter = Math.abs(dy) <= CENTER_DEAD_ZONE;

        if (horizontalCenter && verticalCenter) return EnemyDirection.CENTER;
        if (horizontalCenter) return dy < 0 ? EnemyDirection.UP : EnemyDirection.DOWN;
        if (verticalCenter) return dx < 0 ? EnemyDirection.LEFT : EnemyDirection.RIGHT;

        if (dx < 0 && dy < 0) return EnemyDirection.UP_LEFT;
        if (dx > 0 && dy < 0) return EnemyDirection.UP_RIGHT;
        if (dx < 0) return EnemyDirection.DOWN_LEFT;
        return EnemyDirection.DOWN_RIGHT;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
