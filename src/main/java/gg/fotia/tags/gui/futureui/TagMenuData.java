package gg.fotia.tags.gui.futureui;

import gg.fotia.tags.FotiaTags;
import gg.fotia.tags.gui.TagMenuSettings;
import gg.fotia.tags.gradient.GradientFrameRenderer;
import gg.fotia.tags.gradient.GradientTarget;
import gg.fotia.tags.hook.PaymentManager;
import gg.fotia.futureui.config.Node;
import java.util.*;
import org.bukkit.entity.Player;

/** 从原内存数据生成显示快照，只为当前页渲染动态文字。 */
final class TagMenuData {
    private final FotiaTags plugin;
    private final TagMenuSettings settings;
    TagMenuData(FotiaTags plugin, TagMenuSettings settings) { this.plugin = plugin; this.settings = settings; }

    List<String> ids(Player p, TagMenuSession s) {
        var data = plugin.getTagManager().getPlayerData(p.getUniqueId());
        if (data == null) return List.of();
        return switch (s.view) {
            case "tag-select" -> {
                List<String> ids = new ArrayList<>();
                if (!s.filter.equals("custom")) for (String id : data.getValidTags()) {
                    var tag = plugin.getTagManager().getTag(id);
                    if (tag != null && (!tag.hasPermission() || p.hasPermission(tag.getPermission()))) ids.add(id);
                }
                if (!s.filter.equals("normal") && plugin.getCustomTagManager().isEnabled()) ids.addAll(data.getCustomTags().keySet());
                ids.sort(String::compareTo); yield List.copyOf(ids);
            }
            case "custom-tag-icons" -> plugin.getCustomTagManager().menuIcons().stream().map(i -> i.id()).toList();
            case "gradient-storage", "gradient-shop" -> plugin.getGradientMenuManager().menuEffects(p, s.view.equals("gradient-shop"))
                    .stream().map(e -> e.id()).toList();
            default -> List.of();
        };
    }
    List<Node> rows(Player p, TagMenuSession s, String view) {
        List<String> ids = ids(p, s);
        int pageSize = settings.profile(s.compact).pageSize(), pages = Math.max(1, (ids.size() + pageSize - 1) / pageSize);
        s.page = Math.max(0, Math.min(s.page, pages - 1));
        if (view.equals("page")) return List.of(page(p, s, ids.size(), pages));
        if (!view.equals("entries")) return List.of();
        List<Node> rows = new ArrayList<>();
        for (int index = s.page * pageSize; index < Math.min(ids.size(), (s.page + 1) * pageSize); index++)
            rows.add(entry(p, s, ids.get(index)));
        return rows;
    }
    private Node page(Player p, TagMenuSession s, int count, int pages) {
        var tags = plugin.getTagManager(); var custom = plugin.getCustomTagManager();
        String current = plugin.getGradientManager().render(p.getUniqueId(), tags.getCurrentPrefix(p.getUniqueId()), GradientTarget.PREFIX)
                + plugin.getGradientManager().render(p.getUniqueId(), tags.getCurrentSuffix(p.getUniqueId()), GradientTarget.SUFFIX);
        Map<String,Object> v = new LinkedHashMap<>();
        v.put("view", s.view); v.put("filter", s.filter); v.put("busy", s.busy); v.put("compact", s.compact);
        v.put("current", TagMenuText.preview(current, tags.getCurrentTagName(p.getUniqueId()), s.compact ? 165 : 126)); v.put("current-empty", current.isEmpty());
        v.put("count", count); v.put("empty", count == 0); v.put("page", s.page + 1); v.put("pages", pages);
        v.put("previous", !s.busy && s.page > 0); v.put("next", !s.busy && s.page + 1 < pages);
        v.put("can-tags", p.hasPermission("fotiatags.use"));
        v.put("can-create", custom.isEnabled() && p.hasPermission("fotiatags.custom"));
        v.put("can-effects", plugin.getGradientManager().isEnabled() && p.hasPermission("fotiatags.effects"));
        var playerData = tags.getPlayerData(p.getUniqueId());
        v.put("can-clear", !s.busy && (s.view.equals("tag-select") ? playerData != null && playerData.getCurrentTag() != null && !playerData.getCurrentTag().isEmpty()
                : s.view.equals("gradient-storage") && plugin.getGradientManager().getSelectedEffect(p.getUniqueId()) != null));
        v.put("list-view", Set.of("tag-select", "custom-tag-icons", "gradient-storage", "gradient-shop").contains(s.view));
        v.put("detail-view", Set.of("custom-tag-detail", "custom-tag-delete", "gradient-purchase-confirm").contains(s.view));
        v.put("can-delete", !s.busy && custom.isDeleteEnabled());
        v.put("target-exists", false); v.put("target-selected", false); v.put("target-name", ""); v.put("target-preview", "");
        v.put("target-image", settings.fallbackIcon()); v.put("target-prefix", ""); v.put("target-suffix", "");
        v.put("refund", "0"); v.put("refund-currency", "vault");
        var draft = custom.menuDraft(p);
        v.put("draft-prefix", TagMenuText.text(draft.prefix(), s.compact ? 150 : 300));
        v.put("draft-suffix", TagMenuText.text(draft.suffix(), s.compact ? 150 : 300));
        v.put("draft-preview", TagMenuText.text(draft.preview(), s.compact ? 225 : 342));
        v.put("draft-image", icon("icon/" + draft.iconId()));
        v.put("prefix-empty", draft.prefix().isEmpty()); v.put("suffix-empty", draft.suffix().isEmpty());
        v.put("prefix-editable", draft.prefixEditable() && !draft.locked() && !s.busy);
        v.put("suffix-editable", draft.suffixEditable() && !draft.locked() && !s.busy);
        v.put("draft-locked", draft.locked()); v.put("draft-complete", draft.complete());
        var selectedIcon = custom.getIcon(draft.iconId());
        v.put("draft-icon-name", selectedIcon == null ? "" : TagMenuText.text(selectedIcon.name(), 120));
        PaymentManager.PaymentSnapshot price = plugin.getPaymentManager().createSnapshot();
        if (s.view.startsWith("custom-tag-") && !s.view.equals("custom-tag-icons")) {
            var target = tags.getCustomTag(p.getUniqueId(), s.target);
            if (target != null) {
                v.put("target-exists", true); v.put("target-name", TagMenuText.text(custom.getDisplayName(), 210));
                v.put("target-preview", TagMenuText.text(custom.getDisplayPrefix(target) + custom.getDisplaySuffix(target), s.compact ? 220 : 340));
                v.put("target-prefix", TagMenuText.text(custom.getDisplayPrefix(target), 300));
                v.put("target-suffix", TagMenuText.text(custom.getDisplaySuffix(target), 300));
                v.put("target-image", icon("icon/" + target.getIconId()));
                v.put("target-selected", s.target.equals(tags.getCurrentTagId(p.getUniqueId())));
                v.put("refund", TagMenuText.amount(custom.isRefundEnabled() ? target.getPurchasePrice() * custom.getRefundPercent() / 100 : 0));
                v.put("refund-currency", target.getPaymentProvider());
            }
        }
        boolean owned = false;
        v.put("duration-unit", "permanent"); v.put("duration-value", 0);
        if (s.view.equals("gradient-purchase-confirm")) {
            var effect = plugin.getGradientManager().getEffect(s.target);
            if (effect != null && effect.enabled() && effect.purchase().enabled()) {
                price = plugin.getPaymentManager().createSnapshot(effect.purchase().provider(), effect.purchase().price());
                owned = plugin.getGradientManager().owns(p.getUniqueId(), effect.id());
                long millis = effect.purchase().durationMillis();
                if (millis > 0) {
                    long seconds = millis / 1000 + (millis % 1000 == 0 ? 0 : 1);
                    long unit = seconds % 86400 == 0 ? 86400 : seconds % 3600 == 0 ? 3600 : seconds % 60 == 0 ? 60 : 1;
                    v.put("duration-unit", unit == 86400 ? "days" : unit == 3600 ? "hours" : unit == 60 ? "minutes" : "seconds");
                    v.put("duration-value", seconds / unit);
                }
                v.put("target-exists", true); v.put("target-name", TagMenuText.text(effect.displayName(), 210));
                v.put("target-preview", TagMenuText.text(GradientFrameRenderer.render(plugin.getGradientMenuManager().menuPreviewText(), effect, System.currentTimeMillis() / 50), s.compact ? 220 : 340));
                v.put("target-image", icon("effect/" + effect.id()));
            }
        }
        boolean available = plugin.getPaymentManager().isAvailable(price), enough = available && plugin.getPaymentManager().hasEnough(p, price);
        v.put("price", TagMenuText.amount(price.amount())); v.put("currency", price.provider()); v.put("owned", owned);
        v.put("purchase-state", owned ? "owned" : !available ? "unavailable" : !enough ? "funds" : "ready");
        v.put("can-confirm", !s.busy && enough && !owned && (s.view.equals("custom-tag") ? draft.complete() && !draft.locked() : Boolean.TRUE.equals(v.get("target-exists"))));
        return Node.of(v);
    }
    private Node entry(Player p, TagMenuSession s, String id) {
        var tags = plugin.getTagManager(); var custom = plugin.getCustomTagManager();
        String name = "", preview = "", image = settings.fallbackIcon(), kind = "tag", provider = "vault";
        boolean selected = false, owned = true; long expiry = -1; double price = 0;
        if (s.view.equals("tag-select")) {
            var target = tags.getCustomTag(p.getUniqueId(), id);
            if (tags.isCustomTagId(id) && target != null) {
                kind = "custom"; name = custom.getDisplayName(); preview = custom.getDisplayPrefix(target) + custom.getDisplaySuffix(target);
                image = icon("icon/" + target.getIconId());
            } else {
                var tag = tags.getTag(id);
                if (tag != null) { name = tag.getDisplayName(); preview = tag.getPrefix() + tag.getSuffix(); image = icon("tag/" + id); }
                var data = tags.getPlayerData(p.getUniqueId()); expiry = data == null ? 0 : data.getTagExpireTime(id);
            }
            selected = id.equals(tags.getCurrentTagId(p.getUniqueId()));
        } else if (s.view.equals("custom-tag-icons")) {
            var icon = custom.getIcon(id); kind = "icon";
            if (icon != null) { name = icon.name(); preview = name; image = icon("icon/" + id); }
            selected = id.equals(custom.menuDraft(p).iconId());
        } else {
            var effect = plugin.getGradientManager().getEffect(id); kind = "effect";
            if (effect != null) {
                name = effect.displayName(); image = icon("effect/" + id);
                preview = GradientFrameRenderer.render(plugin.getGradientMenuManager().menuPreviewText(), effect, System.currentTimeMillis() / 50);
                provider = effect.purchase().provider(); price = effect.purchase().price();
                expiry = plugin.getGradientManager().getExpireTime(p.getUniqueId(), id);
            }
            selected = id.equals(plugin.getGradientManager().getSelectedEffect(p.getUniqueId()));
            owned = plugin.getGradientManager().owns(p.getUniqueId(), id);
        }
        long hours = Math.max(0, (expiry - System.currentTimeMillis() + 3599999L) / 3600000L);
        Map<String,Object> v = new LinkedHashMap<>();
        v.put("id", id); v.put("kind", kind); v.put("name", TagMenuText.text(name, s.compact ? 195 : 160));
        v.put("preview", TagMenuText.preview(preview.isEmpty() ? name : preview, name, s.compact ? 230 : 166)); v.put("image", image);
        v.put("selected", selected); v.put("owned", owned); v.put("shop", s.view.equals("gradient-shop"));
        v.put("permanent", expiry == -1); v.put("days", hours / 24); v.put("hours", hours % 24);
        v.put("price", TagMenuText.amount(price)); v.put("currency", provider);
        v.put("enabled", !s.busy && !(s.view.equals("gradient-shop") && owned)
                && !(s.view.equals("custom-tag-icons") && custom.menuDraft(p).locked()));
        return Node.of(v);
    }
    private String icon(String key) { return settings.icons().getOrDefault(key, settings.fallbackIcon()); }
}
