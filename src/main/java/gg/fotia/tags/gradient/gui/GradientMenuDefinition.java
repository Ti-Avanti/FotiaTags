package gg.fotia.tags.gradient.gui;

import java.util.List;
import java.util.Map;

record GradientMenuDefinition(
        String title,
        int size,
        List<String> layout,
        Map<Character, GradientMenuItem> items,
        List<Integer> effectSlots,
        GradientMenuItem selectedEffectItem,
        GradientMenuItem unselectedEffectItem,
        boolean hideOwned
) {
}
