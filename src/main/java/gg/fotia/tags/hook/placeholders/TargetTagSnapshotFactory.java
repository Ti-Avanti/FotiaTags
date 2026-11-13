package gg.fotia.tags.hook.placeholders;

import gg.fotia.basictool.bridge.PaperNickBridge;
import gg.fotia.tags.FotiaTags;
import gg.fotia.tags.tag.CustomTag;
import gg.fotia.tags.tag.PlayerTagData;
import gg.fotia.tags.tag.Tag;
import gg.fotia.tags.tag.TagManager;

/** 在主线程读取在线数据及配置，发布不含可变 Bukkit/玩家数据引用的快照。 */
final class TargetTagSnapshotFactory {
    private final FotiaTags plugin;

    TargetTagSnapshotFactory(FotiaTags plugin) {
        this.plugin = plugin;
    }

    TagPlaceholderSnapshot capture(PlayerTagData data, boolean online) {
        var tags = plugin.getTagManager();
        String defaultId = tags.getDefaultTag();
        var fallback = defaultId == null || defaultId.isBlank() || defaultId.equalsIgnoreCase("disabled")
                ? TagPlaceholderSnapshot.Values.EMPTY : normal(defaultId);
        String id = data.getCurrentTag();
        CustomTag custom = selectedCustom(data, id);
        var selected = fallback;
        long expires = -1;
        if (custom != null) {
            var menus = plugin.getCustomTagManager();
            String prefix = menus.getDisplayPrefix(custom);
            String suffix = menus.getDisplaySuffix(custom);
            String name = prefix + suffix;
            selected = new TagPlaceholderSnapshot.Values(custom.getId(),
                    name.isBlank() ? menus.getDisplayName() : name,
                    prefix, suffix, prefix, suffix, true, custom.getPrefix(), custom.getSuffix());
        } else if (tags.isCustomTagId(id)) {
            selected = TagPlaceholderSnapshot.Values.EMPTY;
        } else if (id != null && !id.isEmpty() && data.hasTag(id)) {
            selected = normal(id);
            expires = data.getTagExpireTime(id);
        }
        long now = System.currentTimeMillis();
        String effectId = data.getGradientData().selectedEffect(now);
        var effect = plugin.getGradientManager().isEnabled() ? plugin.getGradientManager().getEffect(effectId) : null;
        var config = plugin.getConfigManager().getConfig();
        return new TagPlaceholderSnapshot(data.getUuid(), online,
                PaperNickBridge.active("FotiaTags", data.getUuid()), selected, fallback, expires,
                data.getValidTags().stream().map(data::getTagExpireTime).toList(), effect,
                data.getGradientData().expireTime(effectId),
                config.getBoolean("custom-tags.display-wrapper.enabled", true),
                config.getString("custom-tags.display-wrapper.left", "["),
                config.getString("custom-tags.display-wrapper.right", "]"));
    }

    private CustomTag selectedCustom(PlayerTagData data, String id) {
        if (!plugin.getCustomTagManager().isEnabled()) return null;
        CustomTag custom = TagManager.CUSTOM_TAG_ID.equals(id) ? data.getCustomTag() : data.getCustomTag(id);
        return custom != null && custom.isComplete() ? custom : null;
    }

    private TagPlaceholderSnapshot.Values normal(String id) {
        Tag tag = plugin.getTagManager().getTag(id);
        if (tag == null) return new TagPlaceholderSnapshot.Values(id, "", "", "", "", "", false, "", "");
        return new TagPlaceholderSnapshot.Values(id, tag.getDisplayName(), tag.getPrefix(), tag.getSuffix(),
                tag.getPrefix2(), tag.getSuffix2(), false, "", "");
    }
}
