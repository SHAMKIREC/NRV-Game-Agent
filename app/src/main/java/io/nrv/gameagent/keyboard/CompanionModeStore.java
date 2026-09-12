package io.nrv.gameagent.keyboard;

import android.content.Context;

/** Persists which on-device analyzer should consume the approved screen capture. */
public final class CompanionModeStore {
    private static final String PREFS = "nrv_ai_keyboard";
    private static final String KEY_MODE = "analysis_mode";

    public enum Mode {
        GENERAL,
        POKER,
        MOBA
    }

    private CompanionModeStore() {}

    public static void set(Context context, Mode mode) {
        if (context == null || mode == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_MODE, mode.name())
                .apply();
        ScreenInsightStore.publish("Режим: " + title(mode) + ". Нажми «ЭКРАН», если захват ещё не запущен.");
    }

    public static Mode get(Context context) {
        if (context == null) return Mode.GENERAL;
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_MODE, Mode.GENERAL.name());
        try {
            return Mode.valueOf(raw);
        } catch (Exception ignored) {
            return Mode.GENERAL;
        }
    }

    public static String title(Mode mode) {
        return switch (mode) {
            case POKER -> "POKER";
            case MOBA -> "MOBA";
            case GENERAL -> "AI";
        };
    }
}
