package gg.fotia.tags.gradient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PlayerGradientData {

    private final Map<String, Long> ownedEffects = new LinkedHashMap<>();
    private String selectedEffect;

    public synchronized void grant(String effectId, long expireTime) {
        if (effectId == null || effectId.isBlank()) {
            return;
        }
        ownedEffects.put(effectId, expireTime);
    }

    public synchronized void remove(String effectId) {
        ownedEffects.remove(effectId);
        if (effectId != null && effectId.equals(selectedEffect)) {
            selectedEffect = null;
        }
    }

    public synchronized boolean owns(String effectId, long now) {
        Long expireTime = ownedEffects.get(effectId);
        return expireTime != null && (expireTime == -1L || now <= expireTime);
    }

    public synchronized boolean select(String effectId, long now) {
        if (!owns(effectId, now)) {
            return false;
        }
        selectedEffect = effectId;
        return true;
    }

    public synchronized void clearSelection() {
        selectedEffect = null;
    }

    public synchronized String selectedEffect(long now) {
        return selectedEffect != null && owns(selectedEffect, now) ? selectedEffect : null;
    }

    public synchronized String configuredSelection() {
        return selectedEffect;
    }

    public synchronized List<String> validEffects(long now) {
        List<String> valid = new ArrayList<>();
        for (String effectId : ownedEffects.keySet()) {
            if (owns(effectId, now)) {
                valid.add(effectId);
            }
        }
        return valid;
    }

    public synchronized Map<String, Long> ownedEffects() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(ownedEffects));
    }

    public synchronized long expireTime(String effectId) {
        return ownedEffects.getOrDefault(effectId, 0L);
    }

    public synchronized void setSelectedEffectUnchecked(String effectId) {
        selectedEffect = effectId;
    }
}
