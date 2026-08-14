package gg.fotia.tags.gradient;

import gg.fotia.tags.FotiaTags;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GradientEffectRegistry {

    private final FotiaTags plugin;
    private volatile Map<String, GradientEffect> effects = Map.of();

    public GradientEffectRegistry(FotiaTags plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        Map<String, GradientEffect> loadedEffects = new LinkedHashMap<>();
        File folder = new File(plugin.getDataFolder(), "gradients");
        File[] files = folder.listFiles((directory, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null) {
            plugin.getLogger().warning("Unable to read gradient effect directory: " + folder.getPath());
            return;
        }

        List<File> sortedFiles = new ArrayList<>(List.of(files));
        sortedFiles.sort(Comparator.comparing(File::getName));
        for (File file : sortedFiles) {
            String fileName = file.getName();
            String id = fileName.substring(0, fileName.length() - 4).toLowerCase();
            try {
                GradientEffect effect = GradientEffectParser.parse(id, YamlConfiguration.loadConfiguration(file));
                loadedEffects.put(id, effect);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Skipped invalid gradient effect " + fileName + ": " + exception.getMessage());
            }
        }

        effects = Collections.unmodifiableMap(new LinkedHashMap<>(loadedEffects));
        plugin.getLogger().info("Loaded " + effects.size() + " gradient effects");
    }

    public GradientEffect get(String effectId) {
        return effectId == null ? null : effects.get(effectId.toLowerCase());
    }

    public Collection<GradientEffect> all() {
        return List.copyOf(effects.values());
    }
}
