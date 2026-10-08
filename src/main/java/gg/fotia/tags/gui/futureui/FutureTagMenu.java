package gg.fotia.tags.gui.futureui;

import gg.fotia.tags.FotiaTags;
import gg.fotia.tags.gui.TagMenuBridge;
import gg.fotia.tags.gui.TagMenuSettings;
import gg.fotia.futureui.api.FutureUIService;
import gg.fotia.futureui.api.MenuContext;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.persistence.PersistentDataType;

/** 会话隔离和显示路由；业务操作通过 FotiaTags 原服务完成。 */
public final class FutureTagMenu implements TagMenuBridge, Listener {
    static final String TOKEN = "fotiatags.session", EXTENSION = "fotiatags:menu";
    final FotiaTags plugin;
    final TagMenuSettings settings;
    final TagMenuData data;
    private final Map<UUID,TagMenuSession> sessions = new HashMap<>();
    private final List<AutoCloseable> registrations = new ArrayList<>();
    private final NamespacedKey layoutKey;
    private FutureUIService service;
    private boolean closed;

    public FutureTagMenu(FotiaTags plugin, TagMenuSettings settings) {
        this.plugin = plugin; this.settings = settings; data = new TagMenuData(plugin, settings);
        layoutKey = new NamespacedKey(plugin, "futureui-layout");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    private boolean connect() {
        if (closed || !Bukkit.getPluginManager().isPluginEnabled("FutureUI")) return false;
        var current = Bukkit.getServicesManager().load(FutureUIService.class);
        if (current == null) return false;
        if (service == current) return true;
        release(); service = current;
        registrations.add(service.registerAction(plugin, EXTENSION, new TagMenuActions(this)::execute));
        registrations.add(service.registerData(plugin, EXTENSION, (ctx, node) -> {
            requireMain(); var session = current(ctx);
            return CompletableFuture.completedFuture(session == null || !allowed(ctx.player(), session.view)
                    ? List.of() : data.rows(ctx.player(), session, node.text("view", "page")));
        }));
        return true;
    }
    @Override public boolean open(Player player, String view, int page, String target) {
        requireMain();
        if (!settings.enabledViews().contains(view)) { dismiss(player); return false; }
        if (!connect()) return false;
        TagMenuSession s = sessions.get(player.getUniqueId());
        boolean small = s != null ? s.compact : compact(player);
        String menu = settings.profile(small).menu();
        if (!service.canRender(player, menu)) return false;
        if (!allowed(player, view)) { plugin.getMessageManager().send(player, "no-permission"); return true; }
        if (plugin.getTagManager().getPlayerData(player.getUniqueId()) == null) {
            plugin.getMessageManager().send(player, "gradient-data-loading"); return true;
        }
        if (s != null && s.busy) return true;
        if (s == null) s = new TagMenuSession(small);
        boolean reuse = visible(player, s) && menu.equals(s.menu);
        s.view = view; s.target = target == null ? "" : target; s.inputPart = "";
        boolean detail = Set.of("custom-tag-detail", "custom-tag-delete", "gradient-purchase-confirm").contains(view);
        s.returnPage = Math.max(0, page); s.page = detail ? 0 : Math.max(0, page);
        s.menu = menu; sessions.put(player.getUniqueId(), s);
        if (reuse) service.refresh(player); else if (!service.open(player, menu, arguments(s))) sessions.remove(player.getUniqueId(), s);
        return true;
    }
    boolean allowed(Player p, String view) {
        if (view.startsWith("gradient-")) return p.hasPermission("fotiatags.effects") && plugin.getGradientManager().isEnabled();
        if (view.equals("custom-tag") || view.equals("custom-tag-icons"))
            return p.hasPermission("fotiatags.custom") && plugin.getCustomTagManager().isEnabled();
        return p.hasPermission("fotiatags.use") && (!view.startsWith("custom-") || plugin.getCustomTagManager().isEnabled());
    }
    void navigate(Player p, String view, String target, int page) {
        if (!allowed(p, view)) { plugin.getMessageManager().send(p, "no-permission"); return; }
        switch (view) {
            case "tag-select" -> plugin.getMenuManager().openTagSelectMenu(p, page);
            case "custom-tag" -> plugin.getCustomTagManager().openCustomMenu(p);
            case "custom-tag-icons" -> plugin.getCustomTagManager().openIconMenu(p);
            case "custom-tag-detail" -> plugin.getMenuManager().openCustomTagDetailMenu(p, target, page);
            case "custom-tag-delete" -> plugin.getMenuManager().openCustomTagDeleteMenu(p, target, page);
            case "gradient-storage" -> plugin.getGradientMenuManager().openStorage(p, page);
            case "gradient-shop" -> plugin.getGradientMenuManager().openShop(p, page);
            case "gradient-purchase-confirm" -> plugin.getGradientMenuManager().openConfirm(p, target, page);
            default -> throw new IllegalArgumentException("Unknown tag menu " + view);
        }
    }
    void input(Player p, TagMenuSession s, String part) {
        if (!Set.of("prefix", "suffix").contains(part) || !allowed(p, "custom-tag")) return;
        if (!service.canRender(p, settings.inputMenu())) return;
        var draft = plugin.getCustomTagManager().menuDraft(p);
        if (draft.locked() || (part.equals("prefix") ? !draft.prefixEditable() : !draft.suffixEditable())) return;
        s.inputPart = part; s.menu = settings.inputMenu();
        Map<String,Object> args = new LinkedHashMap<>(arguments(s));
        args.put("fotiatags.input", part.equals("prefix") ? draft.prefix() : draft.suffix());
        args.put("fotiatags.part", part);
        if (!service.open(p, s.menu, args)) { s.menu = settings.profile(s.compact).menu(); refresh(p); }
    }
    void returnFromInput(Player p, TagMenuSession s) {
        s.inputPart = ""; s.menu = settings.profile(s.compact).menu();
        service.open(p, s.menu, arguments(s));
    }
    void layout(Player p, TagMenuSession s) {
        s.compact = !s.compact;
        String next = settings.profile(s.compact).menu();
        if (!service.canRender(p, next)) { s.compact = !s.compact; return; }
        s.page = 0; s.menu = next;
        if (service.open(p, next, arguments(s))) p.getPersistentDataContainer().set(layoutKey, PersistentDataType.STRING, s.compact ? "compact" : "standard");
    }
    TagMenuSession current(MenuContext ctx) {
        var s = sessions.get(ctx.player().getUniqueId());
        return s != null && s.token.toString().equals(Objects.toString(ctx.variables().get(TOKEN), ""))
                && Objects.equals(s.menu, ctx.session().menu.id()) ? s : null;
    }
    boolean active(Player p, TagMenuSession s) { return p.isOnline() && sessions.get(p.getUniqueId()) == s && visible(p, s); }
    void complete(Player p, TagMenuSession s, Runnable next) {
        s.busy = false;
        if (active(p, s)) next.run();
    }
    private boolean visible(Player p, TagMenuSession s) { return service != null && s.menu != null && service.isOpen(p, s.menu, arguments(s)); }
    @Override public boolean refresh(Player p) {
        var s = sessions.get(p.getUniqueId());
        if (s == null || !visible(p, s)) return false;
        service.refresh(p); return true;
    }
    void dismiss(Player p) { if (service != null && sessions.containsKey(p.getUniqueId())) service.close(p); sessions.remove(p.getUniqueId()); }
    void closed(Player p, TagMenuSession s) { sessions.remove(p.getUniqueId(), s); }
    private boolean compact(Player p) {
        String value = p.getPersistentDataContainer().get(layoutKey, PersistentDataType.STRING);
        return value == null ? settings.compactDefault() : value.equals("compact");
    }
    private Map<String,String> arguments(TagMenuSession s) { return Map.of(TOKEN, s.token.toString()); }
    static void requireMain() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Tag menu requires main thread"); }
    @EventHandler public void quit(PlayerQuitEvent e) { sessions.remove(e.getPlayer().getUniqueId()); }
    @EventHandler public void disabled(PluginDisableEvent e) { if (e.getPlugin().getName().equals("FutureUI")) release(); }
    private void release() {
        for (UUID id : List.copyOf(sessions.keySet())) { Player p = Bukkit.getPlayer(id); if (p != null) dismiss(p); }
        sessions.clear();
        for (AutoCloseable registration : registrations) try { registration.close(); } catch (Exception e) { plugin.getLogger().warning(e.getMessage()); }
        registrations.clear(); service = null;
    }
    @Override public void close() { closed = true; release(); HandlerList.unregisterAll(this); }
}
