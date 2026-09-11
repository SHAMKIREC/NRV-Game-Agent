package io.nrv.gameagent.vision;

/**
 * Normalized HUD regions measured from a 2048x945 MLBB landscape reference frame.
 * Coordinates are fractions of the current captured frame, so they scale across
 * landscape resolutions with the same HUD layout.
 */
public final class MlbbHudLayout {
    private MlbbHudLayout() {}

    public record Region(double left, double top, double right, double bottom) {
        public int leftPx(int width) { return (int) Math.round(left * width); }
        public int topPx(int height) { return (int) Math.round(top * height); }
        public int rightPx(int width) { return (int) Math.round(right * width); }
        public int bottomPx(int height) { return (int) Math.round(bottom * height); }
    }

    // Reference: user-provided 2048x945 landscape frame.
    public static final Region MINIMAP = new Region(0.0488, 0.0000, 0.1797, 0.2762);
    public static final Region SCORE_TIME = new Region(0.3906, 0.0000, 0.6045, 0.1058);
    public static final Region GOLD = new Region(0.7700, 0.0000, 0.8370, 0.0783);
    public static final Region PLAYER_HUD = new Region(0.3900, 0.3150, 0.4900, 0.3900);
    public static final Region JOYSTICK = new Region(0.0800, 0.5550, 0.2450, 0.8850);
    public static final Region BASIC_ATTACK = new Region(0.7130, 0.6450, 0.8650, 0.9650);
    public static final Region SKILL_1 = new Region(0.6000, 0.6800, 0.6900, 0.8750);
    public static final Region SKILL_2 = new Region(0.7100, 0.4400, 0.8050, 0.6600);
    public static final Region REGEN = new Region(0.4800, 0.7350, 0.5400, 0.8750);
    public static final Region RECALL = new Region(0.4200, 0.7350, 0.4800, 0.8750);

    public static boolean looksLandscape(int width, int height) {
        return width > height && ((double) width / Math.max(1, height)) > 1.7;
    }
}
