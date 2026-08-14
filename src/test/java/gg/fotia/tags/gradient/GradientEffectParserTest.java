package gg.fotia.tags.gradient;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradientEffectParserTest {

    @Test
    void parsesAnimationPurchaseTargetsAndIcon() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                enabled: true
                display-name: "<gold>皇室流光"
                colors: ["&#FF0000", "#0000FF"]
                duration-ticks: 80
                reverse: true
                apply-to: [prefix, suffix]
                purchase:
                  enabled: true
                  provider: playerpoints
                  price: 500
                  duration: 30d
                gui:
                  material: AMETHYST_SHARD
                  item-model: "fotia:gradient/royal"
                  tooltip-style: "fotia:gradient"
                  custom-model-data: 7
                  glow: true
                """);

        GradientEffect effect = GradientEffectParser.parse("royal", config);

        assertEquals("royal", effect.id());
        assertEquals(Set.of(GradientTarget.PREFIX, GradientTarget.SUFFIX), effect.targets());
        assertEquals("#ff0000", effect.colors().get(0));
        assertEquals("#0000ff", effect.colors().get(1));
        assertTrue(effect.reversed());
        assertTrue(effect.purchase().enabled());
        assertEquals("playerpoints", effect.purchase().provider());
        assertEquals(500.0, effect.purchase().price());
        assertEquals(2_592_000_000L, effect.purchase().durationMillis());
        assertEquals("AMETHYST_SHARD", effect.icon().material());
        assertTrue(effect.icon().glow());
    }

    @Test
    void defaultsToPrefixAndSuffixWithPurchasingDisabled() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("colors: ['#123456', '#abcdef']");

        GradientEffect effect = GradientEffectParser.parse("basic", config);

        assertEquals(Set.of(GradientTarget.PREFIX, GradientTarget.SUFFIX), effect.targets());
        assertFalse(effect.purchase().enabled());
    }

    @Test
    void rejectsTemplatesWithFewerThanTwoValidColors() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("colors: ['not-a-color', '#abcdef']");

        assertThrows(IllegalArgumentException.class,
                () -> GradientEffectParser.parse("broken", config));
    }

    @Test
    void rejectsFractionalPlayerPointsPrices() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                colors: ['#123456', '#abcdef']
                purchase:
                  enabled: true
                  provider: playerpoints
                  price: 0.9
                  duration: permanent
                """);

        assertThrows(IllegalArgumentException.class,
                () -> GradientEffectParser.parse("fractional", config));
    }
}
