package gg.fotia.tags.gui;

import gg.fotia.tags.util.MinecraftVersion;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;

public final class GuiItemMetaCompat {

    private static final boolean MODERN_ITEM_META =
            MinecraftVersion.isAtLeast(Bukkit.getBukkitVersion(), 1, 21, 4);

    private GuiItemMetaCompat() {
    }

    public static void setItemModel(ItemMeta meta, String itemModel) {
        if (!MODERN_ITEM_META) {
            return;
        }
        NamespacedKey key = NamespacedKey.fromString(itemModel);
        if (key != null) {
            meta.setItemModel(key);
        }
    }

    public static void setTooltipStyle(ItemMeta meta, String tooltipStyle) {
        if (!MODERN_ITEM_META) {
            return;
        }
        NamespacedKey key = NamespacedKey.fromString(tooltipStyle);
        if (key != null) {
            meta.setTooltipStyle(key);
        }
    }

    public static void setGlow(ItemMeta meta) {
        if (MODERN_ITEM_META) {
            meta.setEnchantmentGlintOverride(true);
            return;
        }
        Enchantment unbreaking = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
        if (unbreaking != null) {
            meta.addEnchant(unbreaking, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
    }
}
