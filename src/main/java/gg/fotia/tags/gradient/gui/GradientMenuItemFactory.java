package gg.fotia.tags.gradient.gui;

import gg.fotia.tags.FotiaTags;
import gg.fotia.tags.gradient.GradientEffect;
import gg.fotia.tags.gradient.GradientFrameRenderer;
import gg.fotia.tags.gradient.GradientIcon;
import gg.fotia.tags.gui.GuiItemMetaCompat;
import gg.fotia.tags.hook.PaymentManager;
import gg.fotia.tags.util.LegacyColorConverter;
import gg.fotia.tags.util.TextComponentParser;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class GradientMenuItemFactory {

    private final FotiaTags plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey effectKey;

    GradientMenuItemFactory(FotiaTags plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "gradient_action");
        this.effectKey = new NamespacedKey(plugin, "gradient_effect");
    }

    ItemStack createStatic(GradientMenuItem configured, Map<String, String> placeholders) {
        return create(configured, null, placeholders, configured.action());
    }

    ItemStack createEffect(GradientMenuItem configured, GradientEffect effect,
                           Map<String, String> placeholders, boolean selected) {
        return create(configured, effect, placeholders, selected ? "toggle-effect" : "select-effect");
    }

    String effectPlaceholder(GradientEffect effect, String previewText, long expireTime) {
        if (effect == null) {
            return "";
        }
        return GradientFrameRenderer.render(previewText, effect, System.currentTimeMillis() / 50L);
    }

    Map<String, String> effectPlaceholders(GradientEffect effect, String previewText, long expireTime) {
        PaymentManager.PaymentSnapshot snapshot = plugin.getPaymentManager().createSnapshot(
                effect.purchase().provider(), effect.purchase().price());
        long duration = effect.purchase().durationMillis();
        return Map.ofEntries(
                Map.entry("effect_id", effect.id()),
                Map.entry("effect_name", LegacyColorConverter.convertToMiniMessage(effect.displayName())),
                Map.entry("effect_preview", effectPlaceholder(effect, previewText, expireTime)),
                Map.entry("colors", String.join(", ", effect.colors())),
                Map.entry("duration", plugin.getMessageManager().formatDuration(duration)),
                Map.entry("expire", plugin.getMessageManager().formatExpireTime(expireTime)),
                Map.entry("provider", effect.purchase().provider()),
                Map.entry("price", effect.purchase().enabled()
                        ? plugin.getPaymentManager().getPriceText(snapshot)
                        : plugin.getMessageManager().getRaw("gradient-not-for-sale", "不可购买"))
        );
    }

    String getAction(ItemStack item) {
        return getString(item, actionKey);
    }

    String getEffectId(ItemStack item) {
        return getString(item, effectKey);
    }

    private ItemStack create(GradientMenuItem configured, GradientEffect effect,
                             Map<String, String> placeholders, String action) {
        GradientIcon icon = effect != null ? effect.icon() : null;
        Material material = configured.material();
        if (configured.useEffectIcon() && icon != null) {
            Material iconMaterial = Material.matchMaterial(icon.material());
            if (iconMaterial != null) {
                material = iconMaterial;
            }
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(parse(apply(configured.name(), placeholders)));
        List<Component> lore = new ArrayList<>();
        for (String line : configured.lore()) {
            lore.add(parse(apply(line, placeholders)));
        }
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }

        String itemModel = configured.useEffectIcon() && icon != null && !icon.itemModel().isBlank()
                ? icon.itemModel() : configured.itemModel();
        String tooltip = configured.useEffectIcon() && icon != null && !icon.tooltipStyle().isBlank()
                ? icon.tooltipStyle() : configured.tooltipStyle();
        int customModelData = configured.useEffectIcon() && icon != null && icon.customModelData() > 0
                ? icon.customModelData() : configured.customModelData();
        boolean glow = configured.glow() || configured.useEffectIcon() && icon != null && icon.glow();

        if (itemModel != null && !itemModel.isBlank()) {
            GuiItemMetaCompat.setItemModel(meta, apply(itemModel, placeholders));
        }
        if (tooltip != null && !tooltip.isBlank()) {
            GuiItemMetaCompat.setTooltipStyle(meta, apply(tooltip, placeholders));
        }
        if (customModelData > 0) {
            GuiItemMetaCompat.setCustomModelData(meta, customModelData);
        }
        if (glow) {
            GuiItemMetaCompat.setGlow(meta);
        }
        if (action != null && !action.isBlank()) {
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        }
        if (effect != null) {
            meta.getPersistentDataContainer().set(effectKey, PersistentDataType.STRING, effect.id());
        }
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private Component parse(String text) {
        return TextComponentParser.parse(text == null ? "" : text);
    }

    private String apply(String text, Map<String, String> placeholders) {
        String result = text == null ? "" : text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return result;
    }

    private String getString(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }
}
