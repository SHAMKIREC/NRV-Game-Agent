package io.nrv.gameagent.poker;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HandEvaluatorTest {
    @Test
    public void detectsRoyalFlushAsStraightFlush() {
        HandValue value = HandEvaluator.evaluateBest(List.of(
                Card.parse("AS"), Card.parse("KS"), Card.parse("QS"), Card.parse("JS"), Card.parse("TS"), Card.parse("2D"), Card.parse("3C")
        ));
        assertEquals(HandValue.Category.STRAIGHT_FLUSH, value.category());
        assertEquals(Integer.valueOf(14), value.kickers().get(0));
    }

    @Test
    public void fullHouseBeatsFlush() {
        HandValue fullHouse = HandEvaluator.evaluateBest(List.of(
                Card.parse("AH"), Card.parse("AD"), Card.parse("AC"), Card.parse("KH"), Card.parse("KD"), Card.parse("2S"), Card.parse("3S")
        ));
        HandValue flush = HandEvaluator.evaluateBest(List.of(
                Card.parse("AS"), Card.parse("QS"), Card.parse("9S"), Card.parse("6S"), Card.parse("2S"), Card.parse("KD"), Card.parse("3C")
        ));
        assertTrue(fullHouse.compareTo(flush) > 0);
    }

    @Test
    public void wheelStraightUsesFiveHigh() {
        HandValue value = HandEvaluator.evaluateBest(List.of(
                Card.parse("AS"), Card.parse("2H"), Card.parse("3D"), Card.parse("4C"), Card.parse("5S"), Card.parse("KD"), Card.parse("QC")
        ));
        assertEquals(HandValue.Category.STRAIGHT, value.category());
        assertEquals(Integer.valueOf(5), value.kickers().get(0));
    }
}
