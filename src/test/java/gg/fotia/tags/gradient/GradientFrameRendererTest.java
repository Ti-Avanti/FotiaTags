package gg.fotia.tags.gradient;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GradientFrameRendererTest {

    @Test
    void wrapsConfiguredTextWithAFramePhase() {
        GradientEffect effect = effect(true);

        assertEquals(
                "<gradient:#ff0000:#0000ff:0.0000>[皇帝]</gradient>",
                GradientFrameRenderer.render("[皇帝]", effect, 20)
        );
    }

    @Test
    void leavesTextUntouchedWhenTheEffectIsDisabled() {
        assertEquals("[皇帝]", GradientFrameRenderer.render("[皇帝]", effect(false), 20));
    }

    @Test
    void dynamicGradientOverridesExistingStaticColorsButKeepsDecorations() {
        String rendered = GradientFrameRenderer.render(
                "&a<gradient:#111111:#222222><bold>皇帝</bold></gradient>§x§F§F§0§0§0§0!",
                effect(true),
                0
        );

        assertEquals("<gradient:#ff0000:#0000ff:-1.0000><bold>皇帝</bold>!</gradient>", rendered);
    }

    private GradientEffect effect(boolean enabled) {
        return new GradientEffect(
                "royal",
                "皇室流光",
                enabled,
                List.of("#ff0000", "#0000ff"),
                80,
                false,
                Set.of(GradientTarget.PREFIX),
                GradientPurchase.disabled(),
                GradientIcon.defaults()
        );
    }
}
