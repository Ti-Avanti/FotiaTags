package gg.fotia.tags.tag;

public class CustomTag {

    private String id;
    private String prefix;
    private String suffix;
    private String iconId;
    private String particleEffect;
    private String paymentProvider;
    private double purchasePrice;

    public CustomTag(String prefix, String suffix, String iconId) {
        this("", prefix, suffix, iconId, "", "", 0.0);
    }

    public CustomTag(String id, String prefix, String suffix, String iconId, String particleEffect, String paymentProvider, double purchasePrice) {
        this.id = id;
        this.prefix = prefix;
        this.suffix = suffix;
        this.iconId = iconId;
        this.particleEffect = particleEffect;
        this.paymentProvider = paymentProvider;
        this.purchasePrice = purchasePrice;
    }

    public String getId() {
        return id != null ? id : "";
    }

    public void setId(String id) {
        this.id = id;
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

    public String getIconId() {
        return iconId != null ? iconId : "";
    }

    public void setIconId(String iconId) {
        this.iconId = iconId;
    }

    public String getParticleEffect() {
        return particleEffect != null ? particleEffect : "";
    }

    public void setParticleEffect(String particleEffect) {
        this.particleEffect = particleEffect;
    }

    public String getPaymentProvider() {
        return paymentProvider != null ? paymentProvider : "";
    }

    public void setPaymentProvider(String paymentProvider) {
        this.paymentProvider = paymentProvider;
    }

    public double getPurchasePrice() {
        return Math.max(0.0, purchasePrice);
    }

    public void setPurchasePrice(double purchasePrice) {
        this.purchasePrice = purchasePrice;
    }

    public boolean isComplete() {
        return (!getPrefix().isEmpty() || !getSuffix().isEmpty()) && !getIconId().isEmpty();
    }
}
