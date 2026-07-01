package gg.fotia.tags.particle;

import org.bukkit.Material;
import org.bukkit.Particle;

public record ParticleEffect(
        String id,
        String displayName,
        boolean enabled,
        Particle particle,
        String style,
        int intervalTicks,
        int amount,
        double radius,
        double yOffset,
        double speed,
        Material guiMaterial,
        String itemModel,
        String tooltipStyle,
        int customModelData,
        boolean glow
) {
}
