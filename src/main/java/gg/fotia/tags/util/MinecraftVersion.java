package gg.fotia.tags.util;

public final class MinecraftVersion {

    private MinecraftVersion() {
    }

    public static boolean isAtLeast(String bukkitVersion, int major, int minor, int patch) {
        String release = bukkitVersion == null ? "" : bukkitVersion.split("-", 2)[0];
        String[] parts = release.split("\\.");
        int actualMajor = parts.length > 0 ? parsePart(parts[0]) : 0;
        int actualMinor = parts.length > 1 ? parsePart(parts[1]) : 0;
        int actualPatch = parts.length > 2 ? parsePart(parts[2]) : 0;

        if (actualMajor != major) {
            return actualMajor > major;
        }
        if (actualMinor != minor) {
            return actualMinor > minor;
        }
        return actualPatch >= patch;
    }

    private static int parsePart(String part) {
        int end = 0;
        while (end < part.length() && Character.isDigit(part.charAt(end))) {
            end++;
        }
        return end == 0 ? 0 : Integer.parseInt(part.substring(0, end));
    }
}
