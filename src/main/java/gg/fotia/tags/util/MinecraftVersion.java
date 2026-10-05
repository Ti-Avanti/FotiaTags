package gg.fotia.tags.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 比较传统版本号和 26.x 起的年份版本号。 */
public final class MinecraftVersion {

    private static final Pattern RELEASE = Pattern.compile(
            "^(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:[-+].*)?$");

    private MinecraftVersion() {
    }

    /**
     * 比较游戏版本；同时接受 Bukkit 后缀，无法识别的版本不启用新版功能。
     *
     * @param minecraftVersion 游戏版本或 Bukkit 版本字符串
     * @param major 目标主版本或年份
     * @param minor 目标次版本
     * @param patch 目标补丁版本，两段版本号的补丁号为零
     * @return 当前版本是否达到目标版本
     */
    public static boolean isAtLeast(String minecraftVersion, int major, int minor, int patch) {
        if (minecraftVersion == null) {
            return false;
        }
        Matcher release = RELEASE.matcher(minecraftVersion.trim());
        if (!release.matches()) {
            return false;
        }
        int actualMajor = parsePart(release.group(1));
        int actualMinor = parsePart(release.group(2));
        int actualPatch = release.group(3) == null ? 0 : parsePart(release.group(3));
        if (actualMajor < 0 || actualMinor < 0 || actualPatch < 0) {
            return false;
        }

        if (actualMajor != major) {
            return actualMajor > major;
        }
        if (actualMinor != minor) {
            return actualMinor > minor;
        }
        return actualPatch >= patch;
    }

    private static int parsePart(String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }
}
