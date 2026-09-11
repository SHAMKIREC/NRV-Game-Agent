package io.nrv.gameagent.agent;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LearningAgentTest {

    @Test
    public void learnsRewardedActionForKnownState() {
        LearningAgent agent = new LearningAgent(0.25, 0.90, 0.0, 42L);
        GameState lowHp = new GameState(0.20, 0.50, 1, 0, true, false, false, false, 3, 900);
        GameState safe = new GameState(0.70, 0.50, 0, 1, false, false, false, false, 3, 900);

        for (int i = 0; i < 100; i++) {
            agent.learn(lowHp, Decision.RETREAT, 5.0, safe, true);
        }

        assertEquals(Decision.RETREAT, agent.decide(lowHp));
        assertTrue(agent.qValue(lowHp, Decision.RETREAT) > 0.0);
        // A terminal transition deliberately does not bootstrap from nextState,
        // therefore only the current state is inserted into the Q-table.
        assertEquals(1, agent.knownStates());
    }

    @Test
    public void learnsNextStateForNonTerminalTransition() {
        LearningAgent agent = new LearningAgent(0.25, 0.90, 0.0, 42L);
        GameState current = new GameState(0.45, 0.50, 1, 1, true, false, false, false, 3, 900);
        GameState next = new GameState(0.70, 0.55, 0, 1, false, false, false, false, 3, 980);

        agent.learn(current, Decision.RETREAT, 2.0, next, false);

        assertEquals(2, agent.knownStates());
    }

    @Test
    public void epsilonCanBeDisabledAfterExploration() {
        LearningAgent agent = new LearningAgent(0.20, 0.90, 1.0, 7L);
        GameState state = new GameState(0.80, 0.70, 0, 0, false, false, false, false, 2, 500);

        agent.learn(state, Decision.FARM, 3.0, state, true);
        agent.setEpsilon(0.0);

        assertEquals(Decision.FARM, agent.decide(state));
    }

    @Test
    public void snapshotRestoresLearnedPolicy() {
        LearningAgent original = new LearningAgent(0.30, 0.90, 0.0, 1L);
        GameState state = new GameState(0.45, 0.60, 1, 1, false, false, false, false, 4, 1400);

        for (int i = 0; i < 50; i++) {
            original.learn(state, Decision.FARM, 4.0, state, true);
        }

        String snapshot = original.snapshot();

        LearningAgent restored = new LearningAgent(0.30, 0.90, 0.0, 2L);
        restored.restore(snapshot);

        assertEquals(Decision.FARM, restored.decide(state));
        assertTrue(restored.qValue(state, Decision.FARM) > 0.0);
    }
}
