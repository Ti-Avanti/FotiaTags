package gg.fotia.tags.hook;

import gg.fotia.tags.FotiaTags;
import gg.fotia.basictool.bridge.profile.TitleMenuService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;

/** 个人主页复用 FotiaTags 自身的称号物品构建与原生菜单。 */
public final class ProfileTitleMenu implements TitleMenuService {
    private final FotiaTags plugin;
    /** 注册可选 BasicTool 菜单服务，仅在依赖存在时调用。 */
    public static void register(FotiaTags plugin){
        Bukkit.getServicesManager().register(TitleMenuService.class,new ProfileTitleMenu(plugin),plugin,ServicePriority.Normal);
    }
    private ProfileTitleMenu(FotiaTags plugin){this.plugin=plugin;}
    /** 返回当前实际佩戴称号的物品副本。 */
    public ItemStack equippedItem(Player player){return plugin.getMenuManager().equippedItem(player);}
    /** 玩家称号缓存是否已加载。 */
    public boolean ready(Player player){return plugin.getTagManager().getPlayerData(player.getUniqueId())!=null;}
    /** 打开称号菜单。 */
    public void open(Player player){plugin.getMenuManager().openTagSelectMenu(player);}
}
