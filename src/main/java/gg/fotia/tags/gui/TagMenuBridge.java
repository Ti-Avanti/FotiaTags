package gg.fotia.tags.gui;

import org.bukkit.entity.Player;

/** 可选显示层；没有 FutureUI 时不加载其 API 类。 */
public interface TagMenuBridge extends AutoCloseable {
    TagMenuBridge NONE = new TagMenuBridge() {
        public boolean open(Player player, String view, int page, String target) { return false; }
        public boolean refresh(Player player) { return false; }
        public void close() {}
    };
    boolean open(Player player, String view, int page, String target);
    boolean refresh(Player player);
    @Override void close();
}
