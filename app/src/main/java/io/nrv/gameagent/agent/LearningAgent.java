package io.nrv.gameagent.agent;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public final class LearningAgent {

    private final Map<String, double[]> qTable = new HashMap<>();
    private final Random random;
    private final double alpha;
    private final double gamma;
    private double epsilon;

    public LearningAgent(double alpha, double gamma, double epsilon, long seed) {
        this.alpha = alpha;
        this.gamma = gamma;
        this.epsilon = epsilon;
        this.random = new Random(seed);
    }

    public Decision decide(GameState state) {
        if (random.nextDouble() < epsilon) {
            Decision[] actions = Decision.values();
            return actions[random.nextInt(actions.length)];
        }

        double[] values = valuesFor(state);
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[best]) best = i;
        }
        return Decision.values()[best];
    }

    public void learn(
            GameState state,
            Decision action,
            double reward,
            GameState nextState,
            boolean terminal
    ) {
        double[] current = valuesFor(state);
        int actionIndex = action.ordinal();

        double future = terminal ? 0.0 : max(valuesFor(nextState));
        double target = reward + gamma * future;
        current[actionIndex] += alpha * (target - current[actionIndex]);
    }

    public void setEpsilon(double epsilon) {
        this.epsilon = Math.max(0.0, Math.min(1.0, epsilon));
    }

    public double qValue(GameState state, Decision action) {
        return valuesFor(state)[action.ordinal()];
    }

    public int knownStates() {
        return qTable.size();
    }

    public String snapshot() {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, double[]> entry : qTable.entrySet()) {
            out.append(entry.getKey());
            for (double value : entry.getValue()) {
                out.append('\t').append(value);
            }
            out.append('\n');
        }
        return out.toString();
    }

    public void restore(String snapshot) {
        qTable.clear();
        if (snapshot == null || snapshot.isBlank()) return;

        String[] lines = snapshot.split("\\R");
        for (String line : lines) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\t");
            if (parts.length != Decision.values().length + 1) continue;

            double[] values = new double[Decision.values().length];
            boolean valid = true;
            for (int i = 0; i < values.length; i++) {
                try {
                    values[i] = Double.parseDouble(parts[i + 1]);
                } catch (NumberFormatException error) {
                    valid = false;
                    break;
                }
            }
            if (valid) qTable.put(parts[0], values);
        }
    }

    private double[] valuesFor(GameState state) {
        return qTable.computeIfAbsent(key(state), ignored -> new double[Decision.values().length]);
    }

    private String key(GameState s) {
        return hpBucket(s.hpRatio()) + ":" +
                Math.min(s.nearbyEnemies(), 3) + ":" +
                Math.min(s.nearbyAllies(), 3) + ":" +
                bool(s.enemyInAttackRange()) + ":" +
                bool(s.objectiveAvailable()) + ":" +
                bool(s.underEnemyTower()) + ":" +
                bool(s.dead());
    }

    private int hpBucket(double hp) {
        if (hp <= 0.25) return 0;
        if (hp <= 0.50) return 1;
        if (hp <= 0.75) return 2;
        return 3;
    }

    private int bool(boolean value) {
        return value ? 1 : 0;
    }

    private double max(double[] values) {
        return Arrays.stream(values).max().orElse(0.0);
    }
}
