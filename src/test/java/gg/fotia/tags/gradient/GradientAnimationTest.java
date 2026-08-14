package gg.fotia.tags.gradient;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GradientAnimationTest {

    @Test
    void phaseMovesSmoothlyFromStartToEndAndBack() {
        assertEquals(-1.0, GradientAnimation.phaseAt(0, 80, false), 0.0001);
        assertEquals(0.0, GradientAnimation.phaseAt(20, 80, false), 0.0001);
        assertEquals(1.0, GradientAnimation.phaseAt(40, 80, false), 0.0001);
        assertEquals(0.0, GradientAnimation.phaseAt(60, 80, false), 0.0001);
        assertEquals(-1.0, GradientAnimation.phaseAt(80, 80, false), 0.0001);
    }

    @Test
    void reverseInvertsTheCurrentPhase() {
        assertEquals(0.5, GradientAnimation.phaseAt(10, 80, true), 0.0001);
        assertEquals(-0.5, GradientAnimation.phaseAt(30, 80, true), 0.0001);
    }
}
