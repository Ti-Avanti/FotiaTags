package gg.fotia.tags.gradient.gui;

import gg.fotia.tags.FotiaTags;
import gg.fotia.tags.core.MessageManager;
import gg.fotia.tags.gradient.GradientEffect;
import gg.fotia.tags.gradient.GradientPurchaseResult;
import gg.fotia.tags.gradient.GradientPurchaseService;
import gg.fotia.tags.util.LegacyColorConverter;
import gg.fotia.tags.util.TextComponentParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GradientMenuManager implements Listener {

    private final FotiaTags plugin;
    private final GradientMenuItemFactory itemFactory;
    private final GradientPurchaseService purchaseService;
    private final Map<UUID, MenuSession> sessions = new HashMap<>();
    private final Set<UUID> reopening = new HashSet<>();
    private GradientMenuDefinition storageMenu;
    private GradientMenuDefinition shopMenu;
    private GradientMenuDefinition confirmMenu;
    private String previewText;

    public GradientMenuManager(FotiaTags plugin) {
        this.plugin = plugin;
        this.itemFactory = new GradientMenuItemFactory(plugin);
        this.purchaseService = new GradientPurchaseService(plugin);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        reload();
    }

    public void reload() {
        GradientMenuConfigLoader loader = new GradientMenuConfigLoader(plugin);
        storageMenu = loader.load("menus/gradient-storage.yml", GradientMenuConfigLoader.storageDefaults());
        shopMenu = loader.load("menus/gradient-shop.yml", GradientMenuConfigLoader.shopDefaults());
        confirmMenu = loader.load("menus/gradient-purchase-confirm.yml", GradientMenuConfigLoader.confirmDefaults());
        previewText = plugin.getConfigManager().getConfig().getString("dynamic-gradients.preview-text", "[动态称号]");
    }

    public void openStorage(Player player) {
        openStorage(player, 0);
    }

    public void openShop(Player player) {
        openShop(player, 0);
    }

    private void openStorage(Player player, int page) {
        if (!canOpen(player)) {
            return;
        }
        List<GradientEffect> effects = plugin.getGradientManager().getEffects().stream()
                .filter(GradientEffect::enabled)
                .filter(effect -> plugin.getGradientManager().owns(player.getUniqueId(), effect.id()))
                .toList();
        openList(player, MenuType.STORAGE, storageMenu, effects, page, null);
    }

    private void openShop(Player player, int page) {
        if (!canOpen(player)) {
            return;
        }
        List<GradientEffect> effects = plugin.getGradientManager().getEffects().stream()
                .filter(GradientEffect::enabled)
                .filter(effect -> effect.purchase().enabled())
                .filter(effect -> !shopMenu.hideOwned() || !plugin.getGradientManager().owns(player.getUniqueId(), effect.id()))
                .toList();
        openList(player, MenuType.SHOP, shopMenu, effects, page, null);
    }

    private void openConfirm(Player player, String effectId, int shopPage) {
        GradientEffect effect = plugin.getGradientManager().getEffect(effectId);
        if (!canOpen(player) || effect == null || !effect.enabled() || !effect.purchase().enabled()) {
            openShop(player, shopPage);
            return;
        }
        openList(player, MenuType.CONFIRM, confirmMenu, List.of(effect), 0, effect.id(), shopPage);
    }

    private boolean canOpen(Player player) {
        if (!plugin.getGradientManager().isEnabled()) {
            plugin.getMessageManager().send(player, "gradient-disabled");
            return false;
        }
        if (plugin.getTagManager().getPlayerData(player.getUniqueId()) == null) {
            plugin.getMessageManager().send(player, "gradient-data-loading");
            return false;
        }
        return true;
    }

    private void openList(Player player, MenuType type, GradientMenuDefinition menu,
                          List<GradientEffect> effects, int requestedPage, String effectId) {
        openList(player, type, menu, effects, requestedPage, effectId, requestedPage);
    }

    private void openList(Player player, MenuType type, GradientMenuDefinition menu,
                          List<GradientEffect> effects, int requestedPage, String effectId, int returnPage) {
        int perPage = Math.max(1, menu.effectSlots().size());
        int totalPages = Math.max(1, (effects.size() + perPage - 1) / perPage);
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));
        Map<String, String> common = commonPlaceholders(player, page, totalPages);
        if (type == MenuType.CONFIRM && !effects.isEmpty()) {
            GradientEffect effect = effects.get(0);
            common = merge(common, itemFactory.effectPlaceholders(
                    effect, previewText, plugin.getGradientManager().getExpireTime(player.getUniqueId(), effect.id())));
        }
        String title = apply(menu.title(), common);
        Inventory inventory = Bukkit.createInventory(null, menu.size(), TextComponentParser.parse(title));

        renderLayout(inventory, menu, common);
        int start = page * perPage;
        String selectedId = plugin.getGradientManager().getSelectedEffect(player.getUniqueId());
        for (int index = 0; index < menu.effectSlots().size(); index++) {
            int effectIndex = start + index;
            if (effectIndex >= effects.size()) {
                break;
            }
            GradientEffect effect = effects.get(effectIndex);
            boolean selected = effect.id().equals(selectedId);
            GradientMenuItem template = selected ? menu.selectedEffectItem() : menu.unselectedEffectItem();
            Map<String, String> placeholders = merge(common, itemFactory.effectPlaceholders(
                    effect, previewText, plugin.getGradientManager().getExpireTime(player.getUniqueId(), effect.id())));
            inventory.setItem(menu.effectSlots().get(index), itemFactory.createEffect(template, effect, placeholders, selected));
        }

        UUID uuid = player.getUniqueId();
        reopening.add(uuid);
        try {
            sessions.put(uuid, new MenuSession(type, page, returnPage, effectId));
            player.openInventory(inventory);
        } finally {
            reopening.remove(uuid);
        }
    }

    private void renderLayout(Inventory inventory, GradientMenuDefinition menu, Map<String, String> placeholders) {
        for (int row = 0; row < menu.layout().size() && row < menu.size() / 9; row++) {
            String rowText = menu.layout().get(row);
            for (int column = 0; column < rowText.length() && column < 9; column++) {
                GradientMenuItem item = menu.items().get(rowText.charAt(column));
                if (item != null) {
                    inventory.setItem(row * 9 + column, itemFactory.createStatic(item, placeholders));
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        MenuSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getInventory()) {
            return;
        }

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        String action = itemFactory.getAction(item);
        String effectId = itemFactory.getEffectId(item);
        if (action == null || action.isBlank()) {
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        execute(player, session, action, effectId);
    }

    private void execute(Player player, MenuSession session, String action, String effectId) {
        switch (action.toLowerCase(Locale.ROOT)) {
            case "select-effect", "toggle-effect" -> handleEffectClick(player, session, effectId);
            case "clear" -> plugin.getGradientManager().clearSelection(player.getUniqueId())
                    .whenComplete((ignored, error) -> runSync(() -> {
                        if (error == null) {
                            plugin.getMessageManager().send(player, "gradient-cleared");
                        } else {
                            plugin.getMessageManager().send(player, "gradient-operation-failed");
                        }
                        openStorage(player, session.page());
                    }));
            case "shop" -> openShop(player, 0);
            case "back" -> openStorage(player, 0);
            case "prev-page" -> reopenPage(player, session, session.page() - 1);
            case "next-page" -> reopenPage(player, session, session.page() + 1);
            case "confirm-buy" -> confirmPurchase(player, session);
            case "cancel" -> openShop(player, session.returnPage());
            case "close" -> player.closeInventory();
            default -> executeExtendedAction(player, action);
        }
    }

    private void handleEffectClick(Player player, MenuSession session, String effectId) {
        if (effectId == null) {
            return;
        }
        if (session.type() == MenuType.SHOP) {
            openConfirm(player, effectId, session.page());
            return;
        }
        if (session.type() != MenuType.STORAGE) {
            return;
        }
        if (effectId.equals(plugin.getGradientManager().getSelectedEffect(player.getUniqueId()))) {
            execute(player, session, "clear", effectId);
            return;
        }
        plugin.getGradientManager().selectEffect(player.getUniqueId(), effectId)
                .whenComplete((selected, error) -> runSync(() -> {
                    if (error == null && Boolean.TRUE.equals(selected)) {
                        GradientEffect effect = plugin.getGradientManager().getEffect(effectId);
                        plugin.getMessageManager().send(player, "gradient-selected", MessageManager.of(
                                "effect", effect != null ? effect.displayName() : effectId));
                    } else {
                        plugin.getMessageManager().send(player, "gradient-operation-failed");
                    }
                    openStorage(player, session.page());
                }));
    }

    private void confirmPurchase(Player player, MenuSession session) {
        GradientEffect effect = plugin.getGradientManager().getEffect(session.effectId());
        if (effect == null || !effect.enabled() || !effect.purchase().enabled()) {
            plugin.getMessageManager().send(player, "gradient-not-for-sale");
            openShop(player, session.returnPage());
            return;
        }
        purchaseService.purchase(player, effect, result -> {
            if (!player.isOnline()) {
                return;
            }
            if (result == GradientPurchaseResult.SUCCESS || result == GradientPurchaseResult.ALREADY_OWNED) {
                openStorage(player, 0);
            } else {
                openShop(player, session.returnPage());
            }
        });
    }

    private void reopenPage(Player player, MenuSession session, int page) {
        if (session.type() == MenuType.STORAGE) {
            openStorage(player, page);
        } else if (session.type() == MenuType.SHOP) {
            openShop(player, page);
        }
    }

    private void executeExtendedAction(Player player, String action) {
        String normalized = action.trim();
        if (normalized.startsWith("command:")) {
            player.performCommand(normalized.substring("command:".length()).trim().replace("%player%", player.getName()));
        } else if (normalized.startsWith("console:")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    normalized.substring("console:".length()).trim().replace("%player%", player.getName()));
        } else if (normalized.startsWith("message:")) {
            player.sendMessage(TextComponentParser.parse(normalized.substring("message:".length()).trim()));
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!reopening.contains(uuid)) {
            sessions.remove(uuid);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
        reopening.remove(event.getPlayer().getUniqueId());
    }

    private Map<String, String> commonPlaceholders(Player player, int page, int totalPages) {
        GradientEffect selected = plugin.getGradientManager().getEffect(
                plugin.getGradientManager().getSelectedEffect(player.getUniqueId()));
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("page", String.valueOf(page + 1));
        placeholders.put("total_pages", String.valueOf(totalPages));
        placeholders.put("selected_effect", selected == null
                ? plugin.getMessageManager().getRaw("gradient-none-selected", "未选择")
                : LegacyColorConverter.convertToMiniMessage(selected.displayName()));
        return placeholders;
    }

    private Map<String, String> merge(Map<String, String> first, Map<String, String> second) {
        Map<String, String> result = new LinkedHashMap<>(first);
        result.putAll(second);
        return result;
    }

    private String apply(String text, Map<String, String> placeholders) {
        String result = text == null ? "" : text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return result;
    }

    private void runSync(Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private enum MenuType {
        STORAGE,
        SHOP,
        CONFIRM
    }

    private record MenuSession(MenuType type, int page, int returnPage, String effectId) {
    }
}
