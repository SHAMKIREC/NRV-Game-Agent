package io.nrv.gameagent.input;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

import io.nrv.gameagent.agent.TacticalIntent;
import io.nrv.gameagent.vision.MlbbHudLayout;

/**
 * Executes coarse joystick/button gestures when the user explicitly enables
 * the Android accessibility service and the local automation switch.
 */
public final class GameAccessibilityService extends AccessibilityService {
    private static volatile GameAccessibilityService instance;
    private static final long MIN_GESTURE_INTERVAL_MS = 140L;

    private long lastGestureAt = 0L;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // No UI scraping is needed. Screen understanding comes from MediaProjection.
    }

    @Override
    public void onInterrupt() {
        // Nothing persistent to cancel here.
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public static boolean isConnected() {
        return instance != null;
    }

    public static boolean executeIfEnabled(
            TacticalIntent intent,
            int width,
            int height
    ) {
        GameAccessibilityService service = instance;
        if (service == null || intent == null || width <= 0 || height <= 0) return false;
        if (!AutomationSettings.isEnabled(service)) return false;
        return service.execute(intent, width, height);
    }

    private boolean execute(TacticalIntent intent, int width, int height) {
        long now = SystemClock.uptimeMillis();
        if (now - lastGestureAt < MIN_GESTURE_INTERVAL_MS) return false;

        GestureDescription.Builder builder = new GestureDescription.Builder();
        boolean hasStroke = false;

        if (Math.abs(intent.moveX()) > 0.05 || Math.abs(intent.moveY()) > 0.05) {
            addMovementStroke(builder, intent, width, height);
            hasStroke = true;
        }

        if (intent.wantsBasicAttack()) {
            addTapStroke(builder, MlbbHudLayout.BASIC_ATTACK, width, height, 0L);
            hasStroke = true;
        }

        if (intent.wantsSkill()) {
            addTapStroke(builder, MlbbHudLayout.SKILL_1, width, height, 35L);
            hasStroke = true;
        }

        if (!hasStroke) return false;

        lastGestureAt = now;
        return dispatchGesture(builder.build(), null, null);
    }

    private void addMovementStroke(
            GestureDescription.Builder builder,
            TacticalIntent intent,
            int width,
            int height
    ) {
        MlbbHudLayout.Region joystick = MlbbHudLayout.JOYSTICK;
        float centerX = centerX(joystick, width);
        float centerY = centerY(joystick, height);
        float radiusX = Math.max(24f, (joystick.rightPx(width) - joystick.leftPx(width)) * 0.32f);
        float radiusY = Math.max(24f, (joystick.bottomPx(height) - joystick.topPx(height)) * 0.32f);

        float targetX = centerX + (float) intent.moveX() * radiusX;
        float targetY = centerY + (float) intent.moveY() * radiusY;

        Path path = new Path();
        path.moveTo(centerX, centerY);
        path.lineTo(targetX, targetY);
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0L, 130L));
    }

    private void addTapStroke(
            GestureDescription.Builder builder,
            MlbbHudLayout.Region region,
            int width,
            int height,
            long delayMs
    ) {
        Path path = new Path();
        path.moveTo(centerX(region, width), centerY(region, height));
        builder.addStroke(new GestureDescription.StrokeDescription(path, delayMs, 45L));
    }

    private float centerX(MlbbHudLayout.Region region, int width) {
        return (region.leftPx(width) + region.rightPx(width)) * 0.5f;
    }

    private float centerY(MlbbHudLayout.Region region, int height) {
        return (region.topPx(height) + region.bottomPx(height)) * 0.5f;
    }
}
