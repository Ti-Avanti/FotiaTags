package gg.fotia.tags.gradient;

public final class GradientAnimation {

    private GradientAnimation() {
    }

    public static double phaseAt(long tick, int durationTicks, boolean reversed) {
        int duration = Math.max(4, durationTicks);
        double progress = Math.floorMod(tick, duration) / (double) duration;
        double phase = progress <= 0.5
                ? -1.0 + progress * 4.0
                : 3.0 - progress * 4.0;
        return reversed ? -phase : phase;
    }
}
