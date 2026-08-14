package gg.fotia.tags.gradient.gui;

import org.bukkit.Material;

import java.util.List;

record GradientMenuItem(
        Material material,
        String name,
        List<String> lore,
        String action,
        String itemModel,
        String tooltipStyle,
        int customModelData,
        boolean glow,
        boolean useEffectIcon
) {
}
