package io.nrv.gameagent.agent;

import android.content.Context;
import android.content.SharedPreferences;

public final class AgentStore {

    private static final String PREFS = "nrv_agent_learning";
    private static final String KEY_Q_TABLE = "q_table";

    private final SharedPreferences preferences;

    public AgentStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void save(LearningAgent agent) {
        preferences.edit().putString(KEY_Q_TABLE, agent.snapshot()).apply();
    }

    public void restoreInto(LearningAgent agent) {
        agent.restore(preferences.getString(KEY_Q_TABLE, ""));
    }

    public void clear() {
        preferences.edit().remove(KEY_Q_TABLE).apply();
    }
}
