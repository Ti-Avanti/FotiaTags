package gg.fotia.tags.hook.placeholders;

import gg.fotia.tags.gradient.GradientEffect;
import gg.fotia.tags.gradient.GradientTarget;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TagPlaceholderSnapshotTest {
    private final TagPlaceholderSnapshot.Values selected = new TagPlaceholderSnapshot.Values(
            "vip", "VIP", "&a[VIP]", "§b[后缀]", "&#123abc[VIP]", "§x§a§b§c§d§e§f[后缀]", false, "", "");
    private final TagPlaceholderSnapshot.Values fallback = new TagPlaceholderSnapshot.Values(
            "member", "成员", "&7[成员]", "", "", "", false, "", "");

    @Test
    void offlineSnapshotExpiresAtTheDeadlineWithoutAnotherDatabaseLoad() {
        var data = new TagPlaceholderSnapshot(UUID.randomUUID(), false, false, selected, fallback,
                1000L, List.of(1000L, -1L, 800L), null, 0, false, "", "");
        assertEquals("vip", data.value("tag", 999));
        assertEquals("member", data.value("tag", 1000));
        assertEquals("2", data.value("count", 999));
        assertEquals("1", data.value("count", 1000));
        assertEquals("[成员]", plain(data.value("prefix", 1000)));
    }

    @Test
    void allTextFieldsPreserveLegacyAndHexColors() {
        var data = new TagPlaceholderSnapshot(UUID.randomUUID(), false, false, selected, fallback,
                -1, List.of(-1L), null, 0, false, "", "");
        assertEquals("§a[VIP]", data.value("prefix", 0));
        assertEquals("§b[后缀]", data.value("suffix", 0));
        assertEquals("§x§1§2§3§a§b§c[VIP]", data.value("prefix2", 0));
        assertEquals("§x§a§b§c§d§e§f[后缀]", data.value("suffix2", 0));
        assertEquals("VIP", data.value("tag_name", 0));
    }

    @Test
    void customGradientKeepsWrappersAndExpiresIndependentlyOfTheTag() {
        var custom = new TagPlaceholderSnapshot.Values("custom:test", "[VIP]", "[VIP]", "", "[VIP]", "",
                true, "VIP", "");
        var effect = new GradientEffect("rainbow", "Rainbow", true, List.of("#ff0000", "#0000ff"),
                40, false, Set.of(GradientTarget.PREFIX), null, null);
        var data = new TagPlaceholderSnapshot(UUID.randomUUID(), false, false, custom, fallback,
                -1, List.of(), effect, 500L, true, "[", "]");
        assertEquals("[VIP]", plain(data.value("prefix", 100)));
        assertNotEquals(data.value("prefix", 100), data.value("prefix", 300));
        assertEquals("[VIP]", data.value("prefix", 501));
    }

    @Test
    void hiddenIdentityDoesNotExposeTitleFields() {
        var data = new TagPlaceholderSnapshot(UUID.randomUUID(), true, true, selected, fallback,
                -1, List.of(-1L), null, 0, false, "", "");
        for (String field : List.of("prefix", "suffix", "prefix2", "suffix2", "tag", "tag_name")) {
            assertEquals("", data.value(field, 0));
        }
        assertEquals("1", data.value("count", 0));
    }

    private String plain(String legacy) {
        return PlainTextComponentSerializer.plainText().serialize(
                LegacyComponentSerializer.legacySection().deserialize(legacy));
    }
}
