package gg.fotia.tags.gui.futureui;

import gg.fotia.tags.gradient.GradientPurchaseResult;
import gg.fotia.futureui.api.ActionResult;
import gg.fotia.futureui.api.MenuContext;
import gg.fotia.futureui.config.Node;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** 校验当前页面和最新拥有状态，再调用原异步业务服务。 */
final class TagMenuActions {
    private final FutureTagMenu owner;
    TagMenuActions(FutureTagMenu owner) { this.owner = owner; }
    CompletionStage<ActionResult> execute(MenuContext ctx, Node options) {
        FutureTagMenu.requireMain();
        Player p = ctx.player(); TagMenuSession s = owner.current(ctx);
        if (s == null) return done();
        String op = options.text("operation", ""), id = argument(ctx, options.text("id", ""));
        if (op.equals("closed")) { owner.closed(p, s); return done(); }
        if (op.equals("close")) { owner.dismiss(p); return done(); }
        if (op.equals("opened")) return done();
        if (!owner.allowed(p, s.view) || owner.plugin.getTagManager().getPlayerData(p.getUniqueId()) == null) return done();
        if (s.busy) return done();
        switch (op) {
            case "navigate" -> owner.navigate(p, id, "", 0);
            case "previous" -> { s.page = Math.max(0, s.page - 1); owner.refresh(p); }
            case "next" -> { s.page++; owner.refresh(p); }
            case "layout" -> owner.layout(p, s);
            case "filter" -> {
                if (id.equals("cycle")) id = switch (s.filter) { case "all" -> "normal"; case "normal" -> "custom"; default -> "all"; };
                if (s.view.equals("tag-select") && Set.of("all", "normal", "custom").contains(id)) {
                    s.filter = id; s.page = 0; owner.refresh(p);
                }
            }
            case "select" -> select(p, s, id);
            case "clear" -> clear(p, s);
            case "input" -> { if (s.view.equals("custom-tag")) owner.input(p, s, id); }
            case "submit" -> {
                if (s.view.equals("custom-tag") && !s.inputPart.isEmpty()) {
                    String input = Objects.toString(ctx.variables().get("input.value"), "").trim();
                    if (owner.plugin.getCustomTagManager().applyMenuText(p, s.inputPart, input)) owner.returnFromInput(p, s);
                }
            }
            case "cancel-input" -> owner.returnFromInput(p, s);
            case "confirm" -> confirm(p, s);
            case "equip" -> {
                if (s.view.equals("custom-tag-detail") && owner.plugin.getTagManager().hasCustomTag(p.getUniqueId(), s.target))
                    selectTag(p, s, s.target);
            }
            case "delete" -> {
                if (s.view.equals("custom-tag-detail")) owner.navigate(p, "custom-tag-delete", s.target, s.returnPage);
            }
            case "back" -> back(p, s);
            case "footer" -> { if (Set.of("tag-select", "gradient-storage").contains(s.view)) clear(p, s); else back(p, s); }
            default -> { }
        }
        return done();
    }
    private void select(Player p, TagMenuSession s, String id) {
        if (!owner.data.ids(p, s).contains(id)) return;
        switch (s.view) {
            case "tag-select" -> {
                if (owner.plugin.getTagManager().isCustomTagId(id)) owner.navigate(p, "custom-tag-detail", id, s.page);
                else selectTag(p, s, id);
            }
            case "custom-tag-icons" -> {
                if (owner.plugin.getCustomTagManager().applyMenuIcon(p, id)) owner.navigate(p, "custom-tag", "", 0);
            }
            case "gradient-storage" -> {
                var manager = owner.plugin.getGradientManager();
                if (!manager.owns(p.getUniqueId(), id)) return;
                boolean clear = id.equals(manager.getSelectedEffect(p.getUniqueId()));
                commit(p, s, clear ? manager.clearSelection(p.getUniqueId()) : manager.selectEffect(p.getUniqueId(), id));
            }
            case "gradient-shop" -> owner.navigate(p, "gradient-purchase-confirm", id, s.page);
            default -> { }
        }
    }
    private void selectTag(Player p, TagMenuSession s, String id) {
        var manager = owner.plugin.getTagManager(); var data = manager.getPlayerData(p.getUniqueId());
        if (data == null) return;
        boolean custom = manager.isCustomTagId(id);
        if (custom && (!owner.plugin.getCustomTagManager().isEnabled() || !manager.hasCustomTag(p.getUniqueId(), id))) return;
        var tag = manager.getTag(id);
        if (!custom && (tag == null || !data.hasTag(id) || (tag.hasPermission() && !p.hasPermission(tag.getPermission())))) return;
        commit(p, s, manager.setCurrentTagAsync(p.getUniqueId(), id.equals(data.getCurrentTag()) ? null : id));
    }
    private void clear(Player p, TagMenuSession s) {
        if (s.view.equals("tag-select")) commit(p, s, owner.plugin.getTagManager().setCurrentTagAsync(p.getUniqueId(), null));
        else if (s.view.equals("gradient-storage")) commit(p, s, owner.plugin.getGradientManager().clearSelection(p.getUniqueId()));
    }
    private void confirm(Player p, TagMenuSession s) {
        if (s.view.equals("custom-tag")) {
            int before = owner.plugin.getTagManager().getCustomTags(p.getUniqueId()).size(); s.busy = true;
            owner.plugin.getCustomTagManager().confirmFromMenu(p, () -> owner.complete(p, s, () -> {
                if (owner.plugin.getTagManager().getCustomTags(p.getUniqueId()).size() > before)
                    owner.navigate(p, "tag-select", "", 0);
                else owner.refresh(p);
            }));
        } else if (s.view.equals("custom-tag-delete")) {
            if (!owner.plugin.getTagManager().hasCustomTag(p.getUniqueId(), s.target)) return;
            s.busy = true;
            owner.plugin.getCustomTagManager().deleteOwnedCustomTag(p, s.target,
                    () -> owner.complete(p, s, () -> owner.navigate(p, "tag-select", "", s.returnPage)),
                    () -> owner.complete(p, s, () -> owner.refresh(p)));
        } else if (s.view.equals("gradient-purchase-confirm")) {
            s.busy = true;
            boolean started = owner.plugin.getGradientMenuManager().buyFromMenu(p, s.target,
                    result -> owner.complete(p, s, () -> {
                        if (result == GradientPurchaseResult.SUCCESS || result == GradientPurchaseResult.ALREADY_OWNED)
                            owner.navigate(p, "gradient-storage", "", 0);
                        else owner.refresh(p);
                    }));
            if (!started) { s.busy = false; owner.refresh(p); }
        }
    }
    private void commit(Player p, TagMenuSession s, CompletionStage<?> operation) {
        s.busy = true; owner.refresh(p);
        operation.whenComplete((result, error) -> {
            if (!owner.plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(owner.plugin, () -> owner.complete(p, s, () -> {
                if (error != null || Boolean.FALSE.equals(result)) owner.plugin.getMessageManager().send(p, "gradient-operation-failed");
                owner.refresh(p);
            }));
        });
    }
    private void back(Player p, TagMenuSession s) {
        switch (s.view) {
            case "custom-tag-icons" -> owner.navigate(p, "custom-tag", "", 0);
            case "custom-tag-delete" -> owner.navigate(p, "custom-tag-detail", s.target, s.returnPage);
            case "gradient-purchase-confirm" -> owner.navigate(p, "gradient-shop", "", s.returnPage);
            default -> owner.navigate(p, "tag-select", "", s.returnPage);
        }
    }
    private static CompletionStage<ActionResult> done() { return CompletableFuture.completedFuture(ActionResult.ok()); }
    private static String argument(MenuContext ctx, String value) {
        return value.startsWith("{") && value.endsWith("}")
                ? Objects.toString(ctx.variables().get(value.substring(1, value.length() - 1)), "") : value;
    }
}
