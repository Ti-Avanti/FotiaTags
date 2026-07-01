package gg.fotia.tags.storage;

import java.util.UUID;

public record PlayerProfile(
        UUID uuid,
        String name,
        String currentTag,
        int ownedTagCount,
        boolean hasCustomTag
) {
    public String displayName() {
        return name != null && !name.isBlank() ? name : uuid.toString();
    }
}
