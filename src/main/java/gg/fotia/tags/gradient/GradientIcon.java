package gg.fotia.tags.gradient;

public record GradientIcon(
        String material,
        String itemModel,
        String tooltipStyle,
        int customModelData,
        boolean glow
) {

    public GradientIcon {
        material = material == null || material.isBlank() ? "NAME_TAG" : material;
        itemModel = itemModel == null ? "" : itemModel;
        tooltipStyle = tooltipStyle == null ? "" : tooltipStyle;
        customModelData = Math.max(0, customModelData);
    }

    public static GradientIcon defaults() {
        return new GradientIcon("NAME_TAG", "", "", 0, false);
    }
}
