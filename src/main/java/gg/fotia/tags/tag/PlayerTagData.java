package gg.fotia.tags.tag;

import java.util.*;

public class PlayerTagData {

    private final UUID uuid;
    private String currentTag;
    private final Map<String, Long> ownedTags; // tagId -> expireTime (-1 = permanent)

    public PlayerTagData(UUID uuid) {
        this.uuid = uuid;
        this.currentTag = null;
        this.ownedTags = new HashMap<>();
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

    public Map<String, Long> getOwnedTags() {
        return ownedTags;
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
