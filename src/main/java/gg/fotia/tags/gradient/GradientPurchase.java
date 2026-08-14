package gg.fotia.tags.gradient;

public record GradientPurchase(boolean enabled, String provider, double price, long durationMillis) {

    public GradientPurchase {
        provider = provider == null ? "" : provider.toLowerCase();
        price = Math.max(0.0, price);
        durationMillis = durationMillis <= 0 ? -1L : durationMillis;
    }

    public static GradientPurchase disabled() {
        return new GradientPurchase(false, "", 0.0, -1L);
    }
}
