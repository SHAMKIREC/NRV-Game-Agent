package io.nrv.gameagent.agent;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RuleAgentTest {

    private final RuleAgent agent = new RuleAgent();

    @Test
    public void retreatsOnLowHp() {
        GameState state = new GameState(0.20, 0.80, 1, 1, true, false, false, false, 4, 1200);
        assertEquals(Decision.RETREAT, agent.decide(state));
    }

    @Test
    public void attacksWhenHealthyAndSupported() {
        GameState state = new GameState(0.85, 0.70, 1, 1, true, false, false, false, 6, 2600);
        assertEquals(Decision.ATTACK, agent.decide(state));
    }

    @Test
    public void prioritizesObjectiveWhenSafe() {
        GameState state = new GameState(0.90, 0.65, 1, 2, false, true, false, false, 7, 3300);
        assertEquals(Decision.TAKE_OBJECTIVE, agent.decide(state));
    }

    @Test
    public void waitsWhileDead() {
        GameState state = new GameState(0.0, 0.0, 0, 0, false, false, false, true, 5, 1800);
        assertEquals(Decision.WAIT, agent.decide(state));
    }
}
