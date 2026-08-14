package gg.fotia.tags.gradient;

import java.util.List;
import java.util.Set;

public record GradientEffect(
        String id,
        String displayName,
        boolean enabled,
        List<String> colors,
        int durationTicks,
        boolean reversed,
        Set<GradientTarget> targets,
        GradientPurchase purchase,
        GradientIcon icon
) {

    public GradientEffect {
        id = id == null ? "" : id;
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        colors = colors == null ? List.of() : List.copyOf(colors);
        durationTicks = Math.max(4, durationTicks);
        targets = targets == null ? Set.of() : Set.copyOf(targets);
        purchase = purchase == null ? GradientPurchase.disabled() : purchase;
        icon = icon == null ? GradientIcon.defaults() : icon;
    }

    public boolean appliesTo(GradientTarget target) {
        return targets.contains(target);
    }
}
