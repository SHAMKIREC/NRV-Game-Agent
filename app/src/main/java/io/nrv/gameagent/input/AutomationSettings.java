package io.nrv.gameagent.input;

import android.content.Context;
import android.content.SharedPreferences;

/** Explicit local switch for gesture execution. Disabled by default. */
public final class AutomationSettings {
    private static final String PREFS = "nrv_agent_settings";
    private static final String KEY_ENABLED = "automation_enabled";

    private AutomationSettings() {}

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
