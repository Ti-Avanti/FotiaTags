package gg.fotia.tags.util;

import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** 使用稳定的注册表 API 兼容 Sound 从枚举类到接口的变化。 */
public final class SoundResolver {

    private static final Map<String, Sound> LEGACY_NAMES = legacyNames();

    private SoundResolver() {
    }

    /**
     * 解析旧枚举名称或命名空间音效 ID，不调用版本相关的 Sound 静态方法。
     *
     * @param name 配置中的音效名称
     * @return 对应音效，名称无效时返回 null
     */
    public static Sound resolve(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.trim();
        Sound legacy = LEGACY_NAMES.get(normalized.toUpperCase(Locale.ROOT));
        if (legacy != null) {
            return legacy;
        }
        NamespacedKey key = NamespacedKey.fromString(normalized.toLowerCase(Locale.ROOT));
        return key == null ? null : Registry.SOUNDS.get(key);
    }

    private static Map<String, Sound> legacyNames() {
        Map<String, Sound> names = new HashMap<>();
        for (Sound sound : Registry.SOUNDS) {
            // 通过两个版本都实现的 Keyed 接口读取键，避免调用 Sound 接口方法。
            NamespacedKey key = ((Keyed) sound).getKey();
            if (key.getNamespace().equals(NamespacedKey.MINECRAFT)) {
                names.put(key.getKey().replace('.', '_').toUpperCase(Locale.ROOT), sound);
            }
        }
        return Map.copyOf(names);
    }
}
