package gg.fotia.tags.gui;

import gg.fotia.tags.util.MinecraftVersion;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;

/** 在各菜单间统一物品元数据的版本降级行为。 */
public final class GuiItemMetaCompat {

    private static final boolean MODERN_ITEM_META =
            MinecraftVersion.isAtLeast(Bukkit.getMinecraftVersion(), 1, 21, 4);

    private GuiItemMetaCompat() {
    }

    /**
     * 设置兼容旧配置的整数模型数据。
     * 26.x 仍支持此 API，并将整数映射为模型数据组件中的单个浮点值。
     *
     * @param meta 物品元数据
     * @param customModelData 配置中的模型编号
     */
    @SuppressWarnings("deprecation")
    public static void setCustomModelData(ItemMeta meta, int customModelData) {
        meta.setCustomModelData(customModelData);
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
