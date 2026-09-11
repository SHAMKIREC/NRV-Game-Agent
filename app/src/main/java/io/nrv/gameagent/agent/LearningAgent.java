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
