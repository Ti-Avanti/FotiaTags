package gg.fotia.tags.tag;

import org.bukkit.Material;

public class Tag {

    private final String id;
    private String displayName;
    private String prefix;
    private String suffix;
    private String prefix2;
    private String suffix2;
    private String permission;

    // GUI物品配置
    private Material material;
    private String itemModel;
    private int customModelData;

    public Tag(String id) {
        this.id = id;
        this.material = Material.PAPER;
        this.customModelData = 0;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName != null ? displayName : id;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPrefix() {
        return prefix != null ? prefix : "";
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getSuffix() {
        return suffix != null ? suffix : "";
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    public String getPrefix2() {
        return prefix2 != null ? prefix2 : "";
    }

    public void setPrefix2(String prefix2) {
        this.prefix2 = prefix2;
    }

    public String getSuffix2() {
        return suffix2 != null ? suffix2 : "";
    }

    public void setSuffix2(String suffix2) {
        this.suffix2 = suffix2;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public boolean hasPermission() {
        return permission != null && !permission.isEmpty();
    }

    public Material getMaterial() {
        return material;
    }

    public void setMaterial(Material material) {
        this.material = material != null ? material : Material.PAPER;
    }

    public String getItemModel() {
        return itemModel;
    }

    public void setItemModel(String itemModel) {
        this.itemModel = itemModel;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public void setCustomModelData(int customModelData) {
        this.customModelData = customModelData;
    }
}
