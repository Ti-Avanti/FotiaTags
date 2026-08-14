package gg.fotia.tags.tag;

import gg.fotia.tags.gradient.PlayerGradientData;

import java.util.*;

public class PlayerTagData {

    private final UUID uuid;
    private String currentTag;
    private CustomTag customTag;
    private final Map<String, CustomTag> customTags;
    private final Map<String, Long> ownedTags; // tagId -> expireTime (-1 = permanent)
    private final PlayerGradientData gradientData;

    public PlayerTagData(UUID uuid) {
        this.uuid = uuid;
        this.currentTag = null;
        this.customTags = new LinkedHashMap<>();
        this.ownedTags = new HashMap<>();
        this.gradientData = new PlayerGradientData();
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getCurrentTag() {
        return currentTag;
    }

    public void setCurrentTag(String currentTag) {
        this.currentTag = currentTag;
    }

    public CustomTag getCustomTag() {
        if (customTag != null) {
            return customTag;
        }
        return customTags.values().stream().findFirst().orElse(null);
    }

    public void setCustomTag(CustomTag customTag) {
        this.customTag = customTag;
        customTags.clear();
        if (customTag != null && !customTag.getId().isEmpty()) {
            customTags.put(customTag.getId(), customTag);
        }
    }

    public boolean hasCustomTag() {
        return customTags.values().stream().anyMatch(CustomTag::isComplete)
                || (customTag != null && customTag.isComplete());
    }

    public boolean hasCustomTag(String customTagId) {
        CustomTag custom = getCustomTag(customTagId);
        return custom != null && custom.isComplete();
    }

    public CustomTag getCustomTag(String customTagId) {
        if (customTagId == null || customTagId.isEmpty()) {
            return null;
        }
        if (customTagId.equals(TagManager.CUSTOM_TAG_ID)) {
            return getCustomTag();
        }
        return customTags.get(customTagId);
    }

    public Map<String, CustomTag> getCustomTags() {
        return Collections.unmodifiableMap(customTags);
    }

    public void addCustomTag(CustomTag customTag) {
        if (customTag == null || customTag.getId().isEmpty()) {
            return;
        }
        customTags.put(customTag.getId(), customTag);
        if (this.customTag == null) {
            this.customTag = customTag;
        }
    }

    public CustomTag removeCustomTag(String customTagId) {
        CustomTag removed = customTags.remove(customTagId);
        if (customTagId != null && customTagId.equals(currentTag)) {
            currentTag = null;
        }
        if (removed != null && removed == customTag) {
            customTag = customTags.values().stream().findFirst().orElse(null);
        }
        return removed;
    }

    public Map<String, Long> getOwnedTags() {
        return ownedTags;
    }

    public PlayerGradientData getGradientData() {
        return gradientData;
    }

    public boolean hasTag(String tagId) {
        return ownedTags.containsKey(tagId) && !isTagExpired(tagId);
    }

    public boolean isTagExpired(String tagId) {
        Long expireTime = ownedTags.get(tagId);
        if (expireTime == null) {
            return true;
        }
        if (expireTime == -1) {
            return false; // permanent
        }
        return System.currentTimeMillis() > expireTime;
    }

    public void addTag(String tagId, long expireTime) {
        ownedTags.put(tagId, expireTime);
    }

    public void removeTag(String tagId) {
        ownedTags.remove(tagId);
        if (tagId.equals(currentTag)) {
            currentTag = null;
        }
    }

    public List<String> getValidTags() {
        List<String> validTags = new ArrayList<>();
        for (Map.Entry<String, Long> entry : ownedTags.entrySet()) {
            if (!isTagExpired(entry.getKey())) {
                validTags.add(entry.getKey());
            }
        }
        return validTags;
    }

    public long getTagExpireTime(String tagId) {
        return ownedTags.getOrDefault(tagId, 0L);
    }

    public List<String> checkAndRemoveExpiredTags() {
        List<String> expiredTags = new ArrayList<>();
        Iterator<Map.Entry<String, Long>> iterator = ownedTags.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (entry.getValue() != -1 && System.currentTimeMillis() > entry.getValue()) {
                expiredTags.add(entry.getKey());
                iterator.remove();
            }
        }
        if (currentTag != null && expiredTags.contains(currentTag)) {
            currentTag = null;
        }
        return expiredTags;
    }
}
